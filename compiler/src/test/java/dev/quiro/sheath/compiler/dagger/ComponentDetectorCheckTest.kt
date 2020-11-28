package dev.quiro.sheath.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test

class ComponentDetectorCheckTest {

  @Test fun `a Dagger component causes an error`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Component
        
        @Component
        interface ComponentInterface
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (5, 1")
      assertThat(messages).contains(
          "Sheath cannot generate the code for Dagger components or subcomponents. In these " +
              "cases the Dagger annotation processor is required. Enabling the Dagger " +
              "annotation processor and turning on Sheath to generate Dagger factories is " +
              "redundant. Set 'generateDaggerFactories' to false."
      )
    }
  }

  @Test fun `a Dagger subcomponent is allowed`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Subcomponent
        
        @Subcomponent
        interface ComponentInterface
        """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a Dagger component causes an error inner class`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Component
        
        class OuterClass {
          @Component
          interface ComponentInterface
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (6, 3")
      assertThat(messages).contains(
          "Sheath cannot generate the code for Dagger components or subcomponents. In these " +
              "cases the Dagger annotation processor is required. Enabling the Dagger " +
              "annotation processor and turning on Sheath to generate Dagger factories is " +
              "redundant. Set 'generateDaggerFactories' to false."
      )
    }
  }

  @Test fun `a Dagger subcomponent in an inner class is allowed`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Subcomponent
        
        class OuterClass {
          @Subcomponent
          interface ComponentInterface
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
  private fun compile(
    vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = dev.quiro.sheath.compiler.compile(
      sources = sources,
      generateDaggerFactories = true,
      block = block
  )
}
