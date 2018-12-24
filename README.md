# fat-aar-android

It works with [the android gradle plugin][3], the android plugin's version of the development is `3.0.1` and later.

## Getting Started

#### Step 1: Apply plugin

Add snippet below to your root build script file:

```gradle
buildscript {
    repositories {
        maven {
            url  "http://dl.bintray.com/kezong/maven"
        }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:xxx'
        classpath 'com.kezong:fat-aar:1.0.0'
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
    // aar project
    embed project(':aar-lib')
    // java project
    embed project(':java-lib')
    // java dependency
    embed 'com.google.guava:guava:20.0'
    // aar dependency
    embed 'com.facebook.fresco:fresco:1.2.0'
  
	compileOnly project(':aar-lib')
	compileOnly project(':java-lib')
	compileOnly 'com.google.guava:guava:20.0'
	compileOnly 'com.facebook.fresco:fresco:1.2.0'
	
    // other dependencies you don't want to embed in
    implementation 'com.squareup.okhttp3:okhttp:3.6.0'
}
```

## About AAR File

AAR is a file format for android library.
The file itself is a zip file that containing useful stuff in android.
See [anatomy of an aar file here][2].

**support list for now:**

- [x] manifest merge
- [x] classes jar and external jars merge
- [x] res merge
- [x] R.txt merge
- [x] assets merge
- [x] jni libs merge
- [x] proguard.txt merge
- [ ] lint.jar merge
- [ ] aidl merge?
- [ ] public.txt merge?

## Known Defects or Issues

* **Proguard note.** Produce lots of(maybe) `Note: duplicate definition of library class`, while proguard is on. A workaround is to add `-dontnote` in `proguard-rules.pro`.
* **The overlay order of res merge is changed:** Embedded dependency has higher priority than other dependencies.
* **Res merge conflicts.** If the library res folder and embedded dependencies res have the same res Id(mostly `string/app_name`). A duplicate resources build exception will be thrown. To avoid res conflicts:
 * consider using a prefix to each res Id, both in library res and aar dependencies if possible. 
 * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
  

## Thanks
[android-fat-aar][1]
[fat-aar-plugin][4]

[1]: https://github.com/adwiv/android-fat-aar
[2]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[3]: https://developer.android.com/studio/releases/gradle-plugin.html
[4]: https://github.com/Vigi0303/fat-aar-plugin