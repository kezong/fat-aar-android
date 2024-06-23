package com.kezong.fataar;

import com.android.build.gradle.api.LibraryVariant;

import org.gradle.api.Project;

/**
 * Temp directory used by fat-aar
 */
class DirectoryManager {

    private static final String RE_BUNDLE_FOLDER = "aar_rebundle";

    private static final String INTERMEDIATES_TEMP_FOLDER = "fat-aar";

    static File getReBundleDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/outputs/${RE_BUNDLE_FOLDER}/${variant.name}")
    }

    static File getRJavaDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/r/${variant.name}")
    }

    static File getRClassDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/r-class/${variant.name}")
    }

    static File getRJarDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/outputs/${RE_BUNDLE_FOLDER}/${variant.name}/libs")
    }

    static File getMergeClassDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/intermediates/${INTERMEDIATES_TEMP_FOLDER}/merge_classes/${variant.name}")
    }

    static File getKotlinMetaDirectory(Project project, LibraryVariant variant) {
        return project.file("${project.getBuildDir()}/tmp/kotlin-classes/${variant.name}/META-INF")
    }
}
