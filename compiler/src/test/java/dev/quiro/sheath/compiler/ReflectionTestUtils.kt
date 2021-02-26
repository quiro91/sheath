package dev.quiro.sheath.compiler

import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Modifier

val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)
val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)

internal inline fun <T, E : Executable> E.use(block: (E) -> T): T {
  // Deprecated since Java 9, but many projects still use JDK 8 for compilation.
  @Suppress("DEPRECATION")
  val original = isAccessible

  isAccessible = true
  return block(this).also {
    isAccessible = original
  }
}

/**
 * Creates a new instance of this class with the given arguments. This method assumes that this
 * class only declares a single constructor.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<T>.createInstance(
  vararg initargs: Any?
): T = declaredConstructors.single().use { it.newInstance(*initargs) } as T
