package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.contributesAndroidInjector
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.extends
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.tschuchort.compiletesting.KotlinCompilation.Result
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Singleton

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class ContributesAndroidInjectorGeneratorTest(
  private val useDagger: Boolean
) {

  private val androidInjectorClass = AndroidInjector::class.java
  private val androidInjectorFactoryClass = AndroidInjector.Factory::class.java

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic
    fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test
  fun `a bind class is generated for a imported @ContributesAndroidInjector`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        import dagger.android.ContributesAndroidInjector
        
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @ContributesAndroidInjector abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      assertThat(subcomponentClass.isAnnotationPresent(Module::class.java)).isTrue()
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with different method name`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        import dagger.android.ContributesAndroidInjector
        
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @ContributesAndroidInjector abstract fun bindNamedFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindNamedFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      assertThat(subcomponentClass.isAnnotationPresent(Module::class.java)).isTrue()
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindNamedFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindNamedFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindNamedFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindNamedFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for @ContributesAndroidInjector following the method name`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      assertThat(subcomponentClass.isAnnotationPresent(Module::class.java)).isTrue()
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with modules`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent(modules = [MyModule::class])
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        class MyFragment
        @dagger.Module
        class MyModule
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector(modules = [MyModule::class])
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      assertThat(subcomponentClass.isAnnotationPresent(Module::class.java)).isTrue()
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      val subcomponentAnnotation = subcomponentInterface.getAnnotation(Subcomponent::class.java)
      assertThat(subcomponentAnnotation.modules.single().simpleName).isEqualTo("MyModule")
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with two modules`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent(modules = [MyModule::class])
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        import dagger.Module
        
        class MyFragment
        @Module
        class MyModule
        
        @Module
        class AnotherModule
        
        @Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector(modules = [MyModule::class, AnotherModule::class])
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      assertThat(subcomponentClass.isAnnotationPresent(Module::class.java)).isTrue()
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      val subcomponentAnnotation = subcomponentInterface.getAnnotation(Subcomponent::class.java)
      assertThat(subcomponentAnnotation.modules.find { it.simpleName == "MyModule" }).isNotNull()
      assertThat(subcomponentAnnotation.modules.find { it.simpleName == "AnotherModule" })
        .isNotNull()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with scope`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Scope
  @Subcomponent(modules = [MyModule::class])
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        import javax.inject.Singleton
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @Singleton
          @dagger.android.ContributesAndroidInjector
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.isAnnotationPresent(Singleton::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with non imported scope`() {
    /*
package com.squareup.test;
@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @my.Scope
  @Subcomponent(modules = [MyModule::class])
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @javax.inject.Singleton
          @dagger.android.ContributesAndroidInjector
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.isAnnotationPresent(Singleton::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with imported target`() {
    /*
package com.squareup.test;
import com.squareup.test.fragments.MyFragment
@Module
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test.fragments
        
        class MyFragment
        """,
      """
        package com.squareup.test
        import com.squareup.test.fragments.MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector with imported module`() {
    /*
package com.squareup.test;
import com.squareup.test.modules.MyModule
@Module
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}
  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);
  @Subcomponent(modules = [MyModule::class])
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test.modules
        
        @dagger.Module
        class MyModule
        """,
      """
        package com.squareup.test
        
        import com.squareup.test.modules.MyModule
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector(modules = [MyModule::class])
          abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindMyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      val subcomponentAnnotation = subcomponentInterface.getAnnotation(Subcomponent::class.java)
      assertThat(subcomponentAnnotation.modules.find { it.simpleName == "MyModule" }).isNotNull()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `two bind classes are generated for @ContributesAndroidInjector with named imports`() {
    compile(
      """
        package a.b.c
        
        class MyFragment
        """,
      """
        package d.e.f
        
        class MyFragment
        """,
      """
        package com.squareup.test
        
        import a.b.c.MyFragment
        import d.e.f.MyFragment as OtherFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector
          abstract fun bindMyFragment(): MyFragment
          
          @dagger.android.ContributesAndroidInjector
          abstract fun bindOtherFragment(): OtherFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindOtherFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  @Test
  fun `forms bug`() {
    compile(
      """
        package a.b.c
        
        class FileUploadBottomSheetFragment
        class FileUploadPreviewFragment
        """,
      """
        package d.e.f
        
        class FormsSearchActivity
        class FormsFragment
        """,
      """
        package com.squareup.test
        
        import a.b.c.FileUploadBottomSheetFragment
        import a.b.c.FileUploadPreviewFragment
        import d.e.f.FormsSearchActivity
        import d.e.f.FormsFragment
        import dagger.Module
        import dagger.android.ContributesAndroidInjector

        @Module
        abstract class DaggerModule1 {

          @ContributesAndroidInjector
          abstract fun bindFormsSearchActivity(): FormsSearchActivity

          @ContributesAndroidInjector
          abstract fun bindFileUploadBottomSheetFragment(): FileUploadBottomSheetFragment

          @ContributesAndroidInjector
          abstract fun bindFileUploadPreviewFragment(): FileUploadPreviewFragment

          @ContributesAndroidInjector
          abstract fun bindFormsFragment(): FormsFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("BindFormsSearchActivity")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java)).isTrue()
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java)).isTrue()
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(androidInjectorFactoryClass)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindOtherFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java)).isTrue()
      assertThat(subcomponentInterface.extends(androidInjectorClass)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
        .isTrue()
      assertThat(subcomponentFactoryInterface.extends(androidInjectorFactoryClass)).isTrue()
    }
  }

  private fun compile(
    vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = compileAnvil(
    *sources,
    enableDaggerAnnotationProcessor = useDagger,
    enableDaggerAndroidAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    block = block
  )
}
