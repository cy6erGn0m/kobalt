package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Singleton
class JavaCompiler @Inject constructor(val jvmCompiler: JvmCompiler) {
    fun compilerAction(executable: File) = object : ICompilerAction {
        override fun compile(info: CompilerActionInfo): TaskResult {
            info.outputDir.mkdirs()

            val allArgs = arrayListOf(
                    executable.absolutePath,
                    "-d", info.outputDir.absolutePath)
            if (info.dependencies.size > 0) {
                allArgs.add("-classpath")
                allArgs.add(info.dependencies.map {it.jarFile.get()}.joinToString(File.pathSeparator))
            }
            allArgs.addAll(info.compilerArgs)
            allArgs.addAll(info.sourceFiles)

            val pb = ProcessBuilder(allArgs)
//            info.directory?.let {
//                pb.directory(File(it))
//            }
            pb.inheritIO()
            val line = allArgs.joinToString(" ")
            log(2, "  Compiling $line")
            val process = pb.start()
            val errorCode = process.waitFor()

            return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
                else TaskResult(false, "There were errors")
        }
    }

    /**
     * Create an ICompilerAction based on the parameters and send it to JvmCompiler.doCompile() with
     * the given executable.
     */
    private fun run(project: Project?, context: KobaltContext?, directory: String?,
            dependencies: List<IClasspathDependency>, sourceFiles: List<String>, outputDir: File,
            args: List<String>, executable: File): TaskResult {
        val info = CompilerActionInfo(directory, dependencies, sourceFiles, outputDir, args)
        return jvmCompiler.doCompile(project, context, compilerAction(executable), info)
    }

    fun compile(project: Project?, context: KobaltContext?, dependencies: List<IClasspathDependency>,
            sourceFiles: List<String>, outputDir: File, args: List<String>) : TaskResult
        = run(project, context, project?.directory, dependencies, sourceFiles, outputDir, args,
            JavaInfo.create(File(SystemProperties.javaBase)).javacExecutable!!)

    fun javadoc(project: Project?, context: KobaltContext?, dependencies: List<IClasspathDependency>,
            sourceFiles: List<String>, outputDir: File, args: List<String>) : TaskResult
        = run(project, context, project?.directory, dependencies, sourceFiles, outputDir, args,
            JavaInfo.create(File(SystemProperties.javaBase)).javadocExecutable!!)
}
