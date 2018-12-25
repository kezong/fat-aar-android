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
        LogUtil.logInfo('Merge jars')
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                LogUtil.logError('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            if (androidLibrary.localJars.isEmpty()) {
                LogUtil.logInfo("Not found jar file, Library:${androidLibrary.name}")
            } else {
                LogUtil.logInfo("Merge ${androidLibrary.name} jar file, Library:${androidLibrary.name}")
            }
            androidLibrary.localJars.each {
                LogUtil.logInfo(it.path)
            }
            project.copy {
                from(androidLibrary.localJars)
                into folderOut
            }
        }
        for (jarFile in jarFiles) {
            if (!jarFile.exists()) {
                LogUtil.logError('[warning]' + jarFile + ' not found!')
                continue
            }
            LogUtil.logInfo('copy jar from: ' + jarFile)
            project.copy {
                from(jarFile)
                into folderOut
            }
        }
    }

    static void processIntoClasses(Project project,
                                   Collection<AndroidArchiveLibrary> androidLibraries, Collection<File> jarFiles,
                                   File folderOut) {
        LogUtil.logInfo('Merge classes')
        Collection<File> allJarFiles = new ArrayList<>()
        List<String> rPathList = new ArrayList<>()
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                LogUtil.logError('[warning]' + androidLibrary.rootFolder + ' not found!')
                continue
            }
            LogUtil.logInfo('[androidLibrary]' + androidLibrary.getName())
            allJarFiles.add(androidLibrary.classesJarFile)
            String packageName = androidLibrary.getPackageName()
            if (!Strings.isNullOrEmpty(packageName)) {
                rPathList.add(androidLibrary.getPackageName())
            }
        }
        for (jarFile in allJarFiles) {
            LogUtil.logInfo('copy classes from: ' + jarFile)
            project.copy {
                from project.zipTree(jarFile)
                into folderOut
                exclude 'META-INF/'
            }
        }
    }
}
