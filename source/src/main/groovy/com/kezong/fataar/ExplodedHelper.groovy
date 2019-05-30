package com.kezong.fataar

import com.google.common.base.Strings
import org.gradle.api.Project

/**
 * process jars and classes
 * Created by Vigi on 2017/1/20.
 * Modified by kezong on 2018/12/18
 */
class ExplodedHelper {

    static void processIntoJars(Project project,
                                Collection<AndroidArchiveLibrary> androidLibraries, Collection<File> jarFiles,
                                File folderOut) {
        Utils.logInfo('Merge jars')
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                Utils.logError('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            if (androidLibrary.localJars.isEmpty()) {
                Utils.logInfo("Not found jar file, Library:${androidLibrary.name}")
            } else {
                Utils.logInfo("Merge ${androidLibrary.name} jar file, Library:${androidLibrary.name}")
            }
            androidLibrary.localJars.each {
                Utils.logInfo(it.path)
            }
            project.copy {
                from(androidLibrary.localJars)
                into folderOut
            }
        }
        for (jarFile in jarFiles) {
            if (!jarFile.exists()) {
                Utils.logError('[warning]' + jarFile + ' not found!')
                continue
            }
            Utils.logInfo('copy jar from: ' + jarFile + " to " + folderOut.absolutePath)
            project.copy {
                from(jarFile)
                into folderOut
            }
        }
    }

    static void processIntoClasses(Project project,
                                   Collection<AndroidArchiveLibrary> androidLibraries, Collection<File> jarFiles,
                                   File folderOut) {
        Utils.logInfo('Merge classes')
        Collection<File> allJarFiles = new ArrayList<>()
        List<String> rPathList = new ArrayList<>()
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                Utils.logError('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            Utils.logInfo('[androidLibrary]' + androidLibrary.getName())
            allJarFiles.add(androidLibrary.classesJarFile)
            String packageName = androidLibrary.getPackageName()
            if (!Strings.isNullOrEmpty(packageName)) {
                rPathList.add(androidLibrary.getPackageName())
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
