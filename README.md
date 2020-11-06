# fat-aar-android

[![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/kezong/fat-aar-android/blob/master/LICENSE)
[![Download](https://api.bintray.com/packages/kezong/maven/fat-aar/images/download.svg)](https://bintray.com/kezong/maven/fat-aar/_latestVersion)

- [中文文档](./README_CN.md)

The solution of merging aar works with [the android gradle plugin][3], the android plugin's version of the development is `3.0.1` and higher. (Tested in gradle plugin 3.0.1 - 4.1.0, and gradle 4.9 - 6.5)

## Getting Started

### Step 1: Apply plugin

Add snippet below to your root build script file:

```gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:xxx'
        classpath 'com.kezong:fat-aar:1.2.20'
    }
}
```

Add snippet below to the `build.gradle` of your android library:

```gradle
apply plugin: 'com.kezong.fat-aar'
```

### Step 2: Embed dependencies

change `implementation` or `api` to `embed` while you want to embed the dependency in the library. Like this:

```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')
    // java dependency
    embed project(path: ':lib-java', configuration:'default')
    // aar dependency
    embed project(path: ':lib-aar', configuration:'default')
    // aar dependency
    embed project(path: ':lib-aar2', configuration:'default')
    // local full aar dependency, just build in flavor1
    flavor1Embed project(path: ':lib-aar-local', configuration:'default')
    // local full aar dependency, just build in debug
    debugEmbed (name:'lib-aar-local2',ext:'aar')
    // remote jar dependency
    embed 'com.google.guava:guava:20.0'
    // remote aar dependency
    embed 'com.facebook.fresco:fresco:1.11.0'
    // don't want to embed in
    // implementation is not recommended because the dependency may be different with the version in application, resulting in the R class not found.
    compileOnly 'com.android.support:appcompat-v7:27.1.1'
}
```

### Transitive

#### Local Dependency
If you want to including local transitive dependencies in final artifact, you must add `embed` for transitive dependencies in your main library. 

For example, mainLib depend on subLib1, subLib1 depend on subLib2, If you want including all dependencies in final artifact, you must add `embed` for subLib1 and subLib2 in mainLib `build.gradle`

#### Remote Dependency
If you want to including all remote transitive dependencies which in pom file, you need change the `embed`'s transitive value to true in your `build.gradle`, like this:
```gradle
// the default value is false
// invalid for local aar dependency
configurations.embed.transitive = true
```
If you change the transitive value to true,and want to ignore a dependency in its POM file, you can add exclude keywords, like this:
```gradle
embed('com.facebook.fresco:fresco:1.11.0') {
    exclude(group:'com.facebook.soloader', module:'soloader')
}
```

**More usage see [example](./example).**

## About AAR File

AAR is a file format for android library.
The file itself is a zip file that containing useful stuff in android.
See [anatomy of an aar file here][2].

**support list for now:**

- [x] productFlavors
- [x] manifest merge
- [x] classes jar and external jars merge
- [x] res merge
- [x] assets merge
- [x] jni libs merge
- [x] R.txt merge
- [x] R.class merge
- [x] databinding merge

## Gradle Version Support
| Version | Gradle Plugin | Gradle |
| :--------: | :--------:|:-------:|
| 1.0.1 | 3.1.0 - 3.2.1 | 4.4-6.0 |
| 1.1.6 | 3.1.0 - 3.4.1 | 4.4-6.0 |
| 1.1.10| 3.0.0 - 3.4.1 | 4.1-6.0 |
| 1.2.6 | 3.0.0 - 3.5.0 | 4.1-6.0 |
| 1.2.8 | 3.0.0 - 3.5.9 | 4.1+ |
| 1.2.11 - 1.2.14 | 3.0.0 - 3.6.9 | 4.1+ |
| 1.2.15 - 1.2.16 | 3.0.0 - 4.0.2 | 4.1+|
| 1.2.17 | 3.0.0 - 4.0.2 | 4.9+ |
| 1.2.18+ | 3.0.0 - 4.1.0 | 4.9+ |

The following link which version of Gradle is required for each version of the Android Gradle plugin. For the best performance, you should use the latest possible version of both Gradle and the plugin.

[Plugin version and Required Gradle version](https://developer.android.google.cn/studio/releases/gradle-plugin.html)

## Version Log
- [1.2.20](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.20>)
  - Fix error that getName() in a null object. [#214](https://github.com/kezong/fat-aar-android/issues/214)
  - Rename r-classes.jar with applicationId.
- [1.2.19](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.19>)
  - Support embed aar that has no classes.jar [#157](https://github.com/kezong/fat-aar-android/issues/158)
  - Support embed aar that has no AndroidManifest.xml [#206](https://github.com/kezong/fat-aar-android/issues/206)
  - Fix bug that R.class not embed when publish to maven [#200](https://github.com/kezong/fat-aar-android/issues/200)
- [1.2.18](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.18>)
  - Adapt gradle plugin 4.1.0 [#201](https://github.com/kezong/fat-aar-android/issues/201)
- [1.2.17](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.17>)
  - Support databing merge [#25](https://github.com/kezong/fat-aar-android/issues/25) [#67](https://github.com/kezong/fat-aar-android/issues/67) [#142](https://github.com/kezong/fat-aar-android/issues/142)
  - Use Gradle's configuration avoidance APIs [#195](https://github.com/kezong/fat-aar-android/issues/195)
  - Support incremental build [#199](https://github.com/kezong/fat-aar-android/issues/199) [#185](https://github.com/kezong/fat-aar-android/issues/185)
  - Fix wrong directory for aar's jar libs [#154](https://github.com/kezong/fat-aar-android/issues/154)
- [1.2.16](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.16>)
  - Search for android build plugin version in full classpath [#172](https://github.com/kezong/fat-aar-android/issues/172)
  - Fixed a bug where resources might not be found when build in gradle version 4.0 [#163](https://github.com/kezong/fat-aar-android/issues/163)
- [1.2.15](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.15>)
  - adapt gradle plugin 4.0.0 [#147](https://github.com/kezong/fat-aar-android/issues/147)
  - support that the module can be indexed in AS 4.0.0 [#148](https://github.com/kezong/fat-aar-android/issues/148)
  - fix lint error [#152](https://github.com/kezong/fat-aar-android/issues/152)
- [1.2.12](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.12>)
  - Added support for specific build type and product flavor dependencies, like debugEmbed or flavorEmbed. [#135](https://github.com/kezong/fat-aar-android/issues/135) [#137](https://github.com/kezong/fat-aar-android/issues/137)
  - Fix some build warning
- [1.2.11](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.11>)
  - Fix build variants error in gradle plugin 3.6.+ [#126](https://github.com/kezong/fat-aar-android/issues/126)
  - Fix bug that remote recources symbol can not found in R.class when build with gradle plugin 3.6.0+
- [1.2.9](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.9>)
  - adapt gradle plugin 3.6.1 [#120](https://github.com/kezong/fat-aar-android/issues/120)
- [1.2.8](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.8>)
  - adapt gradle 6.0.0+ [#97](https://github.com/kezong/fat-aar-android/issues/97)
- [1.2.7](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.7>)
  - Fix manifest merge bug in gradle 3.5.0 [#62](https://github.com/kezong/fat-aar-android/issues/62) [#65](https://github.com/kezong/fat-aar-android/issues/65)
- [1.2.6](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.6>)
  - Adapt gradle plugin 3.5.0 [#53](https://github.com/kezong/fat-aar-android/issues/53) [#58](https://github.com/kezong/fat-aar-android/issues/58)
- [1.2.5](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.5>)
  - Fix task name repeat error [#48](https://github.com/kezong/fat-aar-android/issues/48)
  - If minifyEnabled, jar files would build into classes.jar
- [1.2.4](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.4>)
  - Fix jni and assets can't embed in windows platform [#37](https://github.com/kezong/fat-aar-android/issues/37)
- [1.2.3](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.3>)
  - Fix the problem that non-dependency R cannot be found [#11](https://github.com/kezong/fat-aar-android/issues/11) [#35](https://github.com/kezong/fat-aar-android/issues/35)
  - No longer need to add `compileOnly` for dependencies
  - Default value of transitive change to false
- [1.1.11](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.11>)
  - Fixed a problem where gradle plugin version might have misjudged [#28](https://github.com/kezong/fat-aar-android/issues/28)
  - Fixed LibraryManifestMerger.java build warning [#29](https://github.com/kezong/fat-aar-android/issues/29)
  - Optimize the merging rules of resource、assets、jni... [#27](https://github.com/kezong/fat-aar-android/issues/27)
- [1.1.10](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.10>)
  - Fixed jar merge bug when using gradle plugin 3.0.1 [#24](https://github.com/kezong/fat-aar-android/issues/24)
  - Fixed rebuild(./gradlew clean assemble) error [#24](https://github.com/kezong/fat-aar-android/issues/24)
- [1.1.8](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.8>)
  - Adapt new interface to avoid the warning [#10](https://github.com/kezong/fat-aar-android/issues/10)
  - Optimize AndroidManifest merge rules [#21](https://github.com/kezong/fat-aar-android/issues/21) [#23](https://github.com/kezong/fat-aar-android/issues/23)
- [1.1.7](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.7>)
  - Support embed R file when upload maven [#7](https://github.com/kezong/fat-aar-android/issues/7)
- [1.1.6](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.6>)
  - Adapt gradle plugin 3.3.0, 3.4.0, 3.4.1. [#4](https://github.com/kezong/fat-aar-android/issues/4) [#9](https://github.com/kezong/fat-aar-android/issues/9)
  - Adapt gradle 4.10.1, 5.0, 5.1, 5.1.1... [#8](https://github.com/kezong/fat-aar-android/issues/8)
  - Support sub-module's Flavor
  - Fix the problem that the class update of the sub-module is not timely
- [1.0.3](<https://github.com/kezong/fat-aar-android/releases/tag/v1.0.3>)
  - Fix assets merge
- [1.0.1](<https://github.com/kezong/fat-aar-android/releases/tag/v1.0.1>)
  - Support gradle plugin 3.1.0 - 3.2.1
  - Support R class file merge

## Known Defects or Issues

- **Proguard note.** Produce lots of(maybe) `Note: duplicate definition of library class`, while proguard is on. A workaround is to add `-dontnote` in `proguard-rules.pro`.
- **The overlay order of res merge is changed:** Embedded dependency has higher priority than other dependencies.
- **Res merge conflicts.** If the library res folder and embedded dependencies res have the same res Id(mostly `string/app_name`). A duplicate resources build exception will be thrown. To avoid res conflicts:
  - consider using a prefix to each res Id, both in library res and aar dependencies if possible. 
  - Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
  
## Thanks

- [android-fat-aar][1]
- [fat-aar-plugin][4]

[1]: https://github.com/adwiv/android-fat-aar
[2]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[3]: https://developer.android.com/studio/releases/gradle-plugin.html
[4]: https://github.com/Vigi0303/fat-aar-plugin
