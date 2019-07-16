package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection

/**
 * @author kezong on 2019/7/16.
 */
class VersionAdapter {

    private Project mProject

    private LibraryVariant mVariant

    private String mGradlePluginVersion

    VersionAdapter(Project project, LibraryVariant variant, String version) {
        mProject = project
        mVariant = variant
        mGradlePluginVersion = version
    }

    ConfigurableFileCollection getClassPathDirFiles() {
        ConfigurableFileCollection classpath
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.2.0") >= 0) { // >= Versions 3.2.X
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/" +
                    "javac/${mVariant.name}/compile${mVariant.name.capitalize()}JavaWithJavac/classes")
        } else { // Versions 3.0.x and 3.1.x
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/classes/${mVariant.dirName}")
        }
        return classpath
    }

    ConfigurableFileCollection getRClassPath() {
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mProject.files("${mProject.buildDir.path}/intermediates/" + "compile_only_not_namespaced_r_class_jar/"
                    + "${mVariant.name}/generate${mVariant.name.capitalize()}RFile")
        } else {
            return getClassPathDirFiles()
        }
    }

    File getLibsDirFile() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.1.0") >= 0) {
            return mProject.file(mProject.buildDir.path + '/intermediates/packaged-classes/' + mVariant.dirName + "/libs")
        } else {
            return mProject.file(mProject.buildDir.path + '/intermediates/bundles/' + mVariant.dirName + "/libs")
        }
    }

    Task getJavaCompileTask() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getJavaCompileProvider().get()
        } else {
            return mVariant.getJavaCompiler()
        }
    }

    Task getProcessManifest() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getOutputs().first().getProcessManifestProvider().get()
        } else {
            return mVariant.getOutputs().first().getProcessManifest()
        }
    }

    Task getMergeAssets() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getMergeAssetsProvider().get()
        } else {
            return mVariant.getMergeAssets()
        }
    }
}