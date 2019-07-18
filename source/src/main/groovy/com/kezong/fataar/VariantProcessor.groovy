package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.InvokeManifestMerger
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.Copy

/**
 * Processor for variant
 * Created by Vigi on 2017/2/24.
 * Modified by kezong on 2019/05/29
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private final FatAarExtension mExtension

    private Set<ResolvedArtifact> mResolvedArtifacts = new ArrayList<>()

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<Task> mExplodeTasks = new ArrayList<>()

    private String mGradlePluginVersion

    private VersionAdapter mVersionAdapter

    VariantProcessor(Project project, FatAarExtension extension, LibraryVariant variant) {
        mProject = project
        mExtension = extension
        mVariant = variant
        // gradle version
        mProject.parent.buildscript.getConfigurations().getByName("classpath").getDependencies().each { Dependency dep ->
            if (dep.group == "com.android.tools.build" && dep.name == "gradle") {
                mGradlePluginVersion = dep.version
            }
        }
        if (mGradlePluginVersion == null) {
            throw new IllegalStateException("com.android.tools.build:gradle is no set in the root build.gradle file")
        }
        mVersionAdapter = new VersionAdapter(project, variant, mGradlePluginVersion)
    }

    void addArtifacts(Set<ResolvedArtifact> resolvedArtifacts) {
        mResolvedArtifacts.addAll(resolvedArtifacts)
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        mAndroidArchiveLibraries.add(library)
    }

    void addUnResolveArtifact(Set<ResolvedDependency> dependencies) {
        if (dependencies != null) {
            dependencies.each {
                def artifact = FlavorArtifact.createFlavorArtifact(mProject, mVariant, it, mGradlePluginVersion)
                mResolvedArtifacts.add(artifact)
            }
        }
    }

    void addJarFile(File jar) {
        mJarFiles.add(jar)
    }

    void processVariant() {
        String taskPath = 'pre' + mVariant.name.capitalize() + 'Build'
        Task prepareTask = mProject.tasks.findByPath(taskPath)
        if (prepareTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        taskPath = 'bundle' + mVariant.name.capitalize()
        Task bundleTask = mProject.tasks.findByPath(taskPath)
        if (bundleTask == null) {
            taskPath = 'bundle' + mVariant.name.capitalize() + "Aar"
            bundleTask = mProject.tasks.findByPath(taskPath)
        }
        if (bundleTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        processArtifacts(prepareTask, bundleTask)
        processClassesAndJars(bundleTask)
        if (mAndroidArchiveLibraries.isEmpty()) {
            return
        }
        processManifest()
        processResourcesAndR()
        processAssets()
        processJniLibs()
        processProguardTxt(prepareTask)
        RProcessor rProcessor = new RProcessor(mProject, mExtension, mVariant, mAndroidArchiveLibraries, mGradlePluginVersion)
        rProcessor.inject(bundleTask)
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(Task prepareTask, Task bundleTask) {
        for (final DefaultResolvedArtifact artifact in mResolvedArtifacts) {
            if (FatLibraryPlugin.ARTIFACT_TYPE_JAR == artifact.type) {
                addJarFile(artifact.file)
            } else if (FatLibraryPlugin.ARTIFACT_TYPE_AAR == artifact.type) {
                AndroidArchiveLibrary archiveLibrary = new AndroidArchiveLibrary(mProject, artifact, mVariant.name)
                addAndroidArchiveLibrary(archiveLibrary)
                Set<Task> buildDependencies = artifact.buildDependencies.getDependencies()
                archiveLibrary.getRootFolder().deleteDir()
                final def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                String taskName = "explode${artifact.name.capitalize()}${mVariant.name.capitalize()}"
                Task explodeTask = mProject.tasks.create(name: taskName, type: Copy) {
                    from mProject.zipTree(artifact.file.absolutePath)
                    into zipFolder
                }
                if (buildDependencies.size() == 0) {
                    explodeTask.dependsOn(prepareTask)
                } else {
                    explodeTask.dependsOn(buildDependencies.first())
                }
                Task javacTask = mVersionAdapter.getJavaCompileTask()
                javacTask.dependsOn(explodeTask)
                bundleTask.dependsOn(explodeTask)
                mExplodeTasks.add(explodeTask)
            }
        }
    }

    /**
     * merge manifest
     */
    private void processManifest() {
        Task processManifestTask = mVersionAdapter.getProcessManifest()
        File manifestOutputBackup
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            manifestOutputBackup = mProject.file("${mProject.buildDir.path}/intermediates/library_manifest/${mVariant.name}/AndroidManifest.xml")
        } else {
            manifestOutputBackup = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + '/AndroidManifest.xml')
        }
        InvokeManifestMerger manifestsMergeTask = mProject.tasks.create("merge${mVariant.name.capitalize()}Manifest", LibraryManifestMerger.class)
        manifestsMergeTask.setGradleVersion(mProject.getGradle().getGradleVersion())
        manifestsMergeTask.setGradlePluginVersion(mGradlePluginVersion)
        manifestsMergeTask.setVariantName(mVariant.name)
        manifestsMergeTask.setMainManifestFile(manifestOutputBackup)
        List<File> list = new ArrayList<>()
        for (archiveLibrary in mAndroidArchiveLibraries) {
            list.add(archiveLibrary.getManifest())
        }
        manifestsMergeTask.setSecondaryManifestFiles(list)
        manifestsMergeTask.setOutputFile(manifestOutputBackup)
        manifestsMergeTask.dependsOn processManifestTask
        manifestsMergeTask.doFirst {
            List<File> existFiles = new ArrayList<>()
            manifestsMergeTask.getSecondaryManifestFiles().each {
                if (it.exists()) {
                    existFiles.add(it)
                }
            }
            manifestsMergeTask.setSecondaryManifestFiles(existFiles)
        }

        mExplodeTasks.each { it ->
            manifestsMergeTask.dependsOn it
        }

        processManifestTask.finalizedBy manifestsMergeTask
    }

    private Task handleClassesMergeTask() {
        final Task task = mProject.tasks.create(name: 'mergeClasses'
                + mVariant.name.capitalize())
        task.doFirst {
            def dustDir = mVersionAdapter.getClassPathDirFiles().first()
            ExplodedHelper.processIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, dustDir)
        }
        return task
    }

    private Task handleJarMergeTask() {
        final Task task = mProject.tasks.create(name: 'mergeJars'
                + mVariant.name.capitalize())
        task.doFirst {
            ExplodedHelper.processIntoJars(mProject, mAndroidArchiveLibraries, mJarFiles, mVersionAdapter.getLibsDirFile())
        }
        return task
    }

    /**
     * merge classes and jars
     */
    private void processClassesAndJars(Task bundleTask) {
        if (mVariant.getBuildType().isMinifyEnabled()) {
            //merge proguard file
            for (archiveLibrary in mAndroidArchiveLibraries) {
                List<File> thirdProguardFiles = archiveLibrary.proguardRules
                for (File file : thirdProguardFiles) {
                    if (file.exists()) {
                        Utils.logInfo('add proguard file: ' + file.absolutePath)
                        mProject.android.getDefaultConfig().proguardFile(file)
                    }
                }
            }
        }

        String taskPath = 'transformClassesAndResourcesWithSyncLibJarsFor' + mVariant.name.capitalize()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        Task mergeClasses = handleClassesMergeTask()
        syncLibTask.dependsOn(mergeClasses)
        mExplodeTasks.each { it ->
            mergeClasses.dependsOn it
        }

        Task mergeJars = handleJarMergeTask()
        mergeJars.shouldRunAfter(syncLibTask)
        bundleTask.dependsOn(mergeJars)
        mExplodeTasks.each { it ->
            mergeJars.dependsOn it
        }

        Task javacTask = mVersionAdapter.getJavaCompileTask()
        mergeClasses.dependsOn(javacTask)
        mergeJars.dependsOn(javacTask)
    }

    /**
     * merge R.txt(actually is to fix issue caused by provided configuration) and res
     *
     * Here I have to inject res into "main" instead of "variant.name".
     * To avoid the res from embed dependencies being used, once they have the same res Id with main res.
     *
     * Now the same res Id will cause a build exception: Duplicate resources, to encourage you to change res Id.
     * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
     */
    private void processResourcesAndR() {
        String taskPath = 'generate' + mVariant.name.capitalize() + 'Resources'
        Task resourceGenTask = mProject.tasks.findByPath(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        resourceGenTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                mProject.android.sourceSets.each {
                    if (it.name == mVariant.name) {
                        Utils.logInfo("Merge resource，Library res：${archiveLibrary.resFolder}")
                        it.res.srcDir(archiveLibrary.resFolder)
                    }
                }
            }
        }

        mExplodeTasks.each { it ->
            resourceGenTask.dependsOn(it)
        }
    }

    /**
     * merge assets
     *
     * AaptOptions.setIgnoreAssets and AaptOptions.setIgnoreAssetsPattern will work as normal
     */
    private void processAssets() {
        Task assetsTask = mVersionAdapter.getMergeAssets()
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }

        assetsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                if (archiveLibrary.assetsFolder != null && archiveLibrary.assetsFolder.size() > 0) {
                    mProject.android.sourceSets.each {
                        if (it.name == mVariant.name) {
                            it.assets.srcDir(archiveLibrary.assetsFolder)
                        }
                    }
                }
            }
        }

        mExplodeTasks.each { it ->
            assetsTask.dependsOn it
        }
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'JniLibFolders'
        Task mergeJniLibsTask = mProject.tasks.findByPath(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        mergeJniLibsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                if (archiveLibrary.jniFolder != null && archiveLibrary.jniFolder.size() > 0) {
                    mProject.android.sourceSets.each {
                        if (it.name == mVariant.name) {
                            it.jniLibs.srcDir(archiveLibrary.jniFolder)
                        }
                    }
                }
            }
        }

        mExplodeTasks.each { it ->
            mergeJniLibsTask.dependsOn it
        }
    }

    /**
     * fixme
     * merge proguard.txt
     */
    private void processProguardTxt(Task prepareTask) {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'ConsumerProguardFiles'
        Task mergeFileTask = mProject.tasks.findByPath(taskPath)
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        for (archiveLibrary in mAndroidArchiveLibraries) {
            List<File> thirdProguardFiles = archiveLibrary.proguardRules
            for (File file : thirdProguardFiles) {
                if (file.exists()) {
                    Utils.logInfo('add proguard file: ' + file.absolutePath)
                    mergeFileTask.getInputs().file(file)
                }
            }
        }
        mergeFileTask.doFirst {
            def proguardFiles = mergeFileTask.getInputFiles()
            for (archiveLibrary in mAndroidArchiveLibraries) {
                List<File> thirdProguardFiles = archiveLibrary.proguardRules
                for (File file : thirdProguardFiles) {
                    if (file.exists()) {
                        Utils.logInfo('add proguard file: ' + file.absolutePath)
                        proguardFiles.add(file)
                    }
                }
            }
        }
        mergeFileTask.dependsOn prepareTask
    }
}
