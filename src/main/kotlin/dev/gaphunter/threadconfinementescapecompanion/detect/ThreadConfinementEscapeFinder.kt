package dev.gaphunter.threadconfinementescapecompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSynchronizedStatement
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.threadconfinementescapecompanion.model.ThreadConfinementHit

/**
 * Real alias/escape analysis over a NEW kind of boundary this catalog
 * has never analyzed before: which OUTER-SCOPE variables a lambda or
 * anonymous class body CAPTURES (`interprocedural-resource-leak-
 * companion`'s engine explicitly refuses to descend into a lambda/
 * anonymous class at all when computing a summary -- this plugin does
 * the opposite, descending specifically to see what escapes INTO it).
 *
 * Finds a mutable local variable (a `List`/`Map`/`Set`) captured by a
 * `Runnable`/`Callable` handed to `ExecutorService.submit/execute` or
 * `new Thread(runnable).start()`, then checks whether the SAME method
 * touches that SAME variable again anywhere AFTER the hand-off,
 * OUTSIDE a `synchronized` block -- real, documented thread-
 * confinement violation: the object is now genuinely shared between
 * the original thread and the background one, with no real
 * protection.
 *
 * **v0.1 scope, stated honestly:** only `List`/`Map`/`Set` local
 * variables declared in the SAME method (never a captured field via
 * implicit `this`); only an inline lambda or anonymous class passed
 * directly at the call site (never a `Runnable` constructed
 * elsewhere and passed in by reference); the post-submission access
 * must be textually in the SAME method; a local declared with an
 * interface type (`Map`/`List`/`Set`) but initialized directly with a
 * genuinely thread-safe implementation (`ConcurrentHashMap`,
 * `CopyOnWriteArrayList`, a `Collections.synchronizedXxx(...)` wrapper,
 * ...) is never tracked -- confirmed real feedback: those types
 * provide their own real thread safety, so flagging unsynchronized
 * access to them would be pure noise. Reassigning the variable to a
 * thread-safe implementation AFTER its declaration (not at the
 * initializer) is out of scope and still tracked -- a known, honest
 * v0.1 limitation.
 */
object ThreadConfinementEscapeFinder {

    private val SUBMIT_METHOD_NAMES = setOf("submit", "execute")
    private val MUTABLE_TYPE_SIMPLE_NAMES = setOf(
        "List", "ArrayList", "LinkedList",
        "Map", "HashMap", "TreeMap", "LinkedHashMap",
        "Set", "HashSet", "TreeSet", "LinkedHashSet",
    )
    private val THREAD_SAFE_CONSTRUCTOR_NAMES = setOf(
        "ConcurrentHashMap", "ConcurrentSkipListMap", "ConcurrentSkipListSet",
        "CopyOnWriteArrayList", "CopyOnWriteArraySet",
        "ConcurrentLinkedQueue", "ConcurrentLinkedDeque",
    )
    private val THREAD_SAFE_WRAPPER_METHOD_NAMES = setOf(
        "synchronizedList", "synchronizedMap", "synchronizedSet",
        "synchronizedCollection", "synchronizedSortedMap", "synchronizedSortedSet",
    )

    private data class SubmitSite(val anchor: PsiElement, val afterOffset: Int, val capturedNames: Set<String>)

    fun findAll(file: PsiFile): List<ThreadConfinementHit> {
        val hits = mutableListOf<ThreadConfinementHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                val body = method.body ?: return
                hits += hitsInMethod(body)
            }
        })
        return hits
    }

    private fun hitsInMethod(body: PsiCodeBlock): List<ThreadConfinementHit> {
        val mutableLocals = mutableSetOf<String>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitLocalVariable(variable: PsiLocalVariable) {
                super.visitLocalVariable(variable)
                if (isMutableType(variable.type) && !isThreadSafeInitializer(variable.initializer)) {
                    mutableLocals += variable.name
                }
            }
        })
        if (mutableLocals.isEmpty()) return emptyList()

        val submitSites = mutableListOf<SubmitSite>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                val runnableArg = backgroundRunnableArgumentOf(call) ?: return
                val captured = capturedMutableNames(runnableArg, mutableLocals)
                if (captured.isEmpty()) return
                submitSites += SubmitSite(call.methodExpression.referenceNameElement ?: call.methodExpression, call.textRange.endOffset, captured)
            }
        })
        if (submitSites.isEmpty()) return emptyList()

        val hits = mutableListOf<ThreadConfinementHit>()
        for (site in submitSites) {
            for (name in site.capturedNames) {
                if (hasUnsynchronizedAccessAfter(body, site.afterOffset, name)) {
                    hits += ThreadConfinementHit(site.anchor, name)
                    break // one finding per hand-off site is enough
                }
            }
        }
        return hits
    }

    /** `executor.submit(runnable)`/`.execute(runnable)`, or `new Thread(runnable).start()` -- the argument that ends up running on a background thread. */
    private fun backgroundRunnableArgumentOf(call: PsiMethodCallExpression): PsiElement? {
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName in SUBMIT_METHOD_NAMES) return call.argumentList.expressions.getOrNull(0)
        if (methodName == "start") {
            val qualifier = call.methodExpression.qualifierExpression as? PsiNewExpression ?: return null
            if (qualifier.classReference?.referenceName != "Thread") return null
            return qualifier.argumentList?.expressions?.getOrNull(0)
        }
        return null
    }

    private fun isMutableType(type: PsiType): Boolean = (type as? PsiClassType)?.className in MUTABLE_TYPE_SIMPLE_NAMES

    /** `new ConcurrentHashMap<>()` / `Collections.synchronizedMap(...)` and similar -- a declared `Map`/`List`/`Set` initialized directly with one of these already provides real thread safety, so it's never tracked as confinement-sensitive. */
    private fun isThreadSafeInitializer(initializer: PsiExpression?): Boolean = when (initializer) {
        is PsiNewExpression -> initializer.classReference?.referenceName in THREAD_SAFE_CONSTRUCTOR_NAMES
        is PsiMethodCallExpression -> initializer.methodExpression.referenceName in THREAD_SAFE_WRAPPER_METHOD_NAMES
        else -> false
    }

    private fun capturedMutableNames(runnableArgument: PsiElement, trackedNames: Set<String>): Set<String> {
        val bodyToScan: PsiElement = when (runnableArgument) {
            is PsiLambdaExpression -> runnableArgument.body ?: return emptySet()
            is PsiNewExpression -> runnableArgument.anonymousClass ?: return emptySet()
            else -> return emptySet()
        }
        val captured = mutableSetOf<String>()
        bodyToScan.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceExpression(expr: PsiReferenceExpression) {
                super.visitReferenceExpression(expr)
                if (expr.qualifierExpression != null) return
                val name = expr.referenceName ?: return
                if (name in trackedNames) captured += name
            }
        })
        return captured
    }

    private fun hasUnsynchronizedAccessAfter(body: PsiCodeBlock, afterOffset: Int, name: String): Boolean {
        var found = false
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceExpression(expr: PsiReferenceExpression) {
                if (found) return
                super.visitReferenceExpression(expr)
                if (expr.qualifierExpression != null) return
                if (expr.referenceName != name) return
                if (expr.textRange.startOffset <= afterOffset) return
                if (PsiTreeUtil.getParentOfType(expr, PsiSynchronizedStatement::class.java) != null) return
                found = true
            }
        })
        return found
    }
}
