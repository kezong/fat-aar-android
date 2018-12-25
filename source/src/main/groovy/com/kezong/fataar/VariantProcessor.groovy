package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.InvokeManifestMerger
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * Processor for variant
 * Created by Vigi on 2017/2/24.
 * Modified by kezong on 2018/12/18
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private Set<ResolvedArtifact> mResolvedArtifacts = new ArrayList<>()

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<Task> mExplodeTasks = new ArrayList<>()

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
    }

    void addArtifacts(Set<ResolvedArtifact> resolvedArtifacts) {
        mResolvedArtifacts.addAll(resolvedArtifacts)
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        mAndroidArchiveLibraries.add(library)
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
        processArtifacts(bundleTask)
        processClassesAndJars()

        if (mAndroidArchiveLibraries.isEmpty()) {
            return
        }
        processManifest()
        processResourcesAndR()
        processAssets()
        processJniLibs()
        processProguardTxt(prepareTask)
        processRFile()
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(Task bundleTask) {
        for (DefaultResolvedArtifact artifact in mResolvedArtifacts) {
            if (FatLibraryPlugin.ARTIFACT_TYPE_JAR == artifact.type) {
                addJarFile(artifact.file)
            } else if (FatLibraryPlugin.ARTIFACT_TYPE_AAR == artifact.type) {
                AndroidArchiveLibrary archiveLibrary = new AndroidArchiveLibrary(mProject, artifact)
                addAndroidArchiveLibrary(archiveLibrary)
                Set<Task> buildDependencies = artifact.getBuildDependencies().getDependencies()
                archiveLibrary.getExploadedRootDir().deleteDir()
                def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                if (buildDependencies.size() == 0) {
                    mProject.copy {
                        LogUtil.logInfo("explode $artifact.name")
                        from mProject.zipTree(artifact.file.absolutePath)
                        into zipFolder
                    }
                } else {
                    String taskName = "explode${artifact.name.capitalize()}${mVariant.name.capitalize()}"
                    Task explodeTask = mProject.tasks.create(name: taskName, type: Copy) {
                        from mProject.zipTree(artifact.file.absolutePath)
                        into zipFolder
                    }
                    final String artifactName = artifact.name
                    explodeTask.doFirst {
                        LogUtil.logInfo("explode $artifactName")
                    }
                    explodeTask.dependsOn(buildDependencies.first())
                    explodeTask.shouldRunAfter(buildDependencies.first())
                    Task javacTask = mVariant.getJavaCompiler()
                    javacTask.dependsOn(explodeTask)
                    bundleTask.dependsOn(explodeTask)
                    mExplodeTasks.add(explodeTask)
                }
            }
        }
    }

    /**
     * merge manifest
     *
     * TODO process each variant.getOutputs()
     * TODO "InvokeManifestMerger" deserve more android plugin version check
     * TODO add setMergeReportFile
     * TODO a better temp manifest file location
     */
    private void processManifest() {
        Class invokeManifestTaskClazz = null
        String className = 'com.android.build.gradle.tasks.InvokeManifestMerger'
        try {
            invokeManifestTaskClazz = Class.forName(className)
        } catch (ClassNotFoundException ignored) {
        }
        if (invokeManifestTaskClazz == null) {
            throw new RuntimeException("Can not find class ${className}!")
        }
        Task processManifestTask = mVariant.getOutputs().first().getProcessManifest()
        File manifestOutputBackup = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + '/AndroidManifest.xml')

        InvokeManifestMerger manifestsMergeTask = mProject.tasks.create('merge' + mVariant.name.capitalize() + 'Manifest', invokeManifestTaskClazz)
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

    /**
     * merge classes and jars
     */
    private void processClassesAndJars() {
        if (mVariant.getBuildType().isMinifyEnabled()) {
            //merge proguard file
            for (archiveLibrary in mAndroidArchiveLibraries) {
                List<File> thirdProguardFiles = archiveLibrary.proguardRules
                for (File file : thirdProguardFiles) {
                    if (file.exists()) {
                        LogUtil.logInfo('add proguard file: ' + file.absolutePath)
                        mProject.android.getDefaultConfig().proguardFile(file)
                    }
                }
            }
        }

        // compile to a unbroken classes.jar
        Task javacTask = mVariant.getJavaCompiler()
        if (javacTask == null) {
            throw new RuntimeException("Can not find java compiler task")
        }
        javacTask.doLast {
            ExplodedHelper.processIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, getClassPathDirFiles().first())
        }

        // assemble task
        String taskPath = 'transformClassesAndResourcesWithSyncLibJarsFor' + mVariant.name.capitalize()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        syncLibTask.doLast {
            ExplodedHelper.processIntoJars(mProject, mAndroidArchiveLibraries, mJarFiles, getLibsDirFile())
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
                LogUtil.logInfo("Merge resource，Library res：${archiveLibrary.resFolder}")
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
        Task assetsTask = mVariant.getMergeAssets()
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }
        for (archiveLibrary in mAndroidArchiveLibraries) {
            assetsTask.getInputs().dir(archiveLibrary.assetsFolder)
        }
        assetsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                mProject.android.sourceSets."main".assets.srcDir(archiveLibrary.assetsFolder)
            }
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
        for (archiveLibrary in mAndroidArchiveLibraries) {
            mergeJniLibsTask.getInputs().dir(archiveLibrary.jniFolder)
        }
        mergeJniLibsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                mProject.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
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
                    LogUtil.logInfo('add proguard file: ' + file.absolutePath)
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
                        LogUtil.logInfo('add proguard file: ' + file.absolutePath)
                        proguardFiles.add(file)
                    }
                }
            }
        }
        mergeFileTask.dependsOn prepareTask
    }

    private def processRFile() {
        // R.java dir
        File rFolder = mProject.file("${mProject.getBuildDir()}/intermediates/exploded-aar/r")
        // R.class compile dir
        File rClassFolder = mProject.file("${mProject.getBuildDir()}/intermediates/exploded-aar/r-class")
        // R.jar dir
        File libFolder = mProject.file("${mProject.getBuildDir()}/outputs/aar-R/${mVariant.dirName}/libs")
        libFolder.getParentFile().delete()
        libFolder.mkdirs()
        // aar output file
        File outputFile
        // aar zip file
        File outputDir = libFolder.getParentFile()
        // aar output dir
        File aarDir = mProject.file("${mProject.getBuildDir()}/outputs/aar/")
        mVariant.outputs.all { output ->
            outputFile = output.outputFile
        }

        def RFileTask = createRFileTask(rFolder)
        def RClassTask = createRClassTask(rFolder, rClassFolder)
        def RJarTask = createRJarTask(rClassFolder, libFolder)
        def bundleAar = createBundleAarTask(outputDir, aarDir, outputFile)
        bundleAar.doFirst {
            LogUtil.logInfo("Assemble final aar, from:$outputDir")
            mProject.copy {
                from mProject.zipTree(outputFile.absolutePath)
                into outputDir
            }
        }
        bundleAar.doLast {
            LogUtil.logInfo("Assemble final aar, target:$outputFile")
        }

        Task assembleTask = mProject.tasks.findByPath("assemble${mVariant.name.capitalize()}")
        assembleTask.finalizedBy(RFileTask)
        RFileTask.finalizedBy(RClassTask)
        RClassTask.finalizedBy(RJarTask)
        RJarTask.finalizedBy(bundleAar)
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
                sb << "    public static $type $name = ${libPackageName}.R.${subclass}.${name};" << '\n'
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
                LogUtil.logInfo("Generate R File, Library:${it.name}")
                createRFile(it, destFolder)
            }
        }

        return task
    }

    private Task createRClassTask(def sourceDir, def destinationDir) {
        mProject.mkdir(destinationDir)

        def classpath = getClassPathDirFiles()
        String taskName = "compileRs${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, JavaCompile.class, {
            it.source = sourceDir.path
            it.sourceCompatibility = '1.8'
            it.targetCompatibility = '1.8'
            it.classpath = classpath
            it.destinationDir destinationDir
        })
        task.doFirst {
            LogUtil.logInfo("Compile R.class, Dir:${sourceDir.path}")
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
            LogUtil.logInfo("Generate R.jar, Dir：$fromDir")
        }
        return task
    }

    private Task createBundleAarTask(File from, File destDir, File outputFile) {
        String taskName = "assembleFinalAar${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, Zip.class, {
            it.from from
            it.include "**"
            it.archiveName = outputFile.name
            it.destinationDir(destDir)
        })
        return task
    }

    private ConfigurableFileCollection getClassPathDirFiles() {
        def gradleVersion
        mProject.parent.buildscript.getConfigurations().getByName("classpath").getDependencies().each { Dependency dep ->
            if (dep.name == "gradle") {
                gradleVersion = dep.version
            }
        }

        ConfigurableFileCollection classpath
        if (gradleVersion != null && gradleVersion.contains("3.2")) { // Versions 3.2.x
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/" +
                    "javac/${mVariant.name}/compile${mVariant.name.capitalize()}JavaWithJavac/classes")
        } else { // Versions 3.0.x and 3.1.x
            classpath = mProject.files("${mProject.buildDir.path}/intermediates/classes/${mVariant.dirName}")
        }
        return classpath
    }

    private File getLibsDirFile() {
        return mProject.file(mProject.buildDir.path + '/intermediates/packaged-classes/' + mVariant.dirName + "/libs")
    }
}

