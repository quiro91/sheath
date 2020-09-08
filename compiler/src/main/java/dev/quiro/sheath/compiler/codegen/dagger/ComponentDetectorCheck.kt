package dev.quiro.sheath.compiler.codegen.dagger

import dev.quiro.sheath.compiler.SheathCompilationException
import dev.quiro.sheath.compiler.codegen.PrivateCodeGenerator
import dev.quiro.sheath.compiler.codegen.classesAndInnerClasses
import dev.quiro.sheath.compiler.codegen.hasAnnotation
import dev.quiro.sheath.compiler.daggerComponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal class ComponentDetectorCheck : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    val component = projectFiles
        .asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .firstOrNull { it.hasAnnotation(daggerComponentFqName) }

    if (component != null) {
      throw SheathCompilationException(
          message = "Sheath cannot generate the code for Dagger components or subcomponents. In " +
              "these cases the Dagger annotation processor is required. Enabling the Dagger " +
              "annotation processor and turning on Sheath to generate Dagger factories is " +
              "redundant. Set 'generateDaggerFactories' to false.",
          element = component
      )
    }
  }
}
