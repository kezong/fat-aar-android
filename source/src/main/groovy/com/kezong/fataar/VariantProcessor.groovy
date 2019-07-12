package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.InvokeManifestMerger
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

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

    private String aarOutputFilePath

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
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
        processRFile(bundleTask)
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(Task prepareTask, Task bundleTask) {
        for (final DefaultResolvedArtifact artifact in mResolvedArtifacts) {
            if (FatLibraryPlugin.ARTIFACT_TYPE_JAR == artifact.type) {
                addJarFile(artifact.file)
            } else if (FatLibraryPlugin.ARTIFACT_TYPE_AAR == artifact.type) {
                AndroidArchiveLibrary archiveLibrary = new AndroidArchiveLibrary(mProject, artifact)
                addAndroidArchiveLibrary(archiveLibrary)
                Set<Task> buildDependencies = artifact.buildDependencies.getDependencies()
                archiveLibrary.getExploadedRootDir().deleteDir()
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
                Task javacTask = getJavaCompileTask()
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
        Task processManifestTask = getProcessManifest()
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
            def dustDir = getClassPathDirFiles().first()
            ExplodedHelper.processIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, dustDir)
        }
        return task
    }

    private Task handleJarMergeTask() {
        final Task task = mProject.tasks.create(name: 'mergeJars'
                + mVariant.name.capitalize())
        task.doFirst {
            ExplodedHelper.processIntoJars(mProject, mAndroidArchiveLibraries, mJarFiles, getLibsDirFile())
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

        Task javacTask = getJavaCompileTask()
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
                Utils.logInfo("Merge resource，Library res：${archiveLibrary.resFolder}")
                mProject.android.sourceSets."main".res.srcDir(archiveLibrary.resFolder)
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
        Task assetsTask = getMergeAssets()
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }

        assetsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                if (archiveLibrary.assetsFolder != null && archiveLibrary.assetsFolder.size() > 0) {
                    // the source set here should be main or variant?
                    mProject.android.sourceSets."main".assets.srcDir(archiveLibrary.assetsFolder)
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
                    // the source set here should be main or variant?
                    mProject.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
                }
            }
        }

        mExplodeTasks.each { it ->
            mergeJniLibsTask.dependsOn it
        }
    }

    /**
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

    def deleteEmptyDir = { file ->
        file.listFiles().each { x ->
            if (x.isDirectory()) {
                if (x.listFiles().size() == 0) {
                    x.delete()
                } else {
                    deleteEmptyDir(x)
                    if (x.listFiles().size() == 0) {
                        x.delete()
                    }
                }
            }
        }
    }

    private def processRFile(Task bundleTask) {
        // R.java dir
        File rFolder = mProject.file("${mProject.getBuildDir()}/intermediates/exploded-aar/r")
        // R.class compile dir
        File rClassFolder = mProject.file("${mProject.getBuildDir()}/intermediates/exploded-aar/r-class")
        // R.jar dir
        final File libFolder = mProject.file("${mProject.getBuildDir()}/outputs/aar-R/${mVariant.dirName}/libs")
        // aar zip file
        File outputDir = libFolder.getParentFile()
        // aar output dir
        File aarDir = mProject.file("${mProject.getBuildDir()}/outputs/aar/")
        aarOutputFilePath = mVariant.outputs.first().outputFile.absolutePath
        def RFileTask = createRFileTask(rFolder)
        def RClassTask = createRClassTask(rFolder, rClassFolder)
        def RJarTask = createRJarTask(rClassFolder, libFolder)
        def reBundleAar = createBundleAarTask(outputDir, aarDir, aarOutputFilePath)

        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            RClassTask.doFirst {
                mProject.copy {
                    from mProject.zipTree(getRClassPath().first().absolutePath + "/R.jar")
                    into getRClassPath().first().absolutePath
                }
            }
        }

        reBundleAar.doFirst {
            mProject.copy {
                from mProject.zipTree(aarOutputFilePath)
                into outputDir
            }
            deleteEmptyDir(outputDir)
        }
        reBundleAar.doLast {
            Utils.logAnytime("target: $aarOutputFilePath")
        }

        bundleTask.doFirst {
            File f = new File(aarOutputFilePath)
            if (f.exists()) {
                f.delete()
            }
            libFolder.getParentFile().deleteDir()
            libFolder.mkdirs()
        }

        bundleTask.doLast {
            // support gradle 5.1 && gradle plugin 3.4 before, the outputName is changed
            File file = new File(aarOutputFilePath)
            if (!file.exists()) {
                aarOutputFilePath = aarDir.absolutePath + "/" + mProject.name + ".aar"
                reBundleAar.archiveName = new File(aarOutputFilePath).name
            }
        }
        bundleTask.finalizedBy(RFileTask)
        RFileTask.finalizedBy(RClassTask)
        RClassTask.finalizedBy(RJarTask)
        RJarTask.finalizedBy(reBundleAar)
    }

    private def createRFile(AndroidArchiveLibrary library, def rFolder) {
        def libPackageName = mVariant.getApplicationId()
        def aarPackageName = library.getPackageName()

        String packagePath = aarPackageName.replace('.', '/')

        def rTxt = library.getSymbolFile()
        def rMap = new ConfigObject()

        if (rTxt.exists()) {
            rTxt.eachLine { line ->
                def (type, subclass, name, value) = line.tokenize(' ')
                rMap[subclass].putAt(name, type)
            }
        }

        def sb = "package $aarPackageName;" << '\n' << '\n'
        sb << 'public final class R {' << '\n'
        rMap.each { subclass, values ->
            sb << "  public static final class $subclass {" << '\n'
            values.each { name, type ->
                sb << "    public static final $type $name = ${libPackageName}.R.${subclass}.${name};" << '\n'
            }

            sb << "    }" << '\n'
        }

        sb << '}' << '\n'

        new File("${rFolder.path}/$packagePath").mkdirs()
        FileOutputStream outputStream = new FileOutputStream("${rFolder.path}/$packagePath/R.java")
        outputStream.write(sb.toString().getBytes())
        outputStream.close()
    }

    private Task createRFileTask(def destFolder) {
        def task = mProject.tasks.create(name: 'createRsFile' + mVariant.name)
        task.doLast {
            mAndroidArchiveLibraries.each {
                Utils.logInfo("Generate R File, Library:${it.name}")
                createRFile(it, destFolder)
            }
        }

        return task
    }

    private Task createRClassTask(def sourceDir, def destinationDir) {
        mProject.mkdir(destinationDir)

        def classpath = getRClassPath()
        String taskName = "compileRs${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, JavaCompile.class, {
            it.source = sourceDir.path
            it.sourceCompatibility = mProject.android.compileOptions.sourceCompatibility
            it.targetCompatibility = mProject.android.compileOptions.targetCompatibility
            it.classpath = classpath
            it.destinationDir destinationDir
        })
        task.doFirst {
            Utils.logInfo("Compile R.class, Dir:${sourceDir.path}")
        }
        return task
    }

    private Task createRJarTask(def fromDir, def desFile) {
        String taskName = "createRsJar${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, Jar.class, {
            it.from fromDir.path
            it.archiveName = "r-classes.jar"
            it.destinationDir desFile
        })
        task.doFirst {
            Utils.logInfo("Generate R.jar, Dir：$fromDir")
        }
        return task
    }

    private Task createBundleAarTask(File from, File destDir, String filePath) {
        String taskName = "reBundleAar${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, Zip.class, {
            it.from from
            it.include "**"
            it.archiveName = new File(filePath).name
            it.destinationDir(destDir)
        })
        return task
    }

    private ConfigurableFileCollection getClassPathDirFiles() {
        ConfigurableFileCollection classpath
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.2.0") >= 0) { // >= Versions 3.2.X
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/" +
                    "javac/${mVariant.name}/compile${mVariant.name.capitalize()}JavaWithJavac/classes")
        } else { // Versions 3.0.x and 3.1.x
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/classes/${mVariant.dirName}")
        }
        return classpath
    }

    private ConfigurableFileCollection getRClassPath() {
        if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mProject.files("${mProject.buildDir.path}/intermediates/" + "compile_only_not_namespaced_r_class_jar/"
                    + "${mVariant.name}/generate${mVariant.name.capitalize()}RFile")
        } else {
            return getClassPathDirFiles()
        }
    }

    private File getLibsDirFile() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.1.0") >= 0) {
            return mProject.file(mProject.buildDir.path + '/intermediates/packaged-classes/' + mVariant.dirName + "/libs")
        } else {
            return mProject.file(mProject.buildDir.path + '/intermediates/bundles/' + mVariant.dirName + "/libs")
        }
    }

    private Task getJavaCompileTask() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getJavaCompileProvider().get()
        } else {
            return mVariant.getJavaCompiler()
        }
    }

    private Task getProcessManifest() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getOutputs().first().getProcessManifestProvider().get()
        } else {
            return mVariant.getOutputs().first().getProcessManifest()
        }
    }

    private Task getMergeAssets() {
        if (Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
            return mVariant.getMergeAssetsProvider().get()
        } else {
            return mVariant.getMergeAssets()
        }
    }
}
