package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.provider.MapProperty

/**
 * plugin entry
 */
class FatAarPlugin implements Plugin<Project> {

    public static final String ARTIFACT_TYPE_AAR = 'aar'

    public static final String ARTIFACT_TYPE_JAR = 'jar'

    private static final String CONFIG_NAME = "embed"

    public static final String CONFIG_SUFFIX = 'Embed'

    private Project project

    private RClassesTransform transform

    private final Collection<Configuration> embedConfigurations = new ArrayList<>()

    private MapProperty<String, List<AndroidArchiveLibrary>> variantPackagesProperty;

    @Override
    void apply(Project project) {
        this.project = project
        checkAndroidPlugin()
        FatUtils.attach(project)
        DirectoryManager.attach(project)
        project.extensions.create(FatAarExtension.NAME, FatAarExtension)
        createConfigurations()
        registerTransform()
        project.afterEvaluate {
            doAfterEvaluate()
        }
    }

    private registerTransform() {
        variantPackagesProperty = project.objects.mapProperty(String.class, List.class)
        if (FatUtils.compareVersion(VersionAdapter.AGPVersion, "8.0.0") >= 0) {
            FatAarPluginHelper.registerAsmTransformation(project, variantPackagesProperty)
        } else {
            transform = new RClassesTransform(project)
            // register in project.afterEvaluate is invalid.
            project.android.registerTransform(transform)
        }
    }

    private void doAfterEvaluate() {
        embedConfigurations.each {
            if (project.fataar.transitive) {
                it.transitive = true
            }
        }

        project.android.libraryVariants.all { variant ->
            Collection<ResolvedArtifact> artifacts = new ArrayList()
            Collection<ResolvedDependency> firstLevelDependencies = new ArrayList<>()
            embedConfigurations.each { configuration ->
                if (configuration.name == CONFIG_NAME
                        || configuration.name == variant.getBuildType().name + CONFIG_SUFFIX
                        || configuration.name == variant.getFlavorName() + CONFIG_SUFFIX
                        || configuration.name == variant.name + CONFIG_SUFFIX) {
                    Collection<ResolvedArtifact> resolvedArtifacts = resolveArtifacts(configuration)
                    artifacts.addAll(resolvedArtifacts)
                    artifacts.addAll(dealUnResolveArtifacts(configuration, variant as LibraryVariant, resolvedArtifacts))
                    firstLevelDependencies.addAll(configuration.resolvedConfiguration.firstLevelModuleDependencies)
                }
            }

            if (!artifacts.isEmpty()) {
                def processor = new VariantProcessor(project, variant, variantPackagesProperty)
                processor.processVariant(artifacts, firstLevelDependencies, transform)
            }
        }
    }

    private void createConfigurations() {
        Configuration embedConf = project.configurations.create(CONFIG_NAME)
        createConfiguration(embedConf)
        FatUtils.logInfo("Creating configuration embed")

        project.android.buildTypes.all { buildType ->
            String configName = buildType.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            FatUtils.logInfo("Creating configuration " + configName)
        }

        project.android.productFlavors.all { flavor ->
            String configName = flavor.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            FatUtils.logInfo("Creating configuration " + configName)
            project.android.buildTypes.all { buildType ->
                String variantName = flavor.name + buildType.name.capitalize()
                String variantConfigName = variantName + CONFIG_SUFFIX
                Configuration variantConfiguration = project.configurations.create(variantConfigName)
                createConfiguration(variantConfiguration)
                FatUtils.logInfo("Creating configuration " + variantConfigName)
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
        project.gradle.addListener(new EmbedResolutionListener(project, embedConf))
        embedConfigurations.add(embedConf)
    }

    private Collection<ResolvedArtifact> resolveArtifacts(Configuration configuration) {
        def set = new ArrayList()
        if (configuration != null) {
            configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
                if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                    //
                } else {
                    throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
                }
                set.add(artifact)
            }
        }
        return set
    }

    private Collection<ResolvedArtifact> dealUnResolveArtifacts(Configuration configuration, LibraryVariant variant, Collection<ResolvedArtifact> artifacts) {
        def artifactList = new ArrayList()
        configuration.resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
            def match = artifacts.any { artifact ->
                dependency.moduleName == artifact.moduleVersion.id.name
            }

            if (!match) {
                def flavorArtifact = FlavorArtifact.createFlavorArtifact(project, variant, dependency)
                if (flavorArtifact != null) {
                    artifactList.add(flavorArtifact)
                }
            }
        }
        return artifactList
    }
}
