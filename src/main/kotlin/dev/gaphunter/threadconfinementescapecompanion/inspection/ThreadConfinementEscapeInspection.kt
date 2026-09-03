package dev.gaphunter.threadconfinementescapecompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.threadconfinementescapecompanion.detect.ThreadConfinementEscapeFinder
import dev.gaphunter.threadconfinementescapecompanion.model.ThreadConfinementHit
import dev.gaphunter.threadconfinementescapecompanion.review.ReviewPrompt

/** Flags a mutable local variable shared with a background thread and also touched afterward by the original thread without synchronization -- see [ThreadConfinementEscapeFinder]. */
class ThreadConfinementEscapeInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = ThreadConfinementEscapeFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.variableName}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: ThreadConfinementHit): String =
        "'${hit.variableName}' is captured here by a task handed to a background thread, but this same method also " +
            "touches it afterward outside any synchronized block (CWE-362/366) -- it's now shared mutable state with no real protection"
}
