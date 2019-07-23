# fat-aar-android
[![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/kezong/fat-aar-android/blob/master/LICENSE)
[![Download](https://api.bintray.com/packages/kezong/maven/fat-aar/images/download.svg)](https://bintray.com/kezong/maven/fat-aar/_latestVersion)

该插件提供了将library以及它依赖的module一起打包成一个完整aar的解决方案，支持gradle plugin 3.0.1及以上。（目前测试的版本范围是gradle plugin 3.0.1 - 3.4.2，gradle 4.6 - 5.4.1）

## 如何使用

#### 第一步: Apply plugin

添加以下代码到你工程根目录下的`build.gradle`文件中:

```gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:xxx'
        classpath 'com.kezong:fat-aar:1.2.3'
    }
}
```

添加以下代码到你的主library的`build.gradle`中:

```gradle
apply plugin: 'com.kezong.fat-aar'
```

#### 第二步: Embed dependencies
- 将`implementation`或者`api`改成`embed`

代码所示：
```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')
    // java dependency
    embed project(path: ':lib-java', configuration:'default')
    // aar dependency
    embed project(path: ':lib-aar', configuration:'default')
    // aar dependency
    embed project(path: ':lib-aar2', configuration:'default')
    // local full aar dependency
    embed project(path: ':lib-aar-local', configuration:'default')
    // local full aar dependency
    embed (name:'lib-aar-local2',ext:'aar')
    // remote jar dependency
    embed 'com.google.guava:guava:20.0'
    // remote aar dependency
    embed 'com.facebook.fresco:fresco:1.11.0'
    // don't want to embed in
    implementation 'com.android.support:appcompat-v7:27.1.1'
}
```

### 多级依赖

#### 本地依赖

如果你想将本地所有相关的依赖项全部包含在最终产物中，你需要在你主library中对所有依赖都加上`embed`关键字

比如，mainLib依赖lib1，lib1依赖lib2，如果你想将所有依赖都打入最终产物，你必须在mainLib的`build.gradle`中对lib1以及lib2都加上`embed`关键字

#### 远程依赖

如果你想将所有远程依赖在pom中声明的依赖项同时打入在最终产物里的话，你需要在`build.gradle`中将`embed`的transitive值改为true，例如：
```gradle
// the default value is false
// invalid for local aar dependency
configurations.embed.transitive = true
```

如果你将transitive的值改成了true，并且想忽略pom文件中的某一个依赖项，你可以添加`exclude`关键字，例如：
```gradle
embed('com.facebook.fresco:fresco:1.11.0') {
    exclude(group:'com.facebook.soloader', module:'soloader')
}
```

**更多使用方式可参考 [example](./example).**

## 关于 AAR 文件
AAR是Android提供的一种官方文件形式；
该文件本身是一个Zip文件，并且包含Android里所有的元素；
可以参考 [aar文件详解][2].

**支持功能列表:**

- [x] 支持library以及module中含有flavor
- [x] AndroidManifest合并
- [x] classes以及jar合并
- [x] res合并
- [x] assets合并
- [x] jni合并
- [x] R.txt合并
- [x] R.class合并
- [ ] proguard合并（混淆合并现在看来有些问题，建议将所有混淆文件都写在主Library中）

## 常见问题

* **混淆日志：** 当开启proguard时，可能会产生大量的`Note: duplicate definition of library class`日志，如果你想忽略这些日志，你可以在`proguard-rules.pro`中加上`-dontnote`关键字；
* **资源冲突：** 如果library和module中含有同名的资源(比如 `string/app_name`)，编译将会报`duplication resources`的相关错误，有两种方法可以解决这个问题：
  * 考虑将library以及module中的资源都加一个前缀来避免资源冲突； 
  * 在`gradle.properties`中添加`android.disableResourceValidation=true`可以忽略资源冲突的编译错误，程序会采用第一个找到的同名资源作为实际资源，不建议这样做，如果资源同名但实际资源不一样会造成不可预期的问题。
    
## 更新日志
- [1.2.3](<https://github.com/kezong/fat-aar-android/releases/tag/v1.2.3>)
  - 修复未直接依赖的R类找不到的问题 [#11](https://github.com/kezong/fat-aar-android/issues/11) [#35](https://github.com/kezong/fat-aar-android/issues/35)
  - 不再需要为需要`embed`的依赖项主动添加`compileOnly`
  - `embed`的transitive默认值设置成false
- [1.1.11](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.11>)
  - 修复gradle plugin version有可能判断错误的问题 [#28](https://github.com/kezong/fat-aar-android/issues/28)
  - 修复LibraryManifestMerger.java中出现的build warning [#29](https://github.com/kezong/fat-aar-android/issues/29)
  - 优化resource、assets、jni等的合并规则，不再合并至"main"，而是合并至"variant" [#27](https://github.com/kezong/fat-aar-android/issues/27)
  - 修复重复build造成release包下jni丢失的问题 [#27](https://github.com/kezong/fat-aar-android/issues/27)
- [1.1.10](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.10>)
  - 修复使用gradle plugin 3.0.1时的jar合并错误 [#24](https://github.com/kezong/fat-aar-android/issues/24)
  - 修复无法正常rebuild（同时使用clean assemble）的问题 [#24](https://github.com/kezong/fat-aar-android/issues/24)
- [1.1.8](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.8>)
  - 弃用旧接口，处理编译时输出的warning [#10](https://github.com/kezong/fat-aar-android/issues/10)
  - 将AndroidManifest的合并规则由Application改为Library [#21](https://github.com/kezong/fat-aar-android/issues/21) [#23](https://github.com/kezong/fat-aar-android/issues/23)
- [1.1.7](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.7>)
  - 修复直接publish至maven时，aar的R文件未合并的问题 [#7](https://github.com/kezong/fat-aar-android/issues/7)
- [1.1.6](<https://github.com/kezong/fat-aar-android/releases/tag/v1.1.6>)
  - 适配gradle plugin 3.3.0+ [#4](https://github.com/kezong/fat-aar-android/issues/4) [#9](https://github.com/kezong/fat-aar-android/issues/9)
  - 适配gadle 4.10.0+ [#8](https://github.com/kezong/fat-aar-android/issues/8)
  - 支持子module的flavor编译
  - 修复子module的class文件编译不实时更新的问题
- [1.0.3](<https://github.com/kezong/fat-aar-android/releases/tag/v1.0.3>)
  - 修复assets未合并的问题
- [1.0.1](<https://github.com/kezong/fat-aar-android/releases/tag/v1.0.1>)
  - 支持gradle plugin 3.1.0 - 3.2.1
  - 支持资源合并，R文件合并
  
## 致谢
* [android-fat-aar][1]
* [fat-aar-plugin][4]

[1]: https://github.com/adwiv/android-fat-aar
[2]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[3]: https://developer.android.com/studio/releases/gradle-plugin.html
[4]: https://github.com/Vigi0303/fat-aar-plugin
