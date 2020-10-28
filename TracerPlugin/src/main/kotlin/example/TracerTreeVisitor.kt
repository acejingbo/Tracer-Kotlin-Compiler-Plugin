package example

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.PsiDiagnosticUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/** Parse file as tree. Generate new file text with annotated method wrapped with tracer */
class TracerTreeVisitor(
    private val oldFile: KtFile, private val messageCollector: MessageCollector
) : KtTreeVisitorVoid() {

    private val patches = mutableListOf<Pair<PsiElement, String>>()

    fun buildOutput(): String? {
        oldFile.accept(this)

        return if (patches.isEmpty()) {
            null
        } else {
            applyPatches(oldFile.text, patches)
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val annotations = function.annotationEntries.map { it.text }.toMutableList()
        // TODO: Ideally, pass in as param instead of hardcoding. Also, check 
        //  import for fully qualified name.
        if (!annotations.contains("@TraceMethod")) {
            return
        }

        // Handle block body
        val blockBody = function.bodyBlockExpression
        if (function.hasBlockBody() && blockBody != null) {
            patches.add(Pair<PsiElement, String>(blockBody,
                """|
                 |{
                 |  android.os.Trace.beginSection("${function.fqName}")
                 |  try ${blockBody.text} finally {
                 |    android.os.Trace.endSection()
                 |  }
                 |}
            """.trimMargin()))
            return
        }

        // handle method body
        val expressionBody = function.bodyExpression
        if (expressionBody != null) {
            checkCondition(function.typeReference != null, oldFile, messageCollector, element = function) {
                "@TraceMethod is only supported at function with explicit return type."
            }

            patches.add(Pair<PsiElement, String>(function,
                function.text.replace(
                    "= ${expressionBody.text}",
                    """|
                 |{
                 |  android.os.Trace.beginSection("${function.fqName}")
                 |  try {
                 |    return ${expressionBody.text}
                 |  } finally {
                 |    android.os.Trace.endSection()
                 |  }
                 |}
              """.trimMargin())))
        }
    }
}

private fun applyPatches(oldText: String, patches: List<Pair<PsiElement, String>>): String {
    val sortedPatches: List<Pair<PsiElement, String>> =
        patches.sortedBy { it.first.startOffset }

    // Check no patch intersect
    var previousPatchEndOffset = -1
    for (patch in sortedPatches) {
        if (patch.first.startOffset <= previousPatchEndOffset) {
            throw IllegalArgumentException("Cannot apply patches. Patches intersect.")
        }
        previousPatchEndOffset = patch.first.endOffset
    }

    // Apply patch in reverse order, so each patch won't affect next patch's offset.
    var newText = oldText
    for ((element, replacement) in sortedPatches.reversed()) {
        newText = newText.substring(0, element.startOffset) + replacement + newText.substring(element.endOffset)
    }
    return newText
}

private fun checkCondition(
    condition: Boolean,
    oldFile: KtFile,
    messageCollector: MessageCollector,
    element: PsiElement,
    lazyMessage: () -> String) {
    if (condition) return

    val lineAndColumn =
        PsiDiagnosticUtils.offsetToLineAndColumn(oldFile.viewProvider.document, element.startOffset)
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "[Error] Tracer Kotlin Compiler Plugin error: ${lazyMessage()}",
        CompilerMessageLocation.create(
            oldFile.virtualFilePath,
            lineAndColumn.line,
            lineAndColumn.column,
            lineAndColumn.lineContent))
    throw AnalysisResult.CompilationErrorException()
}