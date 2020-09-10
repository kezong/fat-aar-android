package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider

/**
 * Processor for variant
 * Created by Vigi on 2017/2/24.
 * Modified by kezong on 2019/05/29
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private Set<ResolvedArtifact> mResolvedArtifacts = new HashSet<>()

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<Task> mExplodeTasks = new ArrayList<>()

    private String mGradlePluginVersion

    private VersionAdapter mVersionAdapter

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
        checkGradlePluginVersion()
        mVersionAdapter = new VersionAdapter(project, variant, mGradlePluginVersion)
    }

    private void checkGradlePluginVersion() {
        mProject.rootProject.buildscript.getConfigurations().getByName("classpath").getDependencies().each { Dependency dep ->
            if (dep.group == "com.android.tools.build" && dep.name == "gradle") {
                mGradlePluginVersion = dep.version
            }
        }

        if (mGradlePluginVersion == null) {
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
        }

        if (mGradlePluginVersion == null) {
            throw new IllegalStateException("com.android.tools.build:gradle not found in buildscript classpath")
        }
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
        TaskProvider prepareTask = mProject.tasks.named(taskPath)
        if (prepareTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        TaskProvider bundleTask = FlavorArtifact.getBundleTaskProvider(mProject, mVariant)

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
        processR(bundleTask)
        processDataBinding(bundleTask)
    }

    private void processR(TaskProvider<Task> bundleTask) {
        RProcessor rProcessor = new RProcessor(mProject, mVariant, mAndroidArchiveLibraries, mGradlePluginVersion)
        rProcessor.inject(bundleTask)
    }

    private void processDataBinding(TaskProvider<Task> bundleTask) {
        bundleTask.configure {
            doFirst {
                for (archiveLibrary in mAndroidArchiveLibraries) {
                    if (archiveLibrary.dataBindingFolder != null && archiveLibrary.dataBindingFolder.exists()) {
                        String filePath = "${mProject.getBuildDir()}/outputs/${Constants.RE_BUNDLE_FOLDER}" +
                                "/${mVariant.dirName}/${archiveLibrary.dataBindingFolder.name}"
                        new File(filePath).mkdirs()
                        mProject.copy {
                            from archiveLibrary.dataBindingFolder
                            into filePath
                        }
                    }

                    if (archiveLibrary.dataBindingLogFolder != null && archiveLibrary.dataBindingLogFolder.exists()) {
                        String filePath = "${mProject.getBuildDir()}/outputs/${Constants.RE_BUNDLE_FOLDER}/${mVariant.dirName}" +
                                "/${archiveLibrary.dataBindingLogFolder.name}"
                        new File(filePath).mkdirs()
                        mProject.copy {
                            from archiveLibrary.dataBindingLogFolder
                            into filePath
                        }
                    }
                }
            }
        }
    }

    private void processCache() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.5.0") >= 0) {
            mVersionAdapter.getLibsDirFile().deleteDir()
            mVersionAdapter.getClassPathDirFiles().first().deleteDir()
        }

        File reBundleFile = mProject.file("${mProject.getBuildDir()}/outputs/${Constants.RE_BUNDLE_FOLDER}/${mVariant.dirName}")
        if (reBundleFile != null && reBundleFile.exists()) {
            reBundleFile.delete()
        }
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(TaskProvider<Task> prepareTask, TaskProvider<Task> bundleTask) {
        for (final ResolvedArtifact artifact in mResolvedArtifacts) {
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
                final def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                def group = artifact.getModuleVersion().id.group.capitalize()
                def name = artifact.name.capitalize()
                String taskName = "explode${group}${name}${mVariant.name.capitalize()}"
                Task explodeTask = mProject.tasks.create(taskName, Copy) {
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
                bundleTask.configure {
                    dependsOn(explodeTask)
                }
                mExplodeTasks.add(explodeTask)
            }
        }
    }

    /**
     * merge manifest
     */
    private void processManifest() {
        Task processManifestTask = mVersionAdapter.getProcessManifest()
        String manifestInputDir = "${mProject.getBuildDir()}/intermediates/${Constants.INTERMEDIATES_TEMP_FOLDER}/manifest"
        File manifestOutput
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            manifestOutput = mProject.file("${mProject.buildDir.path}/intermediates/library_manifest/${mVariant.name}/AndroidManifest.xml")
        } else {
            manifestOutput = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + '/AndroidManifest.xml')
        }

        // AGP 4.0.0 brings in a change which wipes out the output files whenever a task gets rerun
        // See https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/tasks/NonIncrementalTask.kt
        // The manifest merging task tries to update the manifest in place, by setting input == output, resulting in the input file being explicitly deleted just before the task gets run
        // The sleight-of-hand below gets things working again
        TaskProvider copyTask = mProject.tasks.register("copy${mVariant.name.capitalize()}Manifest", Copy) {
            dependsOn(processManifestTask)
            outputs.upToDateWhen { false }
            from manifestOutput
            into mProject.file(manifestInputDir)
        }

        List<File> list = new ArrayList<>()
        for (archiveLibrary in mAndroidArchiveLibraries) {
            list.add(archiveLibrary.getManifest())
        }
        TaskProvider<LibraryManifestMerger> manifestsMergeTask = mProject.tasks.register("merge${mVariant.name.capitalize()}Manifest", LibraryManifestMerger) {
            dependsOn(copyTask)
            dependsOn(mExplodeTasks)

            setGradleVersion(mProject.getGradle().getGradleVersion())
            setGradlePluginVersion(mGradlePluginVersion)
            setVariantName(mVariant.name)
            setMainManifestFile(mProject.file("${manifestInputDir}/AndroidManifest.xml"))
            setSecondaryManifestFiles(list)
            setOutputFile(manifestOutput)
            doFirst {
                List<File> existFiles = new ArrayList<>()
                getSecondaryManifestFiles().each {
                    if (it.exists()) {
                        existFiles.add(it)
                    }
                }
                setSecondaryManifestFiles(existFiles)
            }
        }

        processManifestTask.finalizedBy(manifestsMergeTask)
    }


    private TaskProvider handleClassesMergeTask(final boolean isMinifyEnabled) {
        final TaskProvider task = mProject.tasks.register('mergeClasses' + mVariant.name.capitalize()) {
            dependsOn(mExplodeTasks)
            dependsOn(mVersionAdapter.getJavaCompileTask())
            def outputDir = mVersionAdapter.getClassPathDirFiles().first()
            outputs.dir(outputDir)

            doFirst {
                if (isMinifyEnabled) {
                    ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, outputDir)
                    ExplodedHelper.processLibsIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, outputDir)
                } else {
                    ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, outputDir)
                }
            }
        }
        return task
    }

    private TaskProvider handleJarMergeTask(final TaskProvider syncLibTask) {
        final TaskProvider task = mProject.tasks.register('mergeJars' + mVariant.name.capitalize()) {
            dependsOn(mExplodeTasks)
            dependsOn(mVersionAdapter.getJavaCompileTask())
            mustRunAfter(syncLibTask)
            def outputDir = mVersionAdapter.getLibsDirFile()
            outputs.dir(outputDir)
            doFirst {
                ExplodedHelper.processLibsIntoLibs(mProject, mAndroidArchiveLibraries, mJarFiles, outputDir)
            }
        }
        return task
    }

    /**
     * merge classes and jars
     */
    private void processClassesAndJars(TaskProvider<Task> bundleTask) {
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
        TaskProvider syncLibTask = mProject.tasks.named(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        TaskProvider mergeClasses = handleClassesMergeTask(isMinifyEnabled)
        syncLibTask.configure {
            dependsOn(mergeClasses)
        }

        if (!isMinifyEnabled) {
            TaskProvider mergeJars = handleJarMergeTask(syncLibTask)
            bundleTask.configure {
                dependsOn(mergeJars)
            }
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
        TaskProvider resourceGenTask = mProject.tasks.named(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        resourceGenTask.configure {
            dependsOn(mExplodeTasks)

            doFirst {
                for (archiveLibrary in mAndroidArchiveLibraries) {
                    mProject.android.sourceSets.each {
                        if (it.name == mVariant.name) {
                            Utils.logInfo("Merge resource，Library res：${archiveLibrary.resFolder}")
                            it.res.srcDir(archiveLibrary.resFolder)
                        }
                    }
                }
            }
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
        TaskProvider mergeJniLibsTask = mProject.tasks.named(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        mergeJniLibsTask.configure {
            dependsOn(mExplodeTasks)

            doFirst {
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
        }
    }

    /**
     * fixme
     * merge proguard.txt
     */
    private void processProguardTxt(TaskProvider prepareTask) {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'ConsumerProguardFiles'
        TaskProvider mergeFileTask = mProject.tasks.named(taskPath)
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        def proguardFiles = new ArrayList<File>()
        for (archiveLibrary in mAndroidArchiveLibraries) {
            List<File> thirdProguardFiles = archiveLibrary.proguardRules
            for (File file : thirdProguardFiles) {
                if (file.exists()) {
                    Utils.logInfo('add proguard file: ' + file.absolutePath)
                    proguardFiles.add(file)
                }
            }
        }

        mergeFileTask.configure {
            dependsOn(prepareTask)
            inputs.files(proguardFiles)
        }
    }
}
