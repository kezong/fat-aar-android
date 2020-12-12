package com.kezong.fataar

import com.android.build.gradle.api.LibraryVariant
import com.android.builder.model.ProductFlavor
import org.gradle.api.Project
import org.gradle.api.Task
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

    static DefaultResolvedArtifact createFlavorArtifact(Project project, LibraryVariant variant, ResolvedDependency unResolvedArtifact) {
        Project artifactProject = getArtifactProject(project, unResolvedArtifact)
        TaskProvider bundleProvider = getBundleTask(artifactProject, variant)
        if (bundleProvider == null) {
            return null
        }

        ModuleVersionIdentifier identifier = createModuleVersionIdentifier(unResolvedArtifact)
        DefaultIvyArtifactName artifactName = createArtifactName(unResolvedArtifact)
        File artifactFile = createArtifactFile(artifactProject, bundleProvider.get())
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
                    taskDependencyResolveContext.add(createTaskDependency(bundleProvider.get()))
                }
            }
            return new DefaultResolvedArtifact(identifier, artifactName, artifactIdentifier, taskDependencyContainer, fileFactory)
        } else {
            TaskDependency taskDependency = createTaskDependency(bundleProvider.get())
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

    private static File createArtifactFile(Project project, Task bundle) {
        File output
        if (Utils.compareVersion(project.gradle.gradleVersion, "5.1") >= 0) {
            output = new File(bundle.getDestinationDirectory().getAsFile().get(), bundle.getArchiveFileName().get())
        } else {
            output = new File(bundle.destinationDir, bundle.archiveName)
        }
        return output
    }

    private static TaskProvider getBundleTask(Project project, LibraryVariant variant) {
        TaskProvider bundleTaskProvider = null
        project.android.libraryVariants.find { LibraryVariant subVariant ->
            // 1. find same flavor
            if (variant.name == subVariant.name) {
                try {
                    bundleTaskProvider = VersionAdapter.getBundleTaskProvider(project, subVariant)
                    return true
                } catch (Exception ignore) {
                    return false
                }
            }

            // 2. find missingStrategies
            ProductFlavor flavor = variant.productFlavors.first()
            flavor.missingDimensionStrategies.find { entry ->
                String toDimension = entry.getKey()
                String toFlavor = entry.getValue().getFallbacks().first()
                ProductFlavor subFlavor = subVariant.productFlavors.first()
                if (toDimension == subFlavor.dimension
                        && toFlavor == subFlavor.name
                        && variant.buildType.name == subVariant.buildType.name) {
                    try {
                        bundleTaskProvider = VersionAdapter.getBundleTaskProvider(project, subVariant)
                        return true
                    } catch (Exception ignore) {
                        return false
                    }
                }
            }
            return bundleTaskProvider != null
        }

        return bundleTaskProvider
    }

    private static TaskDependency createTaskDependency(Task bundleTask) {
        return new TaskDependency() {
            @Override
            Set<? extends Task> getDependencies(@Nullable Task task) {
                def set = new HashSet()
                set.add(bundleTask)
                return set
            }
        }
    }
}
