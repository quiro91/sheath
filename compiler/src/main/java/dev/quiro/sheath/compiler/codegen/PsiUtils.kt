package dev.quiro.sheath.compiler.codegen

import dev.quiro.sheath.compiler.SheathCompilationException
import dev.quiro.sheath.compiler.getAllSuperTypes
import dev.quiro.sheath.compiler.jvmSuppressWildcardsFqName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

private val kotlinAnnotations = listOf(jvmSuppressWildcardsFqName)

internal fun KtFile.classesAndInnerClasses(): Sequence<KtClassOrObject> {
  val children = findChildrenByClass(KtClassOrObject::class.java)

  return generateSequence(children.toList()) { list ->
    list
        .flatMap {
          it.declarations.filterIsInstance<KtClassOrObject>()
        }
        .ifEmpty { null }
  }.flatMap { it.asSequence() }
}

internal fun KtNamedDeclaration.requireFqName(): FqName = requireNotNull(fqName) {
  "fqName was null for $this, $nameAsSafeName"
}

internal fun KtAnnotated.isInterface(): Boolean = this is KtClass && this.isInterface()

internal fun KtAnnotated.hasAnnotation(fqName: FqName): Boolean {
  return findAnnotation(fqName) != null
}

internal fun KtAnnotated.findAnnotation(fqName: FqName): KtAnnotationEntry? {
  val annotationEntries = annotationEntries
  if (annotationEntries.isEmpty()) return null

  // Look first if it's a Kotlin annotation. These annotations are usually not imported and the
  // remaining checks would fail.
  annotationEntries.firstOrNull { annotation ->
    kotlinAnnotations
        .any { kotlinAnnotationFqName ->
          val text = annotation.text
          text.startsWith("@${kotlinAnnotationFqName.shortName()}") ||
              text.startsWith("@$kotlinAnnotationFqName")
        }
  }?.let { return it }

  // Check if the fully qualified name is used, e.g. `@dagger.Module`.
  val annotationEntry = annotationEntries.firstOrNull {
    it.text.startsWith("@${fqName.asString()}")
  }
  if (annotationEntry != null) return annotationEntry

  // Check if the simple name is used, e.g. `@Module`.
  val annotationEntryShort = annotationEntries
      .firstOrNull {
        it.shortName == fqName.shortName()
      }
      ?: return null

  val importPaths = containingKtFile.importDirectives.mapNotNull { it.importPath }

  // If the simple name is used, check that the annotation is imported.
  val hasImport = importPaths.any { it.fqName == fqName }
  if (hasImport) return annotationEntryShort

  // Look for star imports and make a guess.
  val hasStarImport = importPaths
      .filter { it.isAllUnder }
      .any {
        fqName.asString().startsWith(it.fqName.asString())
      }
  if (hasStarImport) return annotationEntryShort

  return null
}

internal fun PsiElement.fqNameOrNull(
  module: ModuleDescriptor
): FqName? {
  // Usually it's the opposite way, the require*() method calls the nullable method. But in this
  // case we'd like to preserve the better error messages in case something goes wrong.
  return try {
    requireFqName(module)
  } catch (e: SheathCompilationException) {
    null
  }
}

