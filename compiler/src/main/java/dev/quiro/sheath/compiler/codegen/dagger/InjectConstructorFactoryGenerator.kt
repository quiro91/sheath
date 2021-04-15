package dev.quiro.sheath.compiler.codegen.dagger

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmStatic
import dagger.internal.Factory
import dev.quiro.sheath.compiler.codegen.CodeGenerator.GeneratedFile
import dev.quiro.sheath.compiler.codegen.PrivateCodeGenerator
import dev.quiro.sheath.compiler.codegen.asArgumentList
import dev.quiro.sheath.compiler.codegen.asClassName
import dev.quiro.sheath.compiler.codegen.buildFile
import dev.quiro.sheath.compiler.codegen.classesAndInnerClasses
import dev.quiro.sheath.compiler.codegen.createGeneratedFile
import dev.quiro.sheath.compiler.codegen.fqNameOrNull
import dev.quiro.sheath.compiler.codegen.hasAnnotation
import dev.quiro.sheath.compiler.codegen.injectConstructor
import dev.quiro.sheath.compiler.codegen.mapToParameter
import dev.quiro.sheath.compiler.daggerDoubleCheckFqNameString
import dev.quiro.sheath.compiler.generateClassName
import dev.quiro.sheath.compiler.injectFqName
import dev.quiro.sheath.compiler.safePackageString
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File
import java.util.Locale.US

internal class InjectConstructorFactoryGenerator : PrivateCodeGenerator() {

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .forEach { clazz ->
        clazz.injectConstructor(injectFqName)
          ?.let {
            generateFactoryClass(codeGenDir, module, clazz, it)
          }
      }
  }

  private fun generateFactoryClass(
    codeGenDir: File,
    module: ModuleDescriptor,
    clazz: KtClassOrObject,
    constructor: KtConstructor<*>
  ): GeneratedFile {
    val packageName = clazz.containingKtFile.packageFqName.safePackageString()
    val className = "${clazz.generateClassName()}_Factory"

    val memberInjectProperties = clazz.children
      .asSequence()
      .filterIsInstance<KtClassBody>()
      .flatMap { it.properties.asSequence() }
      .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
      .filter { it.hasAnnotation(injectFqName) }
      .toList()

    val parameters = constructor.valueParameters
      .plus(memberInjectProperties)
      .mapToParameter(module)

    val constructorSize = constructor.valueParameters.size

    val constructorInjectParameters = parameters.take(constructorSize)
    val memberInjectParameters = parameters.drop(constructorSize)

    val typeParameters = clazz.typeParameterList
      ?.parameters
      ?.mapNotNull { parameter ->
        val extendsBound = parameter.extendsBound?.fqNameOrNull(module)?.asClassName(module)
        if (parameter.fqNameOrNull(module) == null) {
          TypeVariableName(parameter.nameAsSafeName.asString(), listOfNotNull(extendsBound))
        } else {
          null
        }
      }
      ?: emptyList()

    val factoryClass = ClassName(packageName, className)
    val factoryClassParameterized =
      if (typeParameters.isEmpty()) factoryClass else factoryClass.parameterizedBy(typeParameters)

    val classType = clazz.asClassName().let {
      if (typeParameters.isEmpty()) it else it.parameterizedBy(typeParameters)
    }

    val content = FileSpec.buildFile(packageName, className) {
      val canGenerateAnObject = parameters.isEmpty()
      val classBuilder = if (canGenerateAnObject) {
        TypeSpec.objectBuilder(factoryClass)
      } else {
        TypeSpec.classBuilder(factoryClass)
      }
      typeParameters.forEach { classBuilder.addTypeVariable(it) }

      classBuilder
        .addSuperinterface(Factory::class.asClassName().parameterizedBy(classType))
        .apply {
          if (parameters.isNotEmpty()) {
            primaryConstructor(
              FunSpec.constructorBuilder()
                .apply {
                  parameters.forEach { parameter ->
                    addParameter(parameter.name, parameter.providerTypeName)
                  }
                }
                .build()
            )

            parameters.forEach { parameter ->
              addProperty(
                PropertySpec.builder(parameter.name, parameter.providerTypeName)
                  .initializer(parameter.name)
                  .addModifiers(PRIVATE)
                  .build()
              )
            }
          }
        }
        .addFunction(
          FunSpec.builder("get")
            .addModifiers(OVERRIDE)
            .returns(classType)
            .apply {
              val newInstanceArgumentList = constructorInjectParameters.asArgumentList(
                asProvider = true,
                includeModule = false
              )

              if (memberInjectParameters.isEmpty()) {
                addStatement("return newInstance($newInstanceArgumentList)")
              } else {
                addStatement("val instance = newInstance($newInstanceArgumentList)")

                val memberInjectorClassName = "${clazz.generateClassName()}_MembersInjector"
                val memberInjectorClass = ClassName(packageName, memberInjectorClassName)

                memberInjectParameters.forEachIndexed { index, parameter ->

                  val property = memberInjectProperties[index]
                  val propertyName = property.nameAsSafeName.asString()
                  val functionName = "inject${propertyName.capitalize(US)}"

                  val param = when {
                    parameter.isWrappedInProvider -> parameter.name
                    parameter.isWrappedInLazy ->
                      "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
                    else -> parameter.name + ".get()"
                  }

                  addStatement("%T.$functionName(instance, $param)", memberInjectorClass)
                }
                addStatement("return instance")
              }
            }
            .build()
        )
        .apply {
          val builder = if (canGenerateAnObject) this else TypeSpec.companionObjectBuilder()
          builder
            .addFunction(
              FunSpec.builder("create")
                .jvmStatic()
                .apply {
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters)
                  }
                  if (canGenerateAnObject) {
                    addStatement("return this")
                  } else {
                    parameters.forEach { parameter ->
                      addParameter(parameter.name, parameter.providerTypeName)
                    }

                    val argumentList = parameters.asArgumentList(
                      asProvider = false,
                      includeModule = false
                    )

                    addStatement(
                      "return %T($argumentList)",
                      factoryClassParameterized
                    )
                  }
                }
                .returns(factoryClassParameterized)
                .build()
            )
            .addFunction(
              FunSpec.builder("newInstance")
                .jvmStatic()
                .apply {
                  if (typeParameters.isNotEmpty()) {
                    addTypeVariables(typeParameters)
                  }
                  constructorInjectParameters.forEach { parameter ->
                    addParameter(
                      name = parameter.name,
                      type = parameter.originalTypeName
                    )
                  }
                  val argumentsWithoutModule = constructorInjectParameters.joinToString { it.name }

                  addStatement("return %T($argumentsWithoutModule)", classType)
                }
                .returns(classType)
                .build()
            )
            .build()
            .let {
              if (!canGenerateAnObject) {
                addType(it)
              }
            }
        }
        .build()
        .let { addType(it) }
    }

    return createGeneratedFile(codeGenDir, packageName, className, content)
  }
}
