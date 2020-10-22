package com.kezong.fataar

import org.gradle.api.Project

/**
 * process jars and classes
 * Created by Vigi on 2017/1/20.
 * Modified by kezong on 2018/12/18
 */
class ExplodedHelper {

    static void processLibsIntoLibs(Project project,
                                    Collection<AndroidArchiveLibrary> androidLibraries,
                                    Collection<File> jarFiles,
                                    File folderOut) {
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                Utils.logInfo('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            if (androidLibrary.localJars.isEmpty()) {
                Utils.logInfo("Not found jar file, Library: ${androidLibrary.name}")
            } else {
                Utils.logInfo("Merge ${androidLibrary.name} local jar files")
                project.copy {
                    from(androidLibrary.localJars)
                    into(folderOut)
                }
            }
        }
        for (jarFile in jarFiles) {
            if (jarFile.exists()) {
                Utils.logInfo("Copy jar from: $jarFile to $folderOut.absolutePath")
                project.copy {
                    from(jarFile)
                    into(folderOut)
                }
            } else {
                Utils.logInfo('[warning]' + jarFile + ' not found!')
            }
        }
    }

    static void processClassesJarInfoClasses(Project project,
                                             Collection<AndroidArchiveLibrary> androidLibraries,
                                             File folderOut) {
        Utils.logInfo('Merge ClassesJar')
        Collection<File> allJarFiles = new ArrayList<>()
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                Utils.logInfo('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            allJarFiles.add(androidLibrary.classesJarFile)
        }
        for (jarFile in allJarFiles) {
            if (!jarFile.exists()) {
                continue;
            }
            project.copy {
                from project.zipTree(jarFile)
                into folderOut
                exclude 'META-INF/'
            }
        }
    }

    static void processLibsIntoClasses(Project project,
                                       Collection<AndroidArchiveLibrary> androidLibraries,
                                       Collection<File> jarFiles,
                                       File folderOut) {
        Utils.logInfo('Merge Libs')
        Collection<File> allJarFiles = new ArrayList<>()
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                Utils.logInfo('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            Utils.logInfo('[androidLibrary]' + androidLibrary.getName())
            allJarFiles.addAll(androidLibrary.localJars)
        }
        for (jarFile in jarFiles) {
            if (jarFile.exists()) {
                allJarFiles.add(jarFile)
            }
        }
        for (jarFile in allJarFiles) {
            project.copy {
                from project.zipTree(jarFile)
                into folderOut
                exclude 'META-INF/'
            }
        }
    }
}
