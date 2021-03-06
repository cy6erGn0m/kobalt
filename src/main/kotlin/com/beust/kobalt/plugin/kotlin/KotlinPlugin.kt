package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.*
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotlinPlugin @Inject constructor(
        override val localRepo: LocalRepo,
        override val files: KFiles,
        override val depFactory: DepFactory,
        override val dependencyManager: DependencyManager,
        override val executors: KobaltExecutors,
        override val jvmCompiler: JvmCompiler)
        : JvmCompilerPlugin(localRepo, files, depFactory, dependencyManager, executors, jvmCompiler),
            IProjectContributor, IClasspathContributor {

    companion object {
        public const val TASK_COMPILE: String = "compile"
        public const val TASK_COMPILE_TEST: String = "compileTest"
    }

    override val name = "kotlin"

    override fun accept(project: Project) = project is KotlinProject

    @Task(name = TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_MAIN)
        val classpath = jvmCompiler.calculateDependencies(project, context, project.compileDependencies,
                project.compileProvidedDependencies)

        val projectDirectory = java.io.File(project.directory)
        val buildDirectory = File(projectDirectory, project.buildDirectory + File.separator + "classes")
        buildDirectory.mkdirs()

        val sourceFiles = files.findRecursively(projectDirectory,
                project.sourceDirectories.map { File(it) }, { it.endsWith(".kt") })
        val absoluteSourceFiles = sourceFiles.map {
            File(projectDirectory, it).absolutePath
        }

        compilePrivate(project, classpath, absoluteSourceFiles, buildDirectory)
        lp(project, "Compilation succeeded")
        return TaskResult()
    }

    @Task(name = TASK_COMPILE_TEST, description = "Compile the tests", runAfter = arrayOf(TASK_COMPILE))
    fun taskCompileTest(project: Project): TaskResult {
        copyResources(project, JvmCompilerPlugin.SOURCE_SET_TEST)
        val projectDir = File(project.directory)

        val absoluteSourceFiles = files.findRecursively(projectDir, project.sourceDirectoriesTest.map { File(it) })
            { it: String -> it.endsWith(".kt") }
                    .map { File(projectDir, it).absolutePath }

        compilePrivate(project, testDependencies(project),
                absoluteSourceFiles,
                makeOutputTestDir(project))

        lp(project, "Compilation of tests succeeded")
        return TaskResult()
    }

    private fun compilePrivate(project: Project, cpList: List<IClasspathDependency>, sources: List<String>,
            outputDirectory: File): TaskResult {
        return kotlinCompilePrivate {
            classpath(cpList.map { it.jarFile.get().absolutePath })
            sourceFiles(sources)
            compilerArgs(compilerArgs)
            output = outputDirectory
        }.compile(project, context)
    }

    // interface IProjectContributor
    override fun projects() = projects

    private fun getKotlinCompilerJar(name: String) : String {
        val id = "org.jetbrains.kotlin:$name:${KotlinCompiler.KOTLIN_VERSION}"
        val dep = MavenDependency.create(id, executors.miscExecutor)
        val result = dep.jarFile.get().absolutePath
        return result
    }


    // interface IClasspathContributor
    override fun entriesFor(project: Project?) : List<IClasspathDependency> =
        if (project == null || project is KotlinProject) {
            // All Kotlin projects automatically get the Kotlin runtime added to their class path
            listOf(getKotlinCompilerJar("kotlin-stdlib"), getKotlinCompilerJar("kotlin-compiler-embeddable"))
                .map { FileDependency(it) }
        } else {
            listOf()
        }
}

/**
 * @param project: the list of projects that need to be built before this one.
 */
@Directive
fun kotlinProject(vararg project: Project, init: KotlinProject.() -> Unit): KotlinProject {
    return KotlinProject().apply {
        init()
        (Kobalt.findPlugin("kotlin") as BasePlugin).addProject(this, project)
    }
}

class KotlinCompilerConfig {
    fun args(vararg options: String) {
        (Kobalt.findPlugin("kotlin") as JvmCompilerPlugin).addCompilerArgs(*options)
    }
}

@Directive
fun Project.kotlinCompiler(init: KotlinCompilerConfig.() -> Unit) = KotlinCompilerConfig().init()
