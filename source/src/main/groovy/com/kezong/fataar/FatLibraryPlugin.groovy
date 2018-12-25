package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact

/**
 * plugin entry
 *
 * Created by Vigi on 2017/1/14.
 * Modified by kezong on 2018/12/18
 */
class FatLibraryPlugin implements Plugin<Project> {

    public static final String ARTIFACT_TYPE_AAR = 'aar'

    public static final String ARTIFACT_TYPE_JAR = 'jar'

    private Project project
    private Configuration embedConf

    private Set<ResolvedArtifact> artifacts

    @Override
    void apply(Project project) {
        this.project = project
        checkAndroidPlugin()
        createConfiguration()
        project.afterEvaluate {
            resolveArtifacts()
            project.android.libraryVariants.all { variant ->
                processVariant(variant)
            }
        }

    }

    private void checkAndroidPlugin() {
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new ProjectConfigurationException('fat-aar-plugin must be applied in project that' +
                    ' has android library plugin!', null)
        }
    }

    private void createConfiguration() {
        embedConf = project.configurations.create('embed')
        embedConf.visible = false
    }

    private void resolveArtifacts() {
        def set = new HashSet<>()
        embedConf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            // jar file wouldn't be here
            if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                LogUtil.logInfo('[embed detected][' + artifact.type + ']' + artifact.moduleVersion.id)
            } else {
                throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
            }
            set.add(artifact)
        }
        artifacts = Collections.unmodifiableSet(set)
    }

    private void processVariant(LibraryVariant variant) {
        def processor = new VariantProcessor(project, variant)
        processor.addArtifacts(artifacts)
        processor.processVariant()
    }
}
