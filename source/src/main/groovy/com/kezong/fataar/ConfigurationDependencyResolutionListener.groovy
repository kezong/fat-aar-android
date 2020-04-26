package com.kezong.fataar

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

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
            project.dependencies.add('compileOnly', dependency)
        }
        project.gradle.removeListener(this)
    }

    @Override
    void afterResolve(ResolvableDependencies resolvableDependencies) {
    }
}