package example

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

/** Key to store configuration */
object TracerConfigurationKeys {
    val OUTPUT_DIR_KEY: CompilerConfigurationKey<String> = CompilerConfigurationKey.create<String>("Output directory")
}

/** Accept plugin option from command line and put in configuration */
class TracerCommandLineProcessor : CommandLineProcessor {
    companion object {
        val TRACER_PLUGIN_ID: String = "example.compilerplugin.tracerplugin"
        val OUTPUT_DIR_OPTION: CliOption = CliOption("outputDir", "<value>", "")
    }

    override val pluginId: String = TRACER_PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = listOf(OUTPUT_DIR_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            OUTPUT_DIR_OPTION -> configuration.put(TracerConfigurationKeys.OUTPUT_DIR_KEY, value)
            else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        }
    }
}

/** Read plugin option from configuration. Register extension to certain extension point */
class TracerComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val outputDir: String = checkNotNull(configuration.get(TracerConfigurationKeys.OUTPUT_DIR_KEY), { "outputDir cannot be null" })
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "TracerComponentRegistrar.registerProjectComponents: outputDir=$outputDir")

        // Register TracerPluginExtension to a specific extension point in the compiler.
        // For raw code transformation, we choose the analysis extension point
        AnalysisHandlerExtension.registerExtension(project, TracerPluginExtension(outputDir, messageCollector))
    }
}

/** Transform file to wrap with tracer. */
class TracerPluginExtension(
    private val outputDir: String, private val messageCollector: MessageCollector
): AnalysisHandlerExtension {
    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        files as ArrayList
        for (i in files.indices) {
            val oldFile = files[i]
            messageCollector.report(CompilerMessageSeverity.INFO, oldFile.name)

            // Replace text and create a new KtFile
            val newFileText: String? = TracerTreeVisitor(oldFile, messageCollector).buildOutput()

            // Create new file base on transformed text, and replace the old file with the new one
            if (newFileText != null) {
                files[i] = createNewKtFile(oldFile.name, newFileText, outputDir, oldFile.manager)
                messageCollector.report(
                    CompilerMessageSeverity.INFO, 
                    "${oldFile.virtualFilePath} transformed to ${files[i].virtualFilePath}")
            }
        }
        return null
    }
}

private fun createNewKtFile(
    name: String, content: String, outputDir: String, fileManager: PsiManager): KtFile {
    val directory = File(outputDir).apply { mkdirs() }
    val file = File(directory, name).apply { writeText(content) }
    val virtualFile = CoreLocalVirtualFile(CoreLocalFileSystem(), file)
    return KtFile(SingleRootFileViewProvider(fileManager, virtualFile), false)
}


