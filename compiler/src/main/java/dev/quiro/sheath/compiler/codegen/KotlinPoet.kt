package dev.quiro.sheath.compiler.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import dagger.Lazy
import dev.quiro.sheath.compiler.SheathCompilationException
import dev.quiro.sheath.compiler.SheathComponentRegistrar
import dev.quiro.sheath.compiler.daggerDoubleCheckFqNameString
import dev.quiro.sheath.compiler.daggerLazyFqName
import dev.quiro.sheath.compiler.jvmSuppressWildcardsFqName
import dev.quiro.sheath.compiler.providerFqName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.io.ByteArrayOutputStream
import javax.inject.Provider

internal fun KtClassOrObject.asTypeName(): TypeName = requireFqName().asTypeName()

internal fun ClassDescriptor.asTypeName(): TypeName = fqNameSafe.asTypeName()

private fun FqName.asTypeName(): TypeName {
  return try {
    ClassName.bestGuess(asString())
  } catch (e: IllegalArgumentException) {
    // This happens when the class name starts with a lowercase character.
    TypeVariableName(asString())
  }
}

internal fun KtTypeReference.requireTypeName(
  module: ModuleDescriptor
): TypeName {
  fun PsiElement.fail(): Nothing = throw SheathCompilationException(
    message = "Couldn't resolve type: $text",
    element = this
  )

  fun KtTypeElement.requireTypeName(): TypeName {
    return when (this) {
      is KtUserType -> {
        val className = ClassName.bestGuess(requireFqName(module).asString())
        val typeArgumentList = typeArgumentList
        if (typeArgumentList != null) {
          className.parameterizedBy(
            typeArgumentList.arguments.map {
              (it.typeReference ?: it.fail()).requireTypeName(module)
            }
          )
        } else {
          className
        }
      }
      is KtFunctionType ->
        LambdaTypeName.get(
          receiver = receiver?.typeReference?.requireTypeName(module),
          parameters = parameterList
            ?.parameters
            ?.map { parameter ->
              val parameterReference = parameter.typeReference ?: parameter.fail()
              ParameterSpec.unnamed(parameterReference.requireTypeName(module))
            }
            ?: emptyList(),
          returnType = (returnTypeReference ?: fail())
            .requireTypeName(module)
        )
      is KtNullableType -> {
        (innerType ?: fail()).requireTypeName().copy(nullable = true)
      }
      else -> fail()
    }
  }

  return (typeElement ?: fail()).requireTypeName()
}

internal data class Parameter(
  val name: String,
  val typeName: TypeName,
  val providerTypeName: ParameterizedTypeName,
  val lazyTypeName: ParameterizedTypeName,
  val isWrappedInProvider: Boolean,
  val isWrappedInLazy: Boolean
) {
  val originalTypeName: TypeName = when {
    isWrappedInProvider -> providerTypeName
    isWrappedInLazy -> lazyTypeName
    else -> typeName
  }
}

internal fun List<KtCallableDeclaration>.mapToParameter(module: ModuleDescriptor): List<Parameter> =
  mapIndexed { index, parameter ->
    val typeElement = parameter.typeReference?.typeElement
    val typeFqName = typeElement?.fqNameOrNull(module)

    val isWrappedInProvider = typeFqName == providerFqName
    val isWrappedInLazy = typeFqName == daggerLazyFqName

    val typeName = when {
      parameter.requireTypeReference().isNullable() ->
        parameter.requireTypeReference().requireTypeName(module).copy(nullable = true)

      isWrappedInProvider || isWrappedInLazy ->
        typeElement!!.children
          .filterIsInstance<KtTypeArgumentList>()
          .single()
          .children
          .filterIsInstance<KtTypeProjection>()
          .single()
          .children
          .filterIsInstance<KtTypeReference>()
          .single()
          .requireTypeName(module)

      else -> parameter.requireTypeReference().requireTypeName(module)
    }.withJvmSuppressWildcardsIfNeeded(parameter)

    Parameter(
      name = "param$index",
      typeName = typeName,
      providerTypeName = typeName.wrapInProvider(),
      lazyTypeName = typeName.wrapInLazy(),
      isWrappedInProvider = isWrappedInProvider,
      isWrappedInLazy = isWrappedInLazy
    )
  }

internal fun <T : KtCallableDeclaration> TypeName.withJvmSuppressWildcardsIfNeeded(
  callableDeclaration: T
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards =
    callableDeclaration.typeReference?.hasAnnotation(jvmSuppressWildcardsFqName) ?: false

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = callableDeclaration.typeReference?.isGenericType() ?: false

  // Same for functions.
  val isFunctionType = callableDeclaration.typeReference?.isFunctionType() ?: false

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcardsKt31734()
    else -> this
  }
}

// TODO: remove with Kotlin 1.4.
// Notice the empty member. Instead of generating `@JvmSuppressWildcards Type` it generates
// `@JvmSuppressWildcards() Type`. This is necessary to avoid KT-31734 where the type is a function.
private fun TypeName.jvmSuppressWildcardsKt31734() =
  copy(
    annotations = this.annotations + AnnotationSpec.builder(JvmSuppressWildcards::class)
      .addMember("")
      .build()
  )

internal fun List<Parameter>.asArgumentList(
  asProvider: Boolean,
  includeModule: Boolean
): String {
  return this
    .let { list ->
      if (asProvider) {
        list.map { parameter ->
          when {
            parameter.isWrappedInProvider -> parameter.name
            parameter.isWrappedInLazy ->
              "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
            else -> "${parameter.name}.get()"
          }
        }
      } else list.map { it.name }
    }
    .let {
      if (includeModule) {
        val result = it.toMutableList()
        result.add(0, "module")
        result.toList()
      } else {
        it
      }
    }
    .joinToString()
}

private fun TypeName.wrapInProvider(): ParameterizedTypeName {
  return Provider::class.asClassName().parameterizedBy(this)
}

private fun TypeName.wrapInLazy(): ParameterizedTypeName {
  return Lazy::class.asClassName().parameterizedBy(this)
}

internal fun String.addGeneratedByComment(): String {
  return """
  // Generated by ${SheathComponentRegistrar::class.java.canonicalName}
  // https://github.com/square/anvil
  
  """.trimIndent() + this
}

internal fun FileSpec.writeToString(): String {
  val stream = ByteArrayOutputStream()
  stream.writer().use {
    writeTo(it)
  }
  return stream.toString()
}