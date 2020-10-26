package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
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
    private File mAarOutputFile
    private final String mGradlePluginVersion
    private VersionAdapter mVersionAdapter
    private final Collection<AndroidArchiveLibrary> mLibraries

    RProcessor(Project project, LibraryVariant variant, Collection<AndroidArchiveLibrary> libraries, String version) {
        mProject = project
        mVariant = variant
        mLibraries = libraries
        mGradlePluginVersion = version
        mVersionAdapter = new VersionAdapter(project, variant, version)
        // R.java dir
        mJavaDir = mProject.file("${mProject.getBuildDir()}/intermediates/${Constants.INTERMEDIATES_TEMP_FOLDER}/r/${mVariant.name}")
        // R.class compile dir
        mClassDir = mProject.file("${mProject.getBuildDir()}/intermediates/${Constants.INTERMEDIATES_TEMP_FOLDER}/r-class/${mVariant.name}")
        // R.jar dir
        mJarDir = mProject.file("${mProject.getBuildDir()}/outputs/${Constants.RE_BUNDLE_FOLDER}/${mVariant.name}/libs")
        // Aar unzip dir
        mAarUnZipDir = mJarDir.parentFile
    }

    void inject(TaskProvider<Task> bundleTask) {
        def reBundleAar = createBundleAarTask(mAarUnZipDir)
        def RJarTask = createRJarTask(mClassDir, mJarDir, reBundleAar)
        def RClassTask = createRClassTask(mJavaDir, mClassDir, RJarTask)
        def RFileTask = createRFileTask(mJavaDir, RClassTask)

        reBundleAar.configure {
            doLast {
                Utils.logAnytime("target: ${mAarOutputFile.absolutePath}")
            }
        }

        bundleTask.configure { it ->
            finalizedBy(RFileTask)
            if (Utils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                mAarOutputFile = new File(it.getDestinationDirectory().getAsFile().get(), it.getArchiveFileName().get())
            } else {
                mAarOutputFile = new File(it.destinationDir, it.archiveName)
            }

            doFirst {
                // Delete previously unzipped data.
                mAarUnZipDir.deleteDir()
                mJarDir.mkdirs()
            }

            doLast {
                mProject.copy {
                    from mProject.zipTree(mAarOutputFile)
                    into mAarUnZipDir
                }
                deleteEmptyDir(mAarUnZipDir)
            }
        }
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
                def name = splits.get(1).replace(".", "_")
                if (subclass == "attr?") {
                    //styleable attributes
                    subclass = "attr"
                }
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

    private TaskProvider createRFileTask(final File destFolder, final TaskProvider RClassTask) {
        def task = mProject.tasks.register("createRsFile${mVariant.name}") {
            finalizedBy(RClassTask)

            inputs.files(mLibraries.stream().map { it.symbolFile }.collect())
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            outputs.dir(destFolder)

            doLast {
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
        }
        return task
    }

    private TaskProvider createRClassTask(final File sourceDir, final File destinationDir, final TaskProvider RJarTask) {
        mProject.mkdir(destinationDir)

        def classpath = mVersionAdapter.getRClassPath()
        String taskName = "compileRs${mVariant.name.capitalize()}"
        TaskProvider task = mProject.getTasks().register(taskName, JavaCompile.class) {
            finalizedBy(RJarTask)

            it.source = sourceDir.path
            it.sourceCompatibility = mProject.android.compileOptions.sourceCompatibility
            it.targetCompatibility = mProject.android.compileOptions.targetCompatibility
            it.classpath = classpath
            it.destinationDir = destinationDir

            doFirst {
                Utils.logInfo("Compile R.class, Dir:${sourceDir.path}")
                Utils.logInfo("Compile R.class, classpath:${classpath.first().absolutePath}")

                if (mGradlePluginVersion != null && Utils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
                    mProject.copy {
                        from mProject.zipTree(mVersionAdapter.getRClassPath().first().absolutePath + "/R.jar")
                        into mVersionAdapter.getRClassPath().first().absolutePath
                    }
                }
            }
        }
        return task
    }

    private TaskProvider createRJarTask(final File fromDir, final File desFile, final TaskProvider reBundleAarTask) {
        String taskName = "createRsJar${mVariant.name.capitalize()}"
        TaskProvider task = mProject.getTasks().register(taskName, Jar) {
            finalizedBy(reBundleAarTask)

            it.from fromDir.path
            // The destinationDir property has been deprecated.
            // This is scheduled to be removed in Gradle 7.0. Please use the destinationDirectory property instead.
            if (Utils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                it.getArchiveFileName().set("r-classes.jar")
                it.getDestinationDirectory().set(desFile)
            } else {
                it.archiveName = "r-classes.jar"
                it.destinationDir = desFile
            }
            doFirst {
                Utils.logInfo("Generate R.jar, Dir：$fromDir")
            }
        }
        return task
    }

    private TaskProvider createBundleAarTask(final File from) {
        String taskName = "reBundleAar${mVariant.name.capitalize()}"
        TaskProvider task = mProject.getTasks().register(taskName, Zip.class) {
            it.from from
            it.include "**"
            if (Utils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                it.getArchiveFileName().set(mAarOutputFile.getName())
                it.getDestinationDirectory().set(mAarOutputFile.getParentFile())
            } else {
                it.archiveName = mAarOutputFile.getName()
                it.destinationDir = mAarOutputFile.getParentFile()
            }
        }

        return task
    }

    private void deleteEmptyDir(final File file) {
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