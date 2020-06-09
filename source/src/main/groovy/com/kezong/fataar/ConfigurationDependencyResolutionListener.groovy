package com.kezong.fataar

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

class ConfigurationDependencyResolutionListener implements DependencyResolutionListener {

    private final Project project

    private final Configuration configuration

    ConfigurationDependencyResolutionListener(Project project, Configuration configuration) {
        this.project = project
        this.configuration = configuration
    }

    @Override
    void beforeResolve(ResolvableDependencies resolvableDependencies) {
        configuration.dependencies.each { dependency ->
            if (dependency instanceof DefaultProjectDependency) {
                if (dependency.targetConfiguration == null) {
                    dependency.targetConfiguration = "default"
                }
                // support that the module can be indexed in Android Studio 4.0.0
                DefaultProjectDependency dependencyClone = dependency.copy()
                dependencyClone.targetConfiguration = null;
                project.dependencies.add('compileOnly', dependencyClone)
                project.dependencies.add('testCompile', dependencyClone)
            } else {
                project.dependencies.add('compileOnly', dependency)
                project.dependencies.add('testCompile', dependency)
            }
        }
        project.gradle.removeListener(this)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {
    }
}