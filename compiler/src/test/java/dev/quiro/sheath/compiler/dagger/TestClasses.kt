@file:Suppress("unused")

package dev.quiro.sheath.compiler.dagger

// These classes are used in some of the unit tests. Don't touch them.

object Factory

abstract class OuterClass constructor(innerClass: InnerClass) {
  class InnerClass
}
