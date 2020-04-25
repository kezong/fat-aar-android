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

    public static final String CONFIG_SUFFIX = 'Embed'

    private Project project

    @Override
    void apply(Project project) {
        this.project = project
        Utils.setProject(project)
        checkAndroidPlugin()
        final Configuration embedConf = project.configurations.create('embed')
        createConfiguration(embedConf)
        print("Creating configuration embed\n")

        project.android.buildTypes.all { buildType ->
            String configName = buildType.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            print("Creating configuration " + configName + "\n")
        }

        project.android.productFlavors.all { flavor ->
            String configName = flavor.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            print("Creating configuration " + configName + "\n")
            project.android.buildTypes.all { buildType ->
                String variantName = flavor.name + buildType.name.capitalize()
                String variantConfigName = variantName + CONFIG_SUFFIX
                Configuration variantConfiguration = project.configurations.create(variantConfigName)
                createConfiguration(variantConfiguration)
                print("Creating configuration " + variantConfigName + "\n")
            }
        }

        project.afterEvaluate {
            Set<ResolvedArtifact> commonArtifacts = resolveArtifacts(embedConf)
            Set<ResolvedDependency> commonUnResolveArtifacts = dealUnResolveArtifacts(embedConf, commonArtifacts)
            project.android.libraryVariants.all { variant ->
                String buildTypeConfigName = variant.getBuildType().name + CONFIG_SUFFIX
                Configuration buildTypeConfiguration 
                try {
                    buildTypeConfiguration = project.configurations.getByName(buildTypeConfigName)
                } catch(Exception ignored) {
                    print("Ignored configuration " + buildTypeConfigName + "\n")
                }

                /**
                 * Doesn't support more than one flavor dimension: LibraryVariant does not have
                 * public interface for VariantConfiguration list(which holds flavor configs).
                 * Also Library plugin doesn't have API for variants in the project.
                 */
                String flavorConfigName = variant.getFlavorName() + CONFIG_SUFFIX
                Configuration flavorConfiguration
                if (flavorConfigName != CONFIG_SUFFIX) {
                    try {
                        flavorConfiguration = project.configurations.getByName(flavorConfigName)
                    } catch(Exception ignored) {
                        print("Ignored configuration " + flavorConfigName + "\n")
                    }
                }

                String variantConfigName = variant.name + CONFIG_SUFFIX
                Configuration variantConfiguration
                if (variantConfigName != buildTypeConfigName) {
                    try {
                        variantConfiguration = project.configurations.getByName(variantConfigName)
                    } catch(Exception ignored) {
                        print("Ignored configuration " + variantConfigName + "\n")
                    }
                }

                Set<ResolvedArtifact> artifacts = new HashSet<>()
                artifacts.addAll(commonArtifacts)
                artifacts.addAll(resolveArtifacts(buildTypeConfiguration))
                artifacts.addAll(resolveArtifacts(flavorConfiguration))
                artifacts.addAll(resolveArtifacts(variantConfiguration))

                Set<ResolvedDependency> unResolveArtifacts = new HashSet<>()
                unResolveArtifacts.addAll(commonUnResolveArtifacts)
                unResolveArtifacts.addAll(dealUnResolveArtifacts(buildTypeConfiguration, artifacts))
                unResolveArtifacts.addAll(dealUnResolveArtifacts(flavorConfiguration, artifacts))
                unResolveArtifacts.addAll(dealUnResolveArtifacts(variantConfiguration, artifacts))

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
        if (configuration != null) {
            configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
                // jar file wouldn't be here
                if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                    Utils.logAnytime('[embed detected][' + artifact.type + ']' + artifact.moduleVersion.id)
                } else {
                    throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
                }
                set.add(artifact)
            }
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
        def dependencySet = new HashSet()
        if (configuration != null) {
            def dependencies = Collections.unmodifiableSet(configuration.resolvedConfiguration.firstLevelModuleDependencies)
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
        }
        return Collections.unmodifiableSet(dependencySet)
    }
}
