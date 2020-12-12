package com.kezong.fataar

import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import org.gradle.api.provider.Property
import org.gradle.workers.WorkerExecutor

/**
 * The task is used to merge consumer proguard (proguard.txt)
 * Finally, content of sub aar's proguard.txt will append to embed module's proguard.txt
 */
class EmbedProguardTask extends NonIncrementalTask {

    Collection<File> inputFiles

    File outputFile

    @Override
    protected void doTaskAction() throws Exception {
        mergeFiles(inputFiles, outputFile)
    }

    @Override
    WorkerExecutor getWorkerExecutor() {
        return null
    }

    @Override
    Property<Boolean> getEnableGradleWorkers() {
        return project.objects.property(Boolean.class).value(false)
    }

    static void mergeFiles(Collection<File> inputFiles, File output) {
        if (inputFiles == null) {
            return
        }
        // filter out any non-existent files
        Collection<File> existingFiles = inputFiles.findAll { it ->
            it.exists()
        }

        if (existingFiles.size() == 1) {
            FileUtils.copyFile(existingFiles[0], output)
            return
        }

        // no input? done.
        if (existingFiles.isEmpty()) {
            return
        }

        // otherwise put all the files together append to output file
        for (file in existingFiles) {
            try {
                def content = Files.asCharSource(file, Charsets.UTF_8).read()
                Files.asCharSink(output, Charsets.UTF_8, FileWriteMode.APPEND).write("$content\n")
            } catch (Throwable ignore) {
                def content = Files.toString(file, Charsets.UTF_8)
                Files.append("$content\n", output, Charsets.UTF_8)
            }
        }
    }
}
