package example

import org.gradle.api.Plugin
import org.jetbrains.kotlin.gradle.plugin.*
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions

class TracerGradlePlugin : Plugin<Project>, KotlinGradleSubplugin<AbstractCompile> {
    
    override fun apply(p0: Project) = Unit
    
    override fun isApplicable(project: Project, task: AbstractCompile): Boolean = true

    // groupId, artifactId, and version need to match build.gradle
    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("example","TracerPlugin", "1.0.0")
    
    /** compiler plugin id need to match [TracerCommandLineProcessor]*/
    override fun getCompilerPluginId() = "example.compilerplugin.tracerplugin" 
    
    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> {
        return listOf(SubpluginOption(
            "outputDir",
            project.buildDir.absolutePath + "/generated/ktPlugin"
        ))
    }
}