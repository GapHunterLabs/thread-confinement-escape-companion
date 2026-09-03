package dev.gaphunter.threadconfinementescapecompanion.model

import com.intellij.psi.PsiElement

/** A confirmed thread-confinement violation: [variableName] (a mutable local variable) is captured by a `Runnable`/`Callable` handed to a background thread at [anchor], and the SAME method also touches it afterward outside any `synchronized` block -- shared mutable state with no real protection. */
data class ThreadConfinementHit(val anchor: PsiElement, val variableName: String)
