package dev.quiro.sheath.compiler

import com.google.auto.service.AutoService
import dev.quiro.sheath.compiler.codegen.CodeGenerationExtension
import dev.quiro.sheath.compiler.codegen.CodeGenerator
import dev.quiro.sheath.compiler.codegen.dagger.ComponentDetectorCheck
import dev.quiro.sheath.compiler.codegen.dagger.ContributesAndroidInjectorGenerator
import dev.quiro.sheath.compiler.codegen.dagger.InjectConstructorFactoryGenerator
import dev.quiro.sheath.compiler.codegen.dagger.MembersInjectorGenerator
import dev.quiro.sheath.compiler.codegen.dagger.ProvidesMethodFactoryGenerator
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.LoadingOrder
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

/**
 * Entry point for the Sheath Kotlin compiler plugin. It registers several callbacks for the
 * various compilation phases.
 */
@AutoService(ComponentRegistrar::class)
class SheathComponentRegistrar : ComponentRegistrar {
  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration
  ) {
    val sourceGenFolder = File(configuration.getNotNull(srcGenDirKey))

    val codeGenerators = mutableListOf<CodeGenerator>()
    if (configuration.get(generateDaggerFactoriesKey, false)) {
      codeGenerators += ProvidesMethodFactoryGenerator()
      codeGenerators += InjectConstructorFactoryGenerator()
      codeGenerators += MembersInjectorGenerator()
      codeGenerators += ComponentDetectorCheck()
      codeGenerators += ContributesAndroidInjectorGenerator()
    }

    // It's important to register our extension at the first position. The compiler calls each
    // extension one by one. If an extension returns a result, then the compiler won't call any
    // other extension. That usually happens with Kapt in the stub generating task.
    //
    // It's not dangerous for our extension to run first, because we generate code, restart the
    // analysis phase and then don't return a result anymore. That means the next extension can
    // take over. If we wouldn't do this and any other extension won't let our's run, then we
    // couldn't generate any code.
    AnalysisHandlerExtension.registerExtensionFirst(
        project,
        CodeGenerationExtension(
            codeGenDir = sourceGenFolder,
            codeGenerators = codeGenerators
        )
    )
  }

  private fun AnalysisHandlerExtension.Companion.registerExtensionFirst(
    project: MockProject,
    extension: AnalysisHandlerExtension
  ) {
    project.extensionArea
        .getExtensionPoint(AnalysisHandlerExtension.extensionPointName)
        .registerExtension(extension, LoadingOrder.FIRST, project)
  }
}
