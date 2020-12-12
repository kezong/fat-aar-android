package com.kezong.fataar;

import com.android.build.gradle.api.LibraryVariant;

import org.gradle.api.Project;

/**
 * Temp directory used by fat-aar
 */
class DirectoryManager {

    private static final String RE_BUNDLE_FOLDER = "aar_rebundle";

    private static final String INTERMEDIATES_TEMP_FOLDER = "fat-aar";

    private static Project sProject;

    static void attach(Project project) {
        sProject = project;
    }

    static File getReBundleDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/outputs/${RE_BUNDLE_FOLDER}/${variant.name}")
    }

    static File getRJavaDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/r/${variant.name}")
    }

    static File getRClassDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/r-class/${variant.name}")
    }

    static File getRJarDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/outputs/${RE_BUNDLE_FOLDER}/${variant.name}/libs")
    }

    static File getMergeClassDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/merge_classes/${variant.name}")
    }

    static File getKotlinMetaDirectory(LibraryVariant variant) {
        return sProject.file("${sProject.getBuildDir()}/tmp/kotlin-classes/${variant.name}/META-INF")
    }
}
