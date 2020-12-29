package com.kezong.fataar

import com.android.build.api.transform.*
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import org.gradle.api.Project

import java.util.regex.Pattern
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BuildClassFilterTransform extends Transform {

    private static final String NAME = "BuildClassFilter"

    private Project mProject
    private FatAarExtension configExtension

    BuildClassFilterTransform(Project project, FatAarExtension config) {
        mProject = project
        configExtension = config
    }

    @Override
    String getName() {
        return NAME
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        if (mProject.plugins.hasPlugin(AppPlugin)) {
            return TransformManager.SCOPE_FULL_PROJECT
        } else if (mProject.plugins.hasPlugin(LibraryPlugin)) {
            return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT)
        }
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {

        FatUtils.logInfo("start exclude class:")

        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll()
        }
        transformInvocation.inputs.forEach { TransformInput input ->
            input.jarInputs.forEach { JarInput jarInput ->
                File targetJar = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                excludeFilesFromJar(jarInput.file, targetJar)
            }

            input.directoryInputs.forEach { DirectoryInput directoryInput ->
                File targetDir = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                if (targetDir.exists()) {
                    FileUtils.deleteDirectoryContents(targetDir)
                }
                targetDir.mkdirs()

                List<String> targetList = new ArrayList<>()
                getDeepDirFileList(directoryInput.file, targetList)
                excludeFilesFromDirectory(directoryInput.file.absolutePath, targetList, configExtension.excludeClasses)

                FileUtils.copyDirectory(directoryInput.file, targetDir)
            }
        }
    }

    private void getDeepDirFileList(File dir, List<String> targetList) {
        File[] dirList = dir.listFiles()
        for (File indexFile : dirList) {
            if (indexFile.isDirectory()) {
                getDeepDirFileList(indexFile, targetList)
            } else {
                targetList.add(indexFile.absolutePath)
            }
        }
    }

    private boolean matchPatternFile(List<String> patternList, String file) {
        if (patternList == null || file == null) {
            return false
        }

        for (String pattern : patternList) {
            if (Pattern.compile(pattern).matcher(file).matches()) {
                FatUtils.logInfo("  exclude class : ${file}")
                return true
            }
        }
        return false
    }

    private void excludeFilesFromJar(File srcJar, File targetJar) {
        if (targetJar.exists()) {
            targetJar.delete()
        }
        targetJar.createNewFile()
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(srcJar))
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(targetJar))
        def zipEntry = null
        while ((zipEntry = zipInputStream.nextEntry) != null) {
            if (matchPatternFile(configExtension.excludeClasses, zipEntry.name)) {
                continue
            }
            zipOutputStream.putNextEntry(zipEntry)
            ByteStreams.copy(zipInputStream, zipOutputStream)
            zipOutputStream.closeEntry()
        }
        zipInputStream.close()
        zipOutputStream.close()
    }

    private void excludeFilesFromDirectory(String dirAbsolutePath, List<String> dirFileList, List<String> patternList) {
        dirFileList.forEach {
            String relativePath = it.substring(dirAbsolutePath.length() + 1)
            if (matchPatternFile(patternList, relativePath)) {
                File delFile = new File(it)
                delFile.delete()
            }
        }
    }

}