package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
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

    private static final String CONFIG_NAME = "embed"

    public static final String CONFIG_SUFFIX = 'Embed'

    private Project project

    private RTransform transform

    private final Collection<Configuration> embedConfigurations = new ArrayList<>()

    private String mGradlePluginVersion

    @Override
    void apply(Project project) {
        this.project = project
        Utils.setProject(project)
        DirectoryManager.attach(project)
        checkAndroidPlugin()
        checkGradlePluginVersion()
        project.extensions.create(FatAarExtension.NAME, FatAarExtension)
        createConfigurations()
        if (project.fataar.transformR) {
            transform = new RTransform(project)
            // register in project.afterEvaluate is invalid.
            project.android.registerTransform(transform)
        }

        project.afterEvaluate {
            doAfterEvaluate()
        }
    }

    private void doAfterEvaluate() {
        project.android.libraryVariants.all { LibraryVariant variant ->
            Collection<ResolvedArtifact> artifacts = new ArrayList()
            Collection<ResolvedDependency> firstLevelDependencies = new ArrayList<>()
            embedConfigurations.each { configuration ->
                if (configuration.name == CONFIG_NAME
                        || configuration.name == variant.getBuildType().name + CONFIG_SUFFIX
                        || configuration.name == variant.getFlavorName() + CONFIG_SUFFIX
                        || configuration.name == variant.name + CONFIG_SUFFIX) {
                    Collection<ResolvedArtifact> resolvedArtifacts = resolveArtifacts(configuration)
                    artifacts.addAll(resolvedArtifacts)
                    artifacts.addAll(dealUnResolveArtifacts(configuration, variant, resolvedArtifacts))
                    firstLevelDependencies.addAll(configuration.resolvedConfiguration.firstLevelModuleDependencies)
                }
            }

            def processor = new VariantProcessor(project, variant, mGradlePluginVersion)
            processor.processVariant(artifacts, firstLevelDependencies, transform)
        }
    }

    private void createConfigurations() {
        Configuration embedConf = project.configurations.create(CONFIG_NAME)
        createConfiguration(embedConf)
        Utils.logInfo("Creating configuration embed")

        project.android.buildTypes.all { buildType ->
            String configName = buildType.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            Utils.logInfo("Creating configuration " + configName)
        }

        project.android.productFlavors.all { flavor ->
            String configName = flavor.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            Utils.logInfo("Creating configuration " + configName)
            project.android.buildTypes.all { buildType ->
                String variantName = flavor.name + buildType.name.capitalize()
                String variantConfigName = variantName + CONFIG_SUFFIX
                Configuration variantConfiguration = project.configurations.create(variantConfigName)
                createConfiguration(variantConfiguration)
                Utils.logInfo("Creating configuration " + variantConfigName)
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
        project.gradle.addListener(new EmbedDependencyListener(project, embedConf))
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
            boolean match = false
            artifacts.each { artifact ->
                if (dependency.moduleName == artifact.name) {
                    match = true
                }
            }
            if (!match) {
                def flavorArtifact = FlavorArtifact.createFlavorArtifact(project, variant, dependency, mGradlePluginVersion)
                artifactList.add(flavorArtifact)
            }
        }
        return artifactList
    }

    private void checkGradlePluginVersion() {
        project.rootProject.buildscript.getConfigurations().getByName("classpath").getDependencies().each { Dependency dep ->
            if (dep.group == "com.android.tools.build" && dep.name == "gradle") {
                mGradlePluginVersion = dep.version
            }
        }

        if (mGradlePluginVersion == null) {
            def classpathBuildscriptConfiguration = mProject.rootProject.buildscript.getConfigurations().getByName("classpath")
            def artifacts = classpathBuildscriptConfiguration.getResolvedConfiguration().getResolvedArtifacts()
            artifacts.find {
                def artifactId = it.getModuleVersion().getId()
                if (artifactId.getGroup() == "com.android.tools.build" && artifactId.getName() == "gradle") {
                    mGradlePluginVersion = artifactId.getVersion()
                    return true
                }
                return false
            }
        }

        if (mGradlePluginVersion == null) {
            throw new IllegalStateException("com.android.tools.build:gradle not found in buildscript classpath")
        }
    }
}
