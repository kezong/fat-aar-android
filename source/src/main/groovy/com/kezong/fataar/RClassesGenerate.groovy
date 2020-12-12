package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * Used to generate R classes
 * generate R File -> R Class -> R Jar -> unzip aar -> reBundle with R.jar
 * @deprecated Prefer {@code RClassesTransform}
 */
class RClassesGenerate {

    private final Project mProject
    private final LibraryVariant mVariant

    private VersionAdapter mVersionAdapter
    private final Collection<AndroidArchiveLibrary> mLibraries

    RClassesGenerate(Project project, LibraryVariant variant, Collection<AndroidArchiveLibrary> libraries) {
        mProject = project
        mVariant = variant
        mLibraries = libraries
        mVersionAdapter = new VersionAdapter(project, variant)
    }

    TaskProvider configure(TaskProvider<Task> reBundleTask) {
        File rJavaDir = DirectoryManager.getRJavaDirectory(mVariant)
        File rClassDir = DirectoryManager.getRClassDirectory(mVariant)
        File rJarDir = DirectoryManager.getRJarDirectory(mVariant)
        def RJarTask = configureRJarTask(rClassDir, rJarDir, reBundleTask)
        def RClassTask = configureRClassTask(rJavaDir, rClassDir, RJarTask)
        def RFileTask = configureRFileTask(rJavaDir, RClassTask)

        return RFileTask
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

    private TaskProvider configureRFileTask(final File destFolder, final TaskProvider RClassTask) {
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
                        FatUtils.logInfo("Generate R File, Library:${it.name}")
                        createRFile(it, destFolder, symbolsMap)
                    }
                }
            }
        }
        return task
    }

    private TaskProvider configureRClassTask(final File sourceDir, final File destinationDir, final TaskProvider RJarTask) {
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
                FatUtils.logInfo("Compile R.class, Dir:${sourceDir.path}")
                FatUtils.logInfo("Compile R.class, classpath:${classpath.first().absolutePath}")

                if (FatUtils.compareVersion(VersionAdapter.AGPVersion, "3.3.0") >= 0) {
                    mProject.copy {
                        from mProject.zipTree(mVersionAdapter.getRClassPath().first().absolutePath + "/R.jar")
                        into mVersionAdapter.getRClassPath().first().absolutePath
                    }
                }
            }
        }
        return task
    }

    private TaskProvider configureRJarTask(final File fromDir, final File desFile, final TaskProvider reBundleAarTask) {
        String taskName = "createRsJar${mVariant.name.capitalize()}"
        TaskProvider task = mProject.getTasks().register(taskName, Jar) {
            finalizedBy(reBundleAarTask)

            it.from fromDir.path
            // The destinationDir property has been deprecated.
            // This is scheduled to be removed in Gradle 7.0. Please use the destinationDirectory property instead.
            if (FatUtils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                it.getArchiveFileName().set("${mVariant.getApplicationId()}-r-classes.jar")
                it.getDestinationDirectory().set(desFile)
            } else {
                it.archiveName = "${mVariant.getApplicationId()}-r-classes.jar"
                it.destinationDir = desFile
            }
            doFirst {
                FatUtils.logInfo("Generate R.jar, Dir：$fromDir")
            }
        }
        return task
    }
}