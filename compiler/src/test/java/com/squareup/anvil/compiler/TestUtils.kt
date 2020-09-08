package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.Result
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dagger.Component
import dagger.Subcomponent
import dagger.android.processor.AndroidProcessor
import dagger.internal.codegen.ComponentProcessor
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.util.Locale.US

internal fun compile(
  source: String,
  enableDaggerAnnotationProcessor: Boolean = false,
  enableDaggerAndroidAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  block: Result.() -> Unit = { }
): Result {
  return compile(
    sourceFiles = listOf(source),
    enableDaggerAnnotationProcessor = enableDaggerAnnotationProcessor,
    enableDaggerAndroidAnnotationProcessor = enableDaggerAndroidAnnotationProcessor,
    generateDaggerFactories = generateDaggerFactories,
    block = block
  )
}

internal fun compile(
  sourceFiles: List<String>,
  enableDaggerAnnotationProcessor: Boolean = false,
  enableDaggerAndroidAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  block: Result.() -> Unit = { }
): Result {
  return KotlinCompilation()
      .apply {
        compilerPlugins = listOf(AnvilComponentRegistrar())
        useIR = false
        inheritClassPath = true
        jvmTarget = JvmTarget.JVM_1_8.description

        if (enableDaggerAnnotationProcessor) {
          annotationProcessors = listOf(ComponentProcessor())
        }

        if (enableDaggerAndroidAnnotationProcessor) {
          annotationProcessors = listOf(ComponentProcessor(), AndroidProcessor())
        }

        val commandLineProcessor = AnvilCommandLineProcessor()
        commandLineProcessors = listOf(commandLineProcessor)

        pluginOptions = listOf(
            PluginOption(
                pluginId = commandLineProcessor.pluginId,
                optionName = srcGenDirName,
                optionValue = File(workingDir, "build/anvil").absolutePath
            ),
            PluginOption(
                pluginId = commandLineProcessor.pluginId,
                optionName = generateDaggerFactoriesName,
                optionValue = generateDaggerFactories.toString()
            )
        )

        sources = sourceFiles.mapIndexed { index, content ->
          val name =
            "${workingDir.absolutePath}/sources/src/main/java/com/squareup/test/Source$index.kt"
          File(name).parentFile.mkdirs()
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

@OptIn(ExperimentalStdlibApi::class)
internal fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
      "${`package`.name}.$enclosingClassString$simpleName$companionString" +
          "_${providerMethodName.capitalize(US)}Factory"
  )
}

@OptIn(ExperimentalStdlibApi::class)
internal fun Class<*>.factoryClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}.$enclosingClassString${simpleName}_Factory")
}

@OptIn(ExperimentalStdlibApi::class)
internal fun Class<*>.membersInjector(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}." +
      "$enclosingClassString${simpleName}_MembersInjector")
}

@OptIn(ExperimentalStdlibApi::class)
internal fun Class<*>.contributesAndroidInjector(target: String): Class<*> {
  return classLoader.loadClass("${`package`.name}.DaggerModule1_Bind$target")
}

internal val Class<*>.daggerComponent: Component
  get() = annotations.filterIsInstance<Component>()
      .also { assertThat(it).hasSize(1) }
      .first()

internal val Class<*>.daggerSubcomponent: Subcomponent
  get() = annotations.filterIsInstance<Subcomponent>()
      .also { assertThat(it).hasSize(1) }
      .first()

internal infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)
