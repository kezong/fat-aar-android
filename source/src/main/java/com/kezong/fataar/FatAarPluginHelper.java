package com.kezong.fataar;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import kotlin.Unit;
import kotlin.text.StringsKt;

import static com.android.build.api.instrumentation.FramesComputationMode.COPY_FRAMES;

public class FatAarPluginHelper {

    public static void registerAsmTransformation(
            Project project,
            MapProperty<String, List<AndroidArchiveLibrary>> variantPackagesProperty) {
        AndroidComponentsExtension<?, ?, Variant> components =
                project.getExtensions().getByType(AndroidComponentsExtension.class);
        components.onVariants(components.selector().all(), variant -> {
            variant.getInstrumentation().transformClassesWith(
                    RClassAsmTransformerFactory.class,
                    InstrumentationScope.PROJECT,
                    params -> {
                        params.getNamespace().set(variant.getNamespace());
                        params.getLibraryNamespaces().set(variantPackagesProperty.getting(variant.getName())
                                .map(list -> list.stream().map(it -> it.getPackageName()).collect(Collectors.toList()))
                        );
                        return Unit.INSTANCE;
                    });
            variant.getInstrumentation().setAsmFramesComputationMode(COPY_FRAMES);
        });
    }

    public abstract static class RClassAsmTransformerFactory implements AsmClassVisitorFactory<RClassAsmTransformerFactory.Params> {

        public interface Params extends InstrumentationParameters {

            @Input
            Property<String> getNamespace();

            @Input
            @Optional
            ListProperty<String> getLibraryNamespaces();
        }

        @Override
        public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor classVisitor) {
            Params params = getParameters().get();
            String namespace = params.getNamespace().get();
            List<String> libraryNamespaces = params.getLibraryNamespaces().orElse(Collections.emptyList()).get();

            if (libraryNamespaces.isEmpty()) {
                return classVisitor;
            }

            String targetRClass = namespace.replace(".", "/") + "/R";
            String targetRSubclass = namespace.replace(".", "/") + "/R$";

            Set<String> libraryRClasses = libraryNamespaces.stream()
                    .map(it -> it.replace(".", "/") + "/R")
                    .collect(Collectors.toSet());
            List<String> libraryRSubclasses = libraryNamespaces.stream()
                    .map(it -> it.replace(".", "/") + "/R$")
                    .collect(Collectors.toList());

            Remapper remapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName == null) {
                        return null;
                    }
                    if (libraryRClasses.contains(internalName)) {
                        return targetRClass;
                    }
                    for (String libraryRSubclass : libraryRSubclasses) {
                        if (internalName.startsWith(libraryRSubclass)) {
                            return StringsKt.replaceFirst(internalName, libraryRSubclass, targetRSubclass, false);
                        }
                    }
                    return super.map(internalName);
                }
            };
            return new ClassRemapper(classVisitor, remapper);
        }

        @Override
        public boolean isInstrumentable(ClassData classData) {
            return true;
        }
    }
}
