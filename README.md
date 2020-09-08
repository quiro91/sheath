# Sheath

[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.anvil/gradle-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.squareup.anvil%22)
[![CI](https://github.com/quiro91/sheath/workflows/CI/badge.svg)](https://github.com/quiro91/sheath/actions?query=branch%3Amain)

Sheath is a Kotlin compiler plugin created to improve build performances when using Dagger and Dagger-Android.
Sheath allows you to generate Factory classes that usually the Dagger annotation processor would 
generate for `@Provides` methods, `@Inject` constructors, `@Inject` fields and `@ContributesAndroidInjector` methods. 
The benefit of this feature is that you don't need to enable the Dagger annotation processor in this module. That often 
means you can skip KAPT and the stub generating task. In addition Sheath generates Kotlin instead 
of Java code, which allows Gradle to skip the Java compilation task. The result is faster 
builds.

## Setup

Coming soon

## Contributed bindings

The `@ContributesBinding` annotation generates a Dagger binding method for an annotated class and 
contributes this binding method to the given scope. Imagine this example:
```kotlin
interface Authenticator

class RealAuthenticator @Inject constructor() : Authenticator

@Module
@ContributesTo(AppScope::class)
abstract class AuthenticatorModule {
  @Binds abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
}
```
This is a lot of boilerplate if you always want to use `RealAuthenticator` when injecting
`Authenticator`. You can replace this entire Dagger module with the `@ContributesBinding` 
annotation. The equivalent would be:
```kotlin
interface Authenticator

@ContributesBinding(AppScope::class)
class RealAuthenticator @Inject constructor() : Authenticator
```

## Exclusions

Dagger modules and component interfaces can be excluded in two different levels.

One class can always replace another one. This is especially helpful for modules that provide
different bindings for instrumentation tests, e.g.

```kotlin
@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [DevelopmentApplicationModule::class]
)
object DevelopmentApplicationTestModule {
  @Provides
  fun provideEndpointSelector(): EndpointSelector = TestingEndpointSelector
}
```

The compiler plugin will find both classes on the classpath. Adding both modules
`DevelopmentApplicationModule` and `DevelopmentApplicationTestModule` to the Dagger graph would
lead to duplicate bindings. Anvil sees that the test module wants to replace the other and
ignores it. This replacement rule has a global effect for all applications which are including the
classes on the classpath.

Applications can exclude Dagger modules and component interfaces individually without affecting
other applications.

```kotlin
@MergeComponent(
  scope = AppScope::class,
  exclude = [
    DaggerModule::class
  ]
)
interface AppComponent
```

In a perfect build graph it’s unlikely that this feature is needed. However, due to legacy modules,
wrong imports and deeply nested dependency chains applications might need to make use of it. The
exclusion rule does what it implies. In this specific example `DaggerModule` wishes to be
contributed to this scope, but it has been excluded for this component and thus is not added.

## Advantages

Adding Dagger modules to components in a large modularized codebase with many application targets
is overhead. You need to know where components are defined when creating a new Dagger module and
which modules to add when setting up a new application. This task involves many syncs in the IDE
after adding new module dependencies in the build graph. The process is tedious and cumbersome.
With Anvil you only add a dependency in your build graph and then you can immediately test
the build.

Aligning the build graph and Dagger's dependency graph brings a lot of consistency. If code is on
the compile classpath, then it's also included in the Dagger dependency graph.

Modules implicitly have a scope, if provided objects are tied to a scope. Now the scope of a module
is clear without looking at any binding.

With Anvil you don't need any composite Dagger module anymore, which only purpose is to
combine multiple modules to avoid repeating the setup for multiple applications. Composite modules
easily become hairballs. If one application wants to exclude a module, then it has to repeat the
setup. These forked graphs are painful and confusing. With Dagger you want to make the decision
which modules fulfill dependencies as late as possible, ideally in the application module.
Anvil makes this approach a lot easier by generating the code for included modules. Composite
modules are redundant. You make the decision which bindings to use by importing the desired module
in the application module.

## Performance

Coming soon

## Limitations

#### No Java support

Sheath is a Kotlin compiler plugin, thus Java isn’t supported. You can use Sheath in
modules with mixed Java and Kotlin code for Kotlin classes, though.

#### Incremental Kotlin compilation breaks compiler plugins

There are two bugs that affect the Anvil Kotlin compiler plugin:
* [Incremental compilation breaks compiler plugins](https://youtrack.jetbrains.com/issue/KT-38570)
* [AnalysisResult.RetryWithAdditionalRoots crashes during incremental compilation with java classes in classpath](https://youtrack.jetbrains.com/issue/KT-38576)

The Gradle plugin implements workarounds for these bugs, so you shouldn't notice them. Side effects
are that incremental Kotlin compilation is disabled for stub generating tasks (which don't run a
full compilation before KAPT anyways). The flag `usePreciseJavaTracking` is disabled, if the
module contains Java code.

## License

    Copyright 2020 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.