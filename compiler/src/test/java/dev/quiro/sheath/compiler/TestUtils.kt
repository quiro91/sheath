package dev.quiro.sheath.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.Result
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dagger.android.processor.AndroidProcessor
import dagger.internal.codegen.ComponentProcessor
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.util.Locale.US

internal fun compile(
  vararg sources: String,
  enableDaggerAnnotationProcessor: Boolean = false,
  enableDaggerAndroidAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  block: Result.() -> Unit = { }
): Result {
  return KotlinCompilation()
    .apply {
      compilerPlugins = listOf(SheathComponentRegistrar())
      useIR = USE_IR
      inheritClassPath = true
      jvmTarget = JvmTarget.JVM_1_8.description
      this.allWarningsAsErrors = false

      if (enableDaggerAnnotationProcessor) {
        annotationProcessors = listOf(ComponentProcessor())
      }

      if (enableDaggerAndroidAnnotationProcessor) {
        annotationProcessors = listOf(ComponentProcessor(), AndroidProcessor())
      }

      val commandLineProcessor = SheathCommandLineProcessor()
      commandLineProcessors = listOf(commandLineProcessor)

      pluginOptions = listOf(
        PluginOption(
          pluginId = commandLineProcessor.pluginId,
          optionName = srcGenDirName,
          optionValue = File(workingDir, "build/sheath").absolutePath
        ),
        PluginOption(
          pluginId = commandLineProcessor.pluginId,
          optionName = generateDaggerFactoriesName,
          optionValue = generateDaggerFactories.toString()
        )
      )

      this.sources = sources.map { content ->
        val packageDir = content.lines()
          .firstOrNull { it.trim().startsWith("package ") }
          ?.substringAfter("package ")
          ?.replace('.', '/')
          ?.let { "$it/" }
          ?: ""

        val name = "${workingDir.absolutePath}/sources/src/main/java/$packageDir/Source.kt"
        with(File(name).parentFile) {
          check(exists() || mkdirs())
        }

        SourceFile.kotlin(name, contents = content, trimIndent = true)
      }
    }
    .compile()
    .also(block)
}

internal val Result.daggerModule1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule1")

internal val Result.innerModule: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface\$InnerModule")

internal val Result.injectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.InjectClass")

internal fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
    "${packageName()}$enclosingClassString$simpleName$companionString" +
      "_${providerMethodName.capitalize(US)}Factory"
  )
}

internal fun Class<*>.factoryClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${packageName()}$enclosingClassString${simpleName}_Factory")
}

internal fun Class<*>.membersInjector(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
    "${packageName()}$enclosingClassString${simpleName}_MembersInjector"
  )
}

private fun Class<*>.packageName(): String = `package`.name.let {
  if (it.isBlank()) "" else "$it."
}

internal fun Class<*>.implClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""
  return classLoader.loadClass("${packageName()}$enclosingClassString${simpleName}_Impl")
}

internal fun Class<*>.contributesAndroidInjector(target: String): Class<*> {
  return classLoader.loadClass("${packageName()}DaggerModule1_$target")
}

internal val Result.assistedService: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedService")

internal val Result.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedServiceFactory")

internal fun Any.invokeGet(vararg args: Any?): Any {
  val method = this::class.java.declaredMethods.single { it.name == "get" }
  return method.invoke(this, *args)
}

internal infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)
