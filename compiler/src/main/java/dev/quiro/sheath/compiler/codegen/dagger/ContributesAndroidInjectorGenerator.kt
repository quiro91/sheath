package dev.quiro.sheath.compiler.codegen.dagger

import dev.quiro.sheath.compiler.codegen.CodeGenerator
import dev.quiro.sheath.compiler.codegen.addGeneratedByComment
import dev.quiro.sheath.compiler.codegen.classesAndInnerClasses
import dev.quiro.sheath.compiler.codegen.findAnnotation
import dev.quiro.sheath.compiler.codegen.fqNameOrNull
import dev.quiro.sheath.compiler.codegen.functions
import dev.quiro.sheath.compiler.codegen.hasAnnotation
import dev.quiro.sheath.compiler.codegen.requireTypeName
import dev.quiro.sheath.compiler.codegen.withJvmSuppressWildcardsIfNeeded
import dev.quiro.sheath.compiler.codegen.writeToString
import dev.quiro.sheath.compiler.daggerContributesAndroidInjector
import dev.quiro.sheath.compiler.daggerModuleFqName
import dev.quiro.sheath.compiler.generateClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dev.quiro.sheath.compiler.codegen.PrivateCodeGenerator
import dev.quiro.sheath.compiler.codegen.extractArrayOfClass
import dev.quiro.sheath.compiler.codegen.extractSingleParameter
import dev.quiro.sheath.compiler.codegen.requireFqName
import dev.quiro.sheath.compiler.codegen.requireTypeReference
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import java.lang.StringBuilder

internal class ContributesAndroidInjectorGenerator : PrivateCodeGenerator() {
  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles.asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .filter { it.hasAnnotation(daggerModuleFqName) }
      .flatMap { clazz ->
        clazz
          .functions(includeCompanionObjects = true)
          .asSequence()
          .filter { it.hasAnnotation(daggerContributesAndroidInjector) }
          .map { function ->
            generateInjectorClass(codeGenDir, module, clazz, function)
          }
      }
      .toList()
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun generateInjectorClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    function: KtNamedFunction
  ): CodeGenerator.GeneratedFile {
    val packageName = clazz.containingKtFile.packageFqName.asString()
    val returnType = function.requireTypeReference()
      .requireTypeName(module)
      .withJvmSuppressWildcardsIfNeeded(function)
    val bindingTargetName = function.requireTypeReference()
      .requireFqName(module)
      .shortName()
      .asString()
    val className = "${clazz.generateClassName()}_${function.name!!.capitalize()}"
    val factoryClass = ClassName(packageName, className)
    val contributesAndroidInjector = function.findAnnotation(daggerContributesAndroidInjector)!!
    val moduleClasses = contributesAndroidInjector.extractSingleParameter()
      ?.extractArrayOfClass(module) ?: emptyList()
    val scopeAnnotations = function.annotationEntries
      .asSequence()
      .filterNot { it == contributesAndroidInjector }
      .mapNotNull {
        val element = it.children.singleOrNull()?.children?.singleOrNull()
        element?.fqNameOrNull(module) ?: element?.text?.let(::FqName)
      }

    val content = FileSpec.builder(packageName, className)
      .apply {
        val classBuilder = TypeSpec.classBuilder(factoryClass)

        val subcomponentFactoryClassName = ClassName(packageName, "Factory")
        val subcomponentFactory = TypeSpec.interfaceBuilder(subcomponentFactoryClassName)
          .addAnnotation(Subcomponent.Factory::class.java)
          .addSuperinterface(
            AndroidInjector.Factory::class.asClassName().parameterizedBy(returnType)
          )
          .build()

        val subcomponentClassName = "${bindingTargetName}Subcomponent"
        val fullSubcomponentFactoryClassName =
          ClassName(packageName, "$subcomponentClassName.Factory")
        val subcomponent = TypeSpec.interfaceBuilder(ClassName(packageName, subcomponentClassName))
          .addAnnotation(
            AnnotationSpec.builder(Subcomponent::class.java).apply {
              if (moduleClasses.isNotEmpty()) {
                val builder = StringBuilder("modules = [").apply {
                  moduleClasses.forEachIndexed { index, _ ->
                    append("%T::class")
                    val next = if (index == moduleClasses.lastIndex) "]" else ","
                    append(next)
                  }
                }
                addMember(builder.toString(), *moduleClasses.toTypedArray())
              }
            }.build()
          )
          .apply {
            scopeAnnotations.forEach { annotation ->
              addAnnotation(
                ClassName(
                  annotation.parent().asString(),
                  annotation.shortName().asString()
                )
              )
            }
          }
          .addSuperinterface(AndroidInjector::class.asClassName().parameterizedBy(returnType))
          .addType(subcomponentFactory)
          .build()

        val fullSubcomponentClassName = ClassName.bestGuess(
          "$packageName.$className.$subcomponentClassName"
        )
        classBuilder.primaryConstructor(
          FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .build()
        )
          .addAnnotation(
            AnnotationSpec.builder(Module::class)
              .addMember("subcomponents = [%T::class]", fullSubcomponentClassName)
              .build()
          )
          .addModifiers(KModifier.ABSTRACT)
          .addType(subcomponent)
          .addFunction(
            FunSpec.builder("bindAndroidInjectorFactory")
              .addParameter(ParameterSpec("builder", fullSubcomponentFactoryClassName))
              .addModifiers(KModifier.ABSTRACT)
              .addAnnotation(Binds::class)
              .addAnnotation(IntoMap::class)
              .addAnnotation(
                AnnotationSpec.builder(ClassKey::class)
                  .addMember("%T::class", returnType)
                  .build()
              )
              .returns(AndroidInjector.Factory::class.asClassName().parameterizedBy(STAR))
              .build()
          )
          .build()
          .let { addType(it) }
      }
      .build()
      .writeToString()
      .addGeneratedByComment()

    val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
    val file = File(directory, "$className.kt")
    check(file.parentFile.exists() || file.parentFile.mkdirs()) {
      "Could not generate package directory: ${file.parentFile}"
    }
    file.writeText(content)

    return CodeGenerator.GeneratedFile(file, content)
  }
}
