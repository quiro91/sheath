package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.daggerAndroidContributesAndroidInjector
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.reference.AnnotatedReference
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.PropertyReference
import com.squareup.anvil.compiler.internal.reference.TypeReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
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
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class ContributesAndroidInjectorGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles.classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(daggerModuleFqName) }
      .forEach { clazz ->
        (clazz.companionObjects() + clazz)
          .asSequence()
          .flatMap { it.functions }
          .filter { it.isAnnotatedWith(daggerAndroidContributesAndroidInjector) }
          .forEach { function ->
            generateInjectorClass(codeGenDir, module, clazz, CallableReference(function = function))
          }
      }
  }

  private fun generateInjectorClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: ClassReference.Psi,
    declaration: CallableReference
  ): GeneratedFile {
    val packageName = clazz.packageFqName.safePackageString().dropLast(1)
    val returnType = declaration.type.asTypeName()
      .withJvmSuppressWildcardsIfNeeded(declaration.annotationReference, declaration.type)

    val bindingTargetName = declaration.type
      .asClassReference()
      .fqName
      .shortName()
      .asString()
    val className = "${clazz.generateClassName().relativeClassName}_${declaration.name.capitalize()}"
    val factoryClass = ClassName(packageName, className)
    val contributesAndroidInjector =
      declaration.annotationReference.annotations.find { it.fqName == daggerAndroidContributesAndroidInjector }
    val moduleClasses: List<ClassReference.Psi> = contributesAndroidInjector?.argumentAt("modules", 0)?.value()
      ?: emptyList()
    val scopeAnnotations = declaration.annotationReference
      .annotations
      .asSequence()
      .filterNot { it == contributesAndroidInjector }
      .map { annotation -> annotation.fqName }

    val content = FileSpec.buildFile(packageName, className) {

      val classBuilder = TypeSpec.classBuilder(factoryClass)
      val subcomponentFactoryClassName = ClassName(packageName, "Factory")
      val subcomponentFactory = TypeSpec.interfaceBuilder(subcomponentFactoryClassName)
        .addAnnotation(Subcomponent.Factory::class)
        .addSuperinterface(
          AndroidInjector.Factory::class.asClassName().parameterizedBy(returnType)
        )
        .build()

      val subcomponentClassName = "${bindingTargetName}Subcomponent"
      val fullSubcomponentFactoryClassName =
        ClassName(packageName, "$subcomponentClassName.Factory")
      val subcomponent = TypeSpec.interfaceBuilder(ClassName(packageName, subcomponentClassName))
        .addAnnotation(
          AnnotationSpec.builder(Subcomponent::class).apply {
            if (moduleClasses.isNotEmpty()) {
              val builder = StringBuilder("modules = [").apply {
                moduleClasses.forEachIndexed { index, _ ->
                  append("%T::class")
                  val next = if (index == moduleClasses.lastIndex) "]" else ","
                  append(next)
                }
              }
              val types = moduleClasses.map { ClassName(it.packageFqName.asString(), it.shortName) }
              addMember(builder.toString(), *types.toTypedArray())
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

    val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
    val file = File(directory, "$className.kt")
    check(file.parentFile.exists() || file.parentFile.mkdirs()) {
      "Could not generate package directory: ${file.parentFile}"
    }
    file.writeText(content)

    return GeneratedFile(file, content)
  }

  override fun isApplicable(context: AnvilContext): Boolean {
    return context.generateFactories
  }

  private class CallableReference(
    private val function: FunctionReference.Psi? = null,
    private val property: PropertyReference.Psi? = null
  ) {

    init {
      if (function == null && property == null) {
        throw AnvilCompilationException(
          "Cannot create a CallableReference wrapper without a " +
            "function OR a property"
        )
      }
    }

    val declaringClass = function?.declaringClass ?: property!!.declaringClass

    val visibility
      get() = function?.visibility() ?: property!!.visibility()

    val fqName = function?.fqName ?: property!!.fqName
    val name = function?.name ?: property!!.name

    val isProperty = property != null

    val constructorParameters: List<ConstructorParameter> =
      function?.parameters?.mapToConstructorParameters() ?: emptyList()

    val type: TypeReference = function?.let {
      it.returnTypeOrNull() ?: throw AnvilCompilationExceptionFunctionReference(
        message = "Dagger provider methods must specify the return type explicitly when using " +
          "Anvil. The return type cannot be inferred implicitly.",
        functionReference = it
      )
    } ?: property!!.type()
    val annotationReference: AnnotatedReference = function ?: property!!

    fun isAnnotatedWith(fqName: FqName): Boolean {
      return function?.isAnnotatedWith(fqName) ?: property!!.isAnnotatedWith(fqName)
    }
  }
}
