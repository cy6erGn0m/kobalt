package com.beust.kobalt.plugin.java

import com.beust.kobalt.JavaInfo
import com.beust.kobalt.SystemProperties
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.CompilerActionInfo
import com.beust.kobalt.internal.ICompilerAction
import com.beust.kobalt.internal.JvmCompiler
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.File

@Singleton
class JavaCompiler @Inject constructor(val dependencyManager: DependencyManager,
        val jvmCompiler: JvmCompiler){
    /**
     * Create an ICompilerAction and a CompilerActionInfo suitable to be passed to doCompiler() to perform the
     * actual compilation.
     */
    fun compile(project: Project?, context: KobaltContext?, dependencies: List<IClasspathDependency>,
            sourceFiles: List<String>, outputDir: String, args: List<String>) : TaskResult {

        val info = CompilerActionInfo(dependencies, sourceFiles, outputDir, args)
        val compilerAction = object : ICompilerAction {
            override fun compile(info: CompilerActionInfo): TaskResult {

                val jvm = JavaInfo.create(File(SystemProperties.javaBase))
                val javac = jvm.javacExecutable

                val args = arrayListOf(
                        javac!!.absolutePath,
                        "-d", info.outputDir)
                if (dependencies.size > 0) {
                    args.add("-classpath")
                    args.add(info.dependencies.map {it.jarFile.get()}.joinToString(File.pathSeparator))
                }
                args.addAll(info.compilerArgs)
                args.addAll(info.sourceFiles)

                val pb = ProcessBuilder(args)
                if (outputDir != null) {
                    pb.directory(File(outputDir))
                }
                pb.inheritIO()
                val line = args.joinToString(" ")
                log(1, "  Compiling ${sourceFiles.size} files with classpath size " + info.dependencies.size)
                log(2, "  Compiling ${project?.name}:\n$line")
                val process = pb.start()
                val errorCode = process.waitFor()

                return if (errorCode == 0) TaskResult(true, "Compilation succeeded")
                else TaskResult(false, "There were errors")
            }
        }
        return jvmCompiler.doCompile(project, context, compilerAction, info)
    }
}