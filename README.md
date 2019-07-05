# fat-aar-android
[![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/kezong/fat-aar-android/blob/master/LICENSE)
[![Download](https://api.bintray.com/packages/kezong/maven/fat-aar/images/download.svg)](https://bintray.com/kezong/maven/fat-aar/_latestVersion)

- [中文文档](./README_CN.md)

The solution of merging aar works with [the android gradle plugin][3], the android plugin's version of the development is `3.0.1` and higher. (Tested in gradle plugin 3.0.1 - 3.4.1, and gradle 4.6 - 5.1.1)

## Getting Started

#### Step 1: Apply plugin

Add snippet below to your root build script file:

```gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:xxx'
        classpath 'com.kezong:fat-aar:1.1.7'
    }
}
```

Add snippet below to the `build.gradle` of your android library:

```gradle
apply plugin: 'com.kezong.fat-aar'
```

#### Step 2: Embed dependencies

change `implementation` or `api` to `embed` and add `compileOnly` while you want to embed the dependency in the library. Like this:

```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')

    // java dependency
    embed project(path: ':lib-java', configuration:'default')
    compileOnly project(path: ':lib-java')

    // aar dependency
    embed project(path: ':lib-aar', configuration:'default')
    compileOnly project(path: ':lib-aar')

    // aar dependency
    embed project(path: ':lib-aar2', configuration:'default')
    compileOnly project(path: ':lib-aar2')
    
    // aar dependency
    embed 'com.facebook.fresco:fresco:1.11.0'
    compileOnly 'com.facebook.fresco:fresco:1.11.0'
    
    // local aar dependency, you need add the flatDir first.
    embed (name:'lib-aar-local2',ext:'aar')
    compileOnly (name:'lib-aar-local2',ext:'aar')

    // local aar dependency
    embed project(path: ':lib-aar-local', configuration:'default')
    compileOnly project(path: ':lib-aar-local')

    // other dependencies you don't want to embed in
    implementation 'com.android.support:appcompat-v7:27.1.1'
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
- [x] proguard.txt merge
- [x] R.txt merge
- [x] R.class merge

## Known Defects or Issues

* **Proguard note.** Produce lots of(maybe) `Note: duplicate definition of library class`, while proguard is on. A workaround is to add `-dontnote` in `proguard-rules.pro`.
* **The overlay order of res merge is changed:** Embedded dependency has higher priority than other dependencies.
* **Res merge conflicts.** If the library res folder and embedded dependencies res have the same res Id(mostly `string/app_name`). A duplicate resources build exception will be thrown. To avoid res conflicts:
  * consider using a prefix to each res Id, both in library res and aar dependencies if possible. 
  * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
  

## Thanks
* [android-fat-aar][1]
* [fat-aar-plugin][4]

[1]: https://github.com/adwiv/android-fat-aar
[2]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[3]: https://developer.android.com/studio/releases/gradle-plugin.html
[4]: https://github.com/Vigi0303/fat-aar-plugin
