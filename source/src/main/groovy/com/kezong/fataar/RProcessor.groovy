package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar


/**
 * R file processor
 * @author kezong on 2019/7/16.
 */
class RProcessor {

    private final Project mProject
    private final LibraryVariant mVariant

    private final File mJavaDir
    private final File mClassDir
    private final File mJarDir
    private final File mAarUnZipDir
    private final File mAarOutputDir
    private final String mGradlePluginVersion
    private String mAarOutputPath
    private VersionAdapter mVersionAdapter
    private final Collection<AndroidArchiveLibrary> mLibraries

    RProcessor(Project project, LibraryVariant variant, Collection<AndroidArchiveLibrary> libraries, String version) {
        mProject = project
        mVariant = variant
        mLibraries = libraries
        mGradlePluginVersion = version
        mVersionAdapter = new VersionAdapter(project, variant, version)
        // R.java dir
        mJavaDir = mProject.file("${mProject.getBuildDir()}/intermediates/fat-R/r/${mVariant.dirName}")
        // R.class compile dir
        mClassDir = mProject.file("${mProject.getBuildDir()}/intermediates/fat-R/r-class/${mVariant.dirName}")
        // R.jar dir
        mJarDir = mProject.file("${mProject.getBuildDir()}/outputs/aar-R/${mVariant.dirName}/libs")
        // aar zip file
        mAarUnZipDir = mJarDir.getParentFile()
        // aar output dir
        mAarOutputDir = mProject.file("${mProject.getBuildDir()}/outputs/aar/")

        mAarOutputPath = mVersionAdapter.getOutputPath()
    }

    void inject(Task bundleTask) {
        def RFileTask = createRFileTask(mJavaDir)
        def RClassTask = createRClassTask(mJavaDir, mClassDir)
        def RJarTask = createRJarTask(mClassDir, mJarDir)
        def reBundleAar = createBundleAarTask(mAarUnZipDir, mAarOutputDir, mAarOutputPath)

        reBundleAar.doFirst {
            mProject.copy {
                from mProject.zipTree(mAarOutputPath)
                into mAarUnZipDir
            }
            deleteEmptyDir(mAarUnZipDir)
        }

        reBundleAar.doLast {
            Utils.logAnytime("target: $mAarOutputPath")
        }

        bundleTask.doFirst {
            File f = new File(mAarOutputPath)
            if (f.exists()) {
                f.delete()
            }
            mJarDir.getParentFile().deleteDir()
            mJarDir.mkdirs()
        }

        bundleTask.doLast {
            // support gradle 5.1 && gradle plugin 3.4 before, the outputName is changed
            File file = new File(mAarOutputPath)
            if (!file.exists()) {
                mAarOutputPath = mAarOutputDir.absolutePath + "/" + mProject.name + ".aar"
                if (Utils.compareVersion(mProject.gradle.gradleVersion, "6.0.1") >= 0) {
                    reBundleAar.getArchiveFileName().set(new File(mAarOutputPath).name)
                } else {
                    reBundleAar.archiveName = new File(mAarOutputPath).name
                }
            }
        }

        bundleTask.finalizedBy(RFileTask)
        RFileTask.finalizedBy(RClassTask)
        RClassTask.finalizedBy(RJarTask)
        RJarTask.finalizedBy(reBundleAar)
    }

    private def createRFile(AndroidArchiveLibrary library, def rFolder, ConfigObject symbolsMap) {
        def libPackageName = mVariant.getApplicationId()
        def aarPackageName = library.getPackageName()

        String packagePath = aarPackageName.replace('.', '/')

        def rTxt = library.getSymbolFile()
        def rMap = new ConfigObject()

        if (rTxt.exists()) {
            rTxt.eachLine { line ->
                def (type, subclass, name, value) = line.tokenize(' ')
                if (symbolsMap.containsKey(subclass) && symbolsMap.get(subclass).containsKey(name)) {
                    rMap[subclass].putAt(name, type)
                }
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

    private def getSymbolsMap() {
        def file = mVersionAdapter.getLocalSymbolFile()
        if (!file.exists()) {
            throw IllegalAccessException("{$file.absolutePath} not found")
        }

        def map = new ConfigObject()

        if (file.name == "R-def.txt") {
            // R-def.txt is a local symbol file that format is different of R.txt
            file.eachLine { line ->
                List splits = line.tokenize(' ')
                if (splits == null || splits.size() < 2) {
                    return
                }
                def subclass = splits.get(0)
                def name = splits.get(1)
                map[subclass].putAt(name, 1)
                if (subclass == "styleable" && splits.size() > 2) {
                    for (int i = 2; i < splits.size(); ++i) {
                        String subStyle = splits.get(i).replace(':', "_")
                        map[subclass].putAt("${name}_$subStyle", 1)
                    }
                }
            }
        } else {
            file.eachLine { line ->
                def (type, subclass, name, value) = line.tokenize(' ')
                map[subclass].putAt(name, type)
            }
        }
        return map
    }

    private Task createRFileTask(final File destFolder) {
        def task = mProject.tasks.create(name: 'createRsFile' + mVariant.name)
        task.doLast {
            if (destFolder.exists()) {
                destFolder.deleteDir()
            }
            if (mLibraries != null && mLibraries.size() > 0) {
                def symbolsMap = getSymbolsMap()
                mLibraries.each {
                    Utils.logInfo("Generate R File, Library:${it.name}")
                    createRFile(it, destFolder, symbolsMap)
                }
            }
        }

        return task
    }

    private Task createRClassTask(final def sourceDir, final def destinationDir) {
        mProject.mkdir(destinationDir)

        def classpath = mVersionAdapter.getRClassPath()
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
            Utils.logInfo("Compile R.class, classpath:${classpath.first().absolutePath}")

            if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
                mProject.copy {
                    from mProject.zipTree(mVersionAdapter.getRClassPath().first().absolutePath + "/R.jar")
                    into mVersionAdapter.getRClassPath().first().absolutePath
                }
            }
        }
        return task
    }

    private Task createRJarTask(final def fromDir, final def desFile) {
        String taskName = "createRsJar${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, Jar.class, {
            it.from fromDir.path
            // The destinationDir property has been deprecated.
            // This is scheduled to be removed in Gradle 7.0. Please use the destinationDirectory property instead.
            if (Utils.compareVersion(mProject.gradle.gradleVersion, "6.0.1") >= 0) {
                it.getArchiveFileName().set("r-classes.jar")
                it.getDestinationDirectory().set(desFile)
            } else {
                it.archiveName = "r-classes.jar"
                it.destinationDir desFile
            }
        })
        task.doFirst {
            Utils.logInfo("Generate R.jar, Dir：$fromDir")
        }
        return task
    }

    private Task createBundleAarTask(final File from, final File destDir, final String filePath) {
        String taskName = "reBundleAar${mVariant.name.capitalize()}"
        Task task = mProject.getTasks().create(taskName, Zip.class, {
            it.from from
            it.include "**"
            if (Utils.compareVersion(mProject.gradle.gradleVersion, "6.0.1") >= 0) {
                it.getArchiveFileName().set(new File(filePath).name)
                it.getDestinationDirectory().set(destDir)
            } else {
                it.archiveName = new File(filePath).name
                it.destinationDir(destDir)
            }
        })

        return task
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
}
