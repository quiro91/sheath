# Sheath

[![Maven Central](https://img.shields.io/maven-central/v/dev.quiro.sheath/gradle-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.quiro.sheath%22)
[![CI](https://github.com/quiro91/sheath/workflows/CI/badge.svg)](https://github.com/quiro91/sheath/actions?query=branch%3Amain)

Sheath is a Kotlin compiler plugin created to improve build performances when using Dagger and Dagger-Android.
Sheath allows you to generate Factory classes that usually the Dagger annotation processor would 
generate for `@Provides` methods, `@Inject` constructors, `@Inject` fields and `@ContributesAndroidInjector` methods. 
The benefit of this feature is that you don't need to enable the Dagger annotation processor in this module. That often 
means you can skip KAPT and the stub generating task. In addition Sheath generates Kotlin instead 
of Java code, which allows Gradle to skip the Java compilation task. The result is faster 
builds.

Sheath was forked from [Anvil](https://github.com/square/anvil), an amazing project which helped me understanding
Kotlin compiler plugins.

## Setup

Coming soon

## Performance

Coming soon

## Limitations

#### No Java support

Sheath is a Kotlin compiler plugin, thus Java isn’t supported. You can use Sheath in
modules with mixed Java and Kotlin code for Kotlin classes, though.

#### Incremental Kotlin compilation breaks compiler plugins

There are two bugs that affect the Sheath Kotlin compiler plugin:
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