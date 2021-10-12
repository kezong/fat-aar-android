package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.ManifestProcessorTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.TaskProvider

import java.lang.reflect.Field

class VersionAdapter {

    private Project mProject

    private LibraryVariant mVariant

    VersionAdapter(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
    }

    ConfigurableFileCollection getClassPathDirFiles() {
        ConfigurableFileCollection classpath
        if (FatUtils.compareVersion(AGPVersion, "3.5.0") >= 0) {
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/" +
                    "javac/${mVariant.name}/classes")
        } else if (FatUtils.compareVersion(AGPVersion, "3.2.0") >= 0) { // >= Versions 3.2.X
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/" +
                    "javac/${mVariant.name}/compile${mVariant.name.capitalize()}JavaWithJavac/classes")
        } else { // Versions 3.0.x and 3.1.x
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/classes/${mVariant.dirName}")
        }
        return classpath
    }

    ConfigurableFileCollection getRClassPath() {
        if (FatUtils.compareVersion(AGPVersion, "4.1.0") >= 0) {
            return mProject.files("${mProject.buildDir.path}/intermediates/" + "compile_r_class_jar/"
                    + "${mVariant.name}")
        } else if (FatUtils.compareVersion(AGPVersion, "3.5.0") >= 0) {
            return mProject.files("${mProject.buildDir.path}/intermediates/" + "compile_only_not_namespaced_r_class_jar/"
                    + "${mVariant.name}")
        } else if (FatUtils.compareVersion(AGPVersion, "3.3.0") >= 0) {
            return mProject.files("${mProject.buildDir.path}/intermediates/" + "compile_only_not_namespaced_r_class_jar/"
                    + "${mVariant.name}/generate${mVariant.name.capitalize()}RFile")
        } else {
            return getClassPathDirFiles()
        }
    }

    File getLibsDirFile() {
        if (FatUtils.compareVersion(AGPVersion, '3.6.0') >= 0) {
            return mProject.file("${mProject.buildDir.path}/intermediates/aar_libs_directory/${mVariant.name}/libs")
        } else if (FatUtils.compareVersion(AGPVersion, '3.1.0') >= 0) {
            return mProject.file(mProject.buildDir.path + '/intermediates/packaged-classes/' + mVariant.dirName + "/libs")
        } else {
            return mProject.file(mProject.buildDir.path + '/intermediates/bundles/' + mVariant.name + "/libs")
        }
    }

    Task getJavaCompileTask() {
        if (FatUtils.compareVersion(AGPVersion, "3.3.0") >= 0) {
            return mVariant.getJavaCompileProvider().get()
        } else {
            return mVariant.getJavaCompiler()
        }
    }

    ManifestProcessorTask getProcessManifest() {
        if (FatUtils.compareVersion(AGPVersion, "3.3.0") >= 0) {
            return mVariant.getOutputs().first().getProcessManifestProvider().get()
        } else {
            return mVariant.getOutputs().first().getProcessManifest()
        }
    }

    Task getMergeAssets() {
        if (FatUtils.compareVersion(AGPVersion, "3.3.0") >= 0) {
            return mVariant.getMergeAssetsProvider().get()
        } else {
            return mVariant.getMergeAssets()
        }
    }

    /**
     * return symbol file without remote resources
     * @return symbol file like R.txt
     */
    File getLocalSymbolFile() {
        // > 3.6.0, R.txt contains remote resources, so we use R-def.txt
        if (FatUtils.compareVersion(AGPVersion, "3.6.0") >= 0) {
            return mProject.file(mProject.buildDir.path + '/intermediates/local_only_symbol_list/' + mVariant.name + "/R-def.txt")
        } else if (FatUtils.compareVersion(AGPVersion, "3.1.0") >= 0) {
            return mProject.file(mProject.buildDir.path + '/intermediates/symbols/' + mVariant.dirName + "/R.txt")
        } else {
            return mProject.file(mProject.buildDir.path + '/intermediates/bundles/' + mVariant.name + "/R.txt")
        }
    }

    String getSyncLibJarsTaskPath() {
        if (FatUtils.compareVersion(AGPVersion, '3.6.0') >= 0) {
            return "sync${mVariant.name.capitalize()}LibJars"
        } else {
            return "transformClassesAndResourcesWithSyncLibJarsFor${mVariant.name.capitalize()}"
        }
    }

    File getOutputFile() {
        return outputFile(mProject, mVariant, AGPVersion)
    }

    static File getOutputFile(Project project, LibraryVariant variant) {
        if (FatUtils.compareVersion(AGPVersion, "3.3.0") >= 0) {
            String fileName = variant.outputs.first().outputFileName
            if (FatUtils.compareVersion(project.gradle.gradleVersion, "5.1") >= 0) {
                return new File(variant.getPackageLibraryProvider().get().getDestinationDirectory().getAsFile().get(), fileName)
            } else {
                return new File(variant.getPackageLibraryProvider().get().getDestinationDir(), fileName)
            }
        } else {
            return variant.outputs.first().outputFile
        }
    }

    static TaskProvider<Task> getBundleTaskProvider(Project project, String variantName) throws UnknownTaskException {
        def taskPath = "bundle" + variantName.capitalize()
        TaskProvider bundleTask
        try {
            bundleTask = project.tasks.named(taskPath)
        } catch (UnknownTaskException ignored) {
            taskPath += "Aar"
            bundleTask = project.tasks.named(taskPath)
        }
        return bundleTask
    }

    static String getAGPVersion() {
        // AGP 3.6+
        try {
            Class aClass = Class.forName("com.android.Version")
            Field version = aClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            return version.get(aClass)
        } catch (Throwable ignore) {
            Class aClass = Class.forName("com.android.builder.model.Version")
            Field version = aClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            return version.get(aClass)
        }
    }
}
