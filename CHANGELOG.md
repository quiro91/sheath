# Changelog

## Next Version

## 0.6.3 (2021-04-16)
* Fix bug when disabling incremental compilation for kapt stub generating task

## 0.6.2 (2021-04-15)

* Handle inner generic classes in factories for constructor injection properly.
* Generate a correct factory when a class has both constructor and member injection.
* Handle Lazy assisted parameters properly in generated assisted factories.
* Build and test Anvil with Kotlin 1.5.0-rc in CI.


## 0.6.0 (2021-03-13)

* This release upgrades Sheath to Kotlin 1.4.30. 
* This release upgrades Dagger to 2.33.
* Support Assisted Injection.

## 0.5.0 (2020-12-21)

* Support uppercase functions with the same name of a class.
* Support Kotlin 1.4.20

## 0.4.3 (2020-09-29)

* Fix resolution of named imports clashing with other imports.

## 0.4.2 (2020-09-28)

* Fix resolution of return type in @ContributesAndroidInjector

## 0.4.1 (2020-09-28)

* Fix absolute path being used in the plugin
* Fix @ContributesAndroidInjector class names

## 0.4.0 (2020-09-23)

* Improve configuration caching
* Support constructor injection for classes with bounded type parameters 

## 0.3.0 (2020-09-08)

* Support named imports.
* Support @Inject on constructors of classes with type parameters.

## 0.2.0 (2020-09-08)

* Rework import resolution.
* Improve support for @ContributesAndroidInjector modules.
* More error checking.

## 0.1.0 (2020-09-08)

* Initial release.
