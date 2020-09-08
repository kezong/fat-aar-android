package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.Factory
import org.gradle.internal.component.model.DefaultIvyArtifactName

import javax.annotation.Nullable

/**
 * FlavorArtifact
 * @author kezong on 2019/4/25.
 */
class FlavorArtifact {

    static DefaultResolvedArtifact createFlavorArtifact(Project project, LibraryVariant variant, ResolvedDependency unResolvedArtifact, String version) {
        ModuleVersionIdentifier identifier = createModuleVersionIdentifier(unResolvedArtifact)
        DefaultIvyArtifactName artifactName = createArtifactName(unResolvedArtifact)
        Project artifactProject = getArtifactProject(project, unResolvedArtifact)
        File artifactFile = createArtifactFile(artifactProject, variant, unResolvedArtifact, version)
        Factory<File> fileFactory = new Factory<File>() {
            @Override
            File create() {
                return artifactFile
            }
        }
        ComponentArtifactIdentifier artifactIdentifier = createComponentIdentifier(artifactFile)
        if (Utils.compareVersion(project.gradle.gradleVersion, "6.0.0") >= 0) {
            TaskDependencyContainer taskDependencyContainer = new TaskDependencyContainer() {
                @Override
                void visitDependencies(TaskDependencyResolveContext taskDependencyResolveContext) {
                    taskDependencyResolveContext.add(createTaskDependency(artifactProject, variant))
                }
            }
            return new DefaultResolvedArtifact(identifier, artifactName, artifactIdentifier, taskDependencyContainer, fileFactory)
        } else {
            TaskDependency taskDependency = createTaskDependency(artifactProject, variant)
            return new DefaultResolvedArtifact(identifier, artifactName, artifactIdentifier, taskDependency, fileFactory)
        }
    }

    private static ModuleVersionIdentifier createModuleVersionIdentifier(ResolvedDependency unResolvedArtifact) {
        return new DefaultModuleVersionIdentifier(
                unResolvedArtifact.getModuleGroup(),
                unResolvedArtifact.getModuleName(),
                unResolvedArtifact.getModuleVersion()
        )
    }

    private static DefaultIvyArtifactName createArtifactName(ResolvedDependency unResolvedArtifact) {
        return new DefaultIvyArtifactName(unResolvedArtifact.getModuleName(), "aar", "")
    }

    private static ComponentArtifactIdentifier createComponentIdentifier(final File artifactFile) {
        return new ComponentArtifactIdentifier() {
            @Override
            ComponentIdentifier getComponentIdentifier() {
                return null
            }

            @Override
            String getDisplayName() {
                return artifactFile.name
            }
        }
    }

    private static Project getArtifactProject(Project project, ResolvedDependency unResolvedArtifact) {
        for (Project p : project.getRootProject().getAllprojects()) {
            if (unResolvedArtifact.moduleName == p.name) {
                return p
            }
        }
        return null
    }

    private static File createArtifactFile(Project project, LibraryVariant variant, ResolvedDependency unResolvedArtifact, String version) {
        def buildPath = project.buildDir.path
        def outputName
        if (Utils.compareVersion(project.gradle.gradleVersion, "5.1.0") >= 0 && Utils.compareVersion(version, "3.4") < 0) {
            outputName = "$buildPath/outputs/aar/${unResolvedArtifact.moduleName}.aar"
        } else {
            outputName = "$buildPath/outputs/aar/$unResolvedArtifact.moduleName-$variant.flavorName-${variant.buildType.name}.aar"
        }
        return new File(outputName)
    }

    static TaskProvider<Task> getBundleTaskProvider(final Project project, final LibraryVariant variant) throws UnknownTaskException {
        def taskPath = "bundle" + variant.name.capitalize()
        TaskProvider bundleTask
        try {
            bundleTask = project.tasks.named(taskPath)
        } catch (UnknownTaskException ignored) {
            taskPath += "Aar"
            bundleTask = project.tasks.named(taskPath)
        }
        return bundleTask
    }

    private static TaskDependency createTaskDependency(Project project, LibraryVariant variant) {
        def bundleTaskProvider = getBundleTaskProvider(project, variant)

        return new TaskDependency() {
            @Override
            Set<? extends Task> getDependencies(@Nullable Task task) {
                def set = new HashSet()
                set.add(bundleTaskProvider.get())
                return set
            }
        }
    }
}
