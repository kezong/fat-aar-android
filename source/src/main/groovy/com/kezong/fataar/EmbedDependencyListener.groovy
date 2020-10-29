package com.kezong.fataar

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

class EmbedDependencyListener implements DependencyResolutionListener {

    private final Project project

    private final String flavorName;

    private final Configuration configuration

    EmbedDependencyListener(Project project, Configuration configuration, String flavorName) {
        this.project = project
        this.flavorName = flavorName;
        this.configuration = configuration
    }

    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        String configurationName = new String("compileOnly")
        if (flavorName != null && flavorName != "") {
            configurationName = flavorName + new String("CompileOnly")
        }
        configuration.dependencies.each { dependency ->
            if (dependency instanceof DefaultProjectDependency) {
                if (dependency.targetConfiguration == null) {
                    dependency.targetConfiguration = "default"
                }
                // support that the module can be indexed in Android Studio 4.0.0
                DefaultProjectDependency dependencyClone = dependency.copy()
                dependencyClone.targetConfiguration = null;
                // The purpose is to support the code hints
                project.dependencies.add(configurationName, dependencyClone)
            } else {
                // The purpose is to support the code hints
                project.dependencies.add(configurationName, dependency)
            }
        }
        project.gradle.removeListener(this)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {
    }
}