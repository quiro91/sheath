@file:Suppress("unused")

package dev.quiro.sheath.compiler.dagger

// These classes are used in some of the unit tests. Don't touch them.

object Factory

object Date

abstract class OuterClass constructor(
  @Suppress("UNUSED_PARAMETER") innerClass: InnerClass
) {
  class InnerClass
}