internal fun PsiElement.requireFqName(
  module: ModuleDescriptor
): FqName {
  val containingKtFile = parentsWithSelf
      .filterIsInstance<KtClassOrObject>()
      .first()
      .containingKtFile

  fun failTypeHandling(): Nothing = throw SheathCompilationException(
      "Don't know how to handle Psi element: $text",
      element = this
  )

  val classReference = when (this) {
    // If a fully qualified name is used, then we're done and don't need to do anything further.
    is KtDotQualifiedExpression -> return FqName(text)
    is KtNameReferenceExpression -> getReferencedName()
    is KtUserType -> {
      val isGenericType = children.any { it is KtTypeArgumentList }
      if (isGenericType) {
        referencedName ?: failTypeHandling()
      } else {
        val text = text

        // Sometimes a KtUserType is a fully qualified name. Give it a try and return early.
        if (text.contains(".") && text[0].isLowerCase()) {
          module
            .resolveClassByFqName(FqName(text), FROM_BACKEND)
            ?.let { return it.fqNameSafe }
        }
        // We can't use referencedName here. For inner classes like "Outer.Inner" it would only
        // return "Inner", whereas text returns "Outer.Inner", what we expect.
        text
      }
    }
    is KtTypeReference -> {
      val children = children
      if (children.size == 1) {
        try {
          // Could be a KtNullableType or KtUserType.
          return children[0].requireFqName(module)
        } catch (e: SheathCompilationException) {
          // Fallback to the text representation.
          text
        }
      } else {
        text
      }
    }
    is KtNullableType -> return innerType?.requireFqName(module) ?: failTypeHandling()
    else -> failTypeHandling()
  }

  // First look in the imports for the reference name. If the class is imported, then we know the
  // fully qualified name.
  containingKtFile.importDirectives
      .mapNotNull { it.importPath }
      .firstOrNull {
        it.fqName.shortName()
            .asString() == classReference
      }
      ?.let { return it.fqName }

  // If there is no import, then try to resolve the class with the same package as this file.
  module
      .resolveClassByFqName(
          FqName("${containingKtFile.packageFqName}.$classReference"),
          FROM_BACKEND
      )
      ?.let { return it.fqNameSafe }

  // Maybe it's a type alias?
  module
    .findTypeAliasAcrossModuleDependencies(
      ClassId(containingKtFile.packageFqName, Name.identifier(classReference))
    )
    ?.let { return it.fqNameSafe }

  // If this doesn't work, then maybe a class from the Kotlin package is used.
  module.resolveClassByFqName(FqName("kotlin.$classReference"), FROM_BACKEND)
      ?.let { return it.fqNameSafe }

  // If this doesn't work, then maybe a class from the Kotlin package is used.
  module.resolveClassByFqName(FqName("kotlin.collections.$classReference"), FROM_BACKEND)
      ?.let { return it.fqNameSafe }

  findFqNameInSuperTypes(module, classReference)
      ?.let { return it }

  containingKtFile.importDirectives
    .asSequence()
    .filter { it.isAllUnder }
    .mapNotNull {
      // This fqName is the everything in front of the star, e.g. for "import java.io.*" it
      // returns "java.io".
      it.importPath?.fqName
    }
    .forEach { importFqName ->
      module
        .resolveClassByFqName(FqName("$importFqName.$classReference"), FROM_BACKEND)
        ?.let { return it.fqNameSafe }

      module
        .findTypeAliasAcrossModuleDependencies(
          ClassId(importFqName, Name.identifier(classReference))
        )
        ?.let { return it.fqNameSafe }
    }

  // Everything else isn't supported.
  throw SheathCompilationException(
      "Couldn't resolve FqName $classReference for Psi element: $text",
      element = this
  )
}

private fun PsiElement.findFqNameInSuperTypes(
  module: ModuleDescriptor,
  classReference: String
): FqName? {
  fun tryToResolveClassFqName(outerClass: FqName): FqName? =
    module
        .resolveClassByFqName(FqName("$outerClass.$classReference"), FROM_BACKEND)
        ?.fqNameSafe

  val clazz = parents.filterIsInstance<KtClassOrObject>().first()
  tryToResolveClassFqName(clazz.requireFqName())?.let { return it }

  // At this point we can't work with Psi APIs anymore. We need to resolve the super types and try
  // to find inner class in them.
  val descriptor = module.resolveClassByFqName(clazz.requireFqName(), FROM_BACKEND)
      ?: throw SheathCompilationException(
          message = "Couldn't resolve class descriptor for ${clazz.requireFqName()}",
          element = clazz
      )

  return listOf(descriptor.defaultType).getAllSuperTypes()
      .mapNotNull { tryToResolveClassFqName(it) }
      .firstOrNull()
}

internal fun KtClassOrObject.functions(
  includeCompanionObjects: Boolean
): List<KtNamedFunction> {
  val elements = children.toMutableList()
  if (includeCompanionObjects) {
    elements += companionObjects.flatMap { it.children.toList() }
  }

  return elements
      .filterIsInstance<KtClassBody>()
      .flatMap { it.functions }
}

fun KtTypeReference.isNullable(): Boolean = typeElement is KtNullableType

fun KtTypeReference.isGenericType(): Boolean {
  val typeElement = typeElement ?: return false
  val children = typeElement.children

  if (children.size != 2) return false
  return children[1] is KtTypeArgumentList
}

fun KtTypeReference.isFunctionType(): Boolean = typeElement is KtFunctionType

fun KtClassOrObject.isGenericClass(): Boolean = typeParameterList != null

fun KtCallableDeclaration.requireTypeReference(): KtTypeReference =
  typeReference ?: throw SheathCompilationException(
    "Couldn't obtain type reference.", element = this
  )
