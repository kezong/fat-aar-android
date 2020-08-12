package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.InvokeManifestMerger
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskDependency

/**
 * Processor for variant
 * Created by Vigi on 2017/2/24.
 * Modified by kezong on 2019/05/29
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private Set<ResolvedArtifact> mResolvedArtifacts = new ArrayList<>()

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<Task> mExplodeTasks = new ArrayList<>()

    private String mGradlePluginVersion

    private VersionAdapter mVersionAdapter

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
        // gradle version
        def classpathBuildscriptConfiguration = mProject.rootProject.buildscript.getConfigurations().getByName("classpath")

        def artifacts = classpathBuildscriptConfiguration.getResolvedConfiguration().getResolvedArtifacts()
        artifacts.find {
            def artifactId = it.getModuleVersion().getId()
            if (artifactId.getGroup() == "com.android.tools.build" && artifactId.getName() == "gradle") {
                mGradlePluginVersion = artifactId.getVersion()
                return true
            }
            return false
        }
        if (mGradlePluginVersion == null) {
            throw new IllegalStateException("com.android.tools.build:gradle not found in buildscript classpath")
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
        processCache()
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
        RProcessor rProcessor = new RProcessor(mProject, mVariant, mAndroidArchiveLibraries, mGradlePluginVersion)
        rProcessor.inject(bundleTask)
    }

    private void processCache() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.5.0") >= 0) {
            mVersionAdapter.getLibsDirFile().deleteDir()
            mVersionAdapter.getClassPathDirFiles().first().deleteDir()
        }
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
                Set<Task> dependencies
                if (artifact.buildDependencies instanceof TaskDependency) {
                    dependencies = artifact.buildDependencies.getDependencies()
                } else {
                    CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext()
                    artifact.buildDependencies.visitDependencies(context)
                    if (context.queue.size() == 0) {
                        dependencies = new HashSet<>()
                    } else {
                        dependencies = context.queue.getFirst().getDependencies()
                    }
                }
                archiveLibrary.getRootFolder().deleteDir()
                final def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                def group = artifact.getModuleVersion().id.group.capitalize()
                def name = artifact.name.capitalize()
                String taskName = "explode${group}${name}${mVariant.name.capitalize()}"
                Task explodeTask = mProject.tasks.create(name: taskName, type: Copy) {
                    from mProject.zipTree(artifact.file.absolutePath)
                    into zipFolder
                }

                if (dependencies.size() == 0) {
                    explodeTask.dependsOn(prepareTask)
                } else {
                    explodeTask.dependsOn(dependencies.first())
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
        String manifestInputDir = "${mProject.getBuildDir()}/intermediates/fat-R/manifest"
        File manifestOutput
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            manifestOutput = mProject.file("${mProject.buildDir.path}/intermediates/library_manifest/${mVariant.name}/AndroidManifest.xml")
        } else {
            manifestOutput = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + '/AndroidManifest.xml')
        }
        InvokeManifestMerger manifestsMergeTask = mProject.tasks.create("merge${mVariant.name.capitalize()}Manifest", LibraryManifestMerger.class)
        manifestsMergeTask.setGradleVersion(mProject.getGradle().getGradleVersion())
        manifestsMergeTask.setGradlePluginVersion(mGradlePluginVersion)
        manifestsMergeTask.setVariantName(mVariant.name)
        manifestsMergeTask.setMainManifestFile(mProject.file("${manifestInputDir}/AndroidManifest.xml"))
        List<File> list = new ArrayList<>()
        for (archiveLibrary in mAndroidArchiveLibraries) {
            list.add(archiveLibrary.getManifest())
        }
        manifestsMergeTask.setSecondaryManifestFiles(list)
        manifestsMergeTask.setOutputFile(manifestOutput)
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

        // AGP 4.0.0 brings in a change which wipes out the output files whenever a task gets rerun
        // See https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/tasks/NonIncrementalTask.kt
        // The manifest merging task tries to update the manifest in place, by setting input == output, resulting in the input file being explicitly deleted just before the task gets run
        // The sleight-of-hand below gets things working again
        Task copyTask = mProject.tasks.create(name: "copy${mVariant.name.capitalize()}Manifest", type: Copy) {
            from manifestOutput
            into mProject.file(manifestInputDir)
        }
        copyTask.dependsOn processManifestTask
        manifestsMergeTask.dependsOn copyTask
        processManifestTask.finalizedBy manifestsMergeTask
    }

    private Task handleClassesMergeTask(final boolean isMinifyEnabled) {
        final Task task = mProject.tasks.create(name: 'mergeClasses'
                + mVariant.name.capitalize())
        task.doFirst {
            def dustDir = mVersionAdapter.getClassPathDirFiles().first()
            if (isMinifyEnabled) {
                ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, dustDir)
                ExplodedHelper.processLibsIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, dustDir)
            } else {
                ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, dustDir)
            }
        }
        return task
    }

    private Task handleJarMergeTask() {
        final Task task = mProject.tasks.create(name: 'mergeJars'
                + mVariant.name.capitalize())
        task.doFirst {
            ExplodedHelper.processLibsIntoLibs(mProject, mAndroidArchiveLibraries, mJarFiles, mVersionAdapter.getLibsDirFile())
        }
        return task
    }

    /**
     * merge classes and jars
     */
    private void processClassesAndJars(Task bundleTask) {
        boolean isMinifyEnabled = mVariant.getBuildType().isMinifyEnabled()
        if (isMinifyEnabled) {
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

        String taskPath = mVersionAdapter.getSyncLibJarsTaskPath()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        Task javacTask = mVersionAdapter.getJavaCompileTask()
        Task mergeClasses = handleClassesMergeTask(isMinifyEnabled)
        syncLibTask.dependsOn(mergeClasses)
        mExplodeTasks.each { it ->
            mergeClasses.dependsOn it
        }
        mergeClasses.dependsOn(javacTask)

        if (!isMinifyEnabled) {
            Task mergeJars = handleJarMergeTask()
            mergeJars.mustRunAfter(syncLibTask)
            bundleTask.dependsOn(mergeJars)
            mExplodeTasks.each { it ->
                mergeJars.dependsOn it
            }
            mergeJars.dependsOn(javacTask)
        }
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
                if (archiveLibrary.assetsFolder != null && archiveLibrary.assetsFolder.exists()) {
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
                if (archiveLibrary.jniFolder != null && archiveLibrary.jniFolder.exists()) {
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
