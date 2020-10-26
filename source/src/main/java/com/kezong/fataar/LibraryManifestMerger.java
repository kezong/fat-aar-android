package com.kezong.fataar;

import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.tasks.InvokeManifestMerger;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.MergingReport;
import com.android.utils.ILogger;

import org.apache.tools.ant.BuildException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ManifestMerger for Library
 * @author kezong on 2019/7/8.
 */
public class LibraryManifestMerger extends InvokeManifestMerger {

    private String mGradlePluginVersion;

    private String mGradleVersion;

    public void setGradlePluginVersion(String gradlePluginVersion) {
        mGradlePluginVersion = gradlePluginVersion;
    }

    public void setGradleVersion(String gradleVersion) {
        mGradleVersion = gradleVersion;
    }

    protected void doTaskAction() {
        try {
            doFullTaskAction();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Gradle Plugin Version:" + mGradlePluginVersion);
            System.out.println("Gradle Version:" + mGradleVersion);
            System.out.println("If you see this error message, please submit issue to " +
                    "https://github.com/kezong/fat-aar-android/issues with Gradle version. Thank you.");
        }
    }

    @TaskAction
    protected void doFullTaskAction() throws ManifestMerger2.MergeFailureException, IOException {
        ILogger iLogger = new LoggerWrapper(getLogger());
        ManifestMerger2.Invoker mergerInvoker = ManifestMerger2.
                newMerger(getMainManifestFile(), iLogger, ManifestMerger2.MergeType.LIBRARY);
        List<File> secondaryManifestFiles = getSecondaryManifestFiles();
        List<ManifestProvider> manifestProviders = new ArrayList<>();
        if (secondaryManifestFiles != null) {
            for (final File file : secondaryManifestFiles) {
                if (!file.exists()) {
                    continue;
                }
                manifestProviders.add(new ManifestProvider() {
                    @Override
                    public File getManifest() {
                        return file.getAbsoluteFile();
                    }

                    @Override
                    public String getName() {
                        return file.getName();
                    }
                });
            }
        }
        mergerInvoker.addManifestProviders(manifestProviders);
        MergingReport mergingReport = mergerInvoker.merge();
        if (mergingReport.getResult().isError()) {
            getLogger().error(mergingReport.getReportString());
            mergingReport.log(iLogger);
            throw new BuildException(mergingReport.getReportString());
        }

        // fix utf-8 problem in windows
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(getOutputFile()), "UTF-8")
        );
        writer.append(mergingReport
                .getMergedDocument(MergingReport.MergedManifestKind.MERGED));
        writer.flush();
        writer.close();
    }

    public WorkerExecutor getWorkerExecutor() {
        return null;
    }
}
