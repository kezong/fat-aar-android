package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency

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

    @Override
    void apply(Project project) {
        this.project = project
        Utils.setProject(project)
        checkAndroidPlugin()
        final Configuration embedConf = project.configurations.create('embed')
        createConfiguration(embedConf)
        print("Creating configuration embed\n")

        project.android.productFlavors.all { flavor ->
            String configName = flavor.name + 'Embed'
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            print("Creating configuration " + configName + "\n")
        }

//        project.android.libraryVariants.all { variant ->
//            String configName = variant.name + 'Embed'
//            final Configuration configuration = project.configurations.create(configName)
//            createConfiguration(configuration)
//            print("Configuration created: " + configName + "\n")
//        }

        project.afterEvaluate {
            Set<ResolvedArtifact> commonArtifacts = resolveArtifacts(embedConf)
            Set<ResolvedDependency> commonUnResolveArtifacts = dealUnResolveArtifacts(embedConf, commonArtifacts)
            project.android.libraryVariants.all { variant ->
//                String configName = variant.name + 'Embed'

                /**
                 * Doesn't support more than one flavor dimension: LibraryVariant does not have
                 * public interface for VariantConfiguration list(which holds flavor configs).
                 * Also Library plugin doesn't have API for variants in the project.
                 */
                String flavorConfigName = variant.getFlavorName() + 'Embed'
                Configuration flavorConfiguration = project.configurations.getByName(flavorConfigName)

                Set<ResolvedArtifact> artifacts = new HashSet<>()
                artifacts.addAll(commonArtifacts)
                artifacts.addAll(resolveArtifacts(flavorConfiguration))

                Set<ResolvedDependency> unResolveArtifacts = new HashSet<>()
                unResolveArtifacts.addAll(commonUnResolveArtifacts)
                unResolveArtifacts.addAll(dealUnResolveArtifacts(flavorConfiguration, artifacts))

                processVariant(variant, artifacts, unResolveArtifacts)
            }
        }

    }

    private void checkAndroidPlugin() {
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new ProjectConfigurationException('fat-aar-plugin must be applied in project that' +
                    ' has android library plugin!', null)
        }
    }

    private void createConfiguration(Configuration embedConf) {
        embedConf.visible = false
        embedConf.transitive = false
        project.gradle.addListener(new ConfigurationDependencyResolutionListener(project, embedConf))
    }

    private Set<ResolvedArtifact> resolveArtifacts(Configuration configuration) {
        def set = new HashSet<>()
        configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            // jar file wouldn't be here
            if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                Utils.logAnytime('[embed detected][' + artifact.type + ']' + artifact.moduleVersion.id)
            } else {
                throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
            }
            set.add(artifact)
        }
        return Collections.unmodifiableSet(set)
    }

    private void processVariant(LibraryVariant variant, Set<ResolvedArtifact> artifacts, Set<ResolvedDependency> unResolveArtifacts) {
        def processor = new VariantProcessor(project, variant)
        processor.addArtifacts(artifacts)
        processor.addUnResolveArtifact(unResolveArtifacts)
        processor.processVariant()
    }

    private Set<ResolvedDependency> dealUnResolveArtifacts(Configuration configuration, Set<ResolvedArtifact> artifacts) {
        def dependencies = Collections.unmodifiableSet(configuration.resolvedConfiguration.firstLevelModuleDependencies)
        def dependencySet = new HashSet()
        dependencies.each { dependency ->
            boolean match = false
            artifacts.each { artifact ->
                if (dependency.moduleName == artifact.name) {
                    match = true
                }
            }
            if (!match) {
                Utils.logAnytime('[unResolve dependency detected][' + dependency.name + ']')
                dependencySet.add(dependency)
            }
        }
        return Collections.unmodifiableSet(dependencySet)
    }
}
