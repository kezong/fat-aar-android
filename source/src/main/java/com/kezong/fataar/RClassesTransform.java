package com.kezong.fataar;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import kotlin.io.FilesKt;

/**
 * com.sdk.R
 * |-- com.lib1.R
 * |-- com.lib2.R
 * <p>
 * rename com.lib1.R and com.lib2.R to com.sdk.R
 */
public class RClassesTransform extends Transform {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executor = Executors.newFixedThreadPool(CPU_COUNT + 1);

    private final List<Future<?>> futures = new ArrayList<>();

    private final Project project;

    private final Map<String, String> targetPackageMap = new HashMap<>();

    private final Map<String, Collection<String>> libraryPackageMap = new HashMap<>();

    private String resourcePrefix = null;

    private HashSet<String> renamedResources = null;

    public RClassesTransform(final Project project) {
        this.project = project;
    }

    /**
     * Different variants have different package names.
     * So targetPackageName must set after evaluate
     *
     * @param variantName   variant name
     * @param targetPackage main module's package name
     */
    public void putTargetPackage(String variantName, String targetPackage) {
        targetPackageMap.put(variantName, targetPackage);
    }

    public void putResourcesPrefix(String prefix) {
        this.resourcePrefix = prefix;
    }

    public void putRenamedResources(HashSet<String> renamedResources) {
        this.renamedResources = renamedResources;
    }

    /**
     * library packages name must set after exploded task perform
     *
     * @param variantName     variant name
     * @param libraryPackages sub module's package name, read from AndroidManifest.xml
     */
    public void putLibraryPackages(String variantName, Collection<String> libraryPackages) {
        libraryPackageMap.put(variantName, libraryPackages);
    }

    @Override
    public String getName() {
        return "transformR";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();
        Map<String, String> transformTable = buildTransformTable(transformInvocation.getContext().getVariantName());
        final boolean isIncremental = transformInvocation.isIncremental() && this.isIncremental();
        final TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        if (!isIncremental) {
            outputProvider.deleteAll();
        }

        final File outputDir = outputProvider.getContentLocation("classes", getOutputTypes(), getScopes(), Format.DIRECTORY);

        try {
            for (final TransformInput input : transformInvocation.getInputs()) {
                for (final DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    final File directoryFile = directoryInput.getFile();
                    final ClassPool classPool = new ClassPool();
                    classPool.insertClassPath(directoryFile.getAbsolutePath());

                    for (final File originalClassFile : getChangedClassesList(directoryInput)) {
                        if (!originalClassFile.getPath().endsWith(".class")) {
                            continue; // ignore anything that is not class file
                        }

                        Future<?> submit = executor.submit(() -> {
                            try {
                                File relative = FilesKt.relativeTo(originalClassFile, directoryFile);
                                String className = filePathToClassname(relative);
                                final CtClass ctClass = classPool.get(className);
                                if (transformTable != null) {
                                    ClassFile classFile = ctClass.getClassFile();
                                    ConstPool constPool = classFile.getConstPool();
                                    constPool.renameClass(transformTable);
                                    addPrefixForResourceReads(constPool, resourcePrefix, transformTable.values());
                                }

                                ctClass.writeFile(outputDir.getAbsolutePath());
                            } catch (CannotCompileException | NotFoundException | IOException e) {
                                e.printStackTrace();
                            }
                        });

                        futures.add(submit);
                    }
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }


        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        futures.clear();

        long endTime = System.currentTimeMillis();
        project.getLogger().info("the task cost "
                + (endTime - startTime)
                + "ms");
    }

    private void addPrefixForResourceReads(ConstPool constPool, String resourcePrefix, Collection<String> libRClasses) {

        if (resourcePrefix == null || resourcePrefix.isEmpty()) return;

        final List<String> resourceTypes = Arrays.asList("anim", "animator", "array", "bool", "color", "dimen",
                "drawable", "font", "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "navigation",
                "plurals", "raw", "string", "styleable", "transition", "xml", "style"); // except, attr

        for (int i = 1; i < constPool.getSize(); i++) {
            try {
                String constClassName = constPool.getFieldrefClassName(i).replace(".", "/");

                if (libRClasses.contains(constClassName)
                        && resourceTypes.contains(constClassName.split("\\$")[1])) {

                    String name = constPool.getFieldrefName(i);
                    String newName = resourcePrefix + name;
                    constPool.renameClass(name, newName);
                    int nameId = constPool.getNameAndTypeName(constPool.getFieldrefNameAndType(i));

                    try {
                        Method getItemMethod = constPool.getClass()
                                .getDeclaredMethod("getItem", int.class);
                        getItemMethod.setAccessible(true);

                        Object utf8Info = getItemMethod.invoke(constPool, nameId);

                        Field classPoolUtf8NameField = utf8Info.getClass()
                                .getDeclaredField("string");

                        classPoolUtf8NameField.setAccessible(true);
                        String classPoolResourceName = (String) classPoolUtf8NameField.get(utf8Info);

                        if (renamedResources.contains(classPoolResourceName)) {
                            classPoolUtf8NameField.set(utf8Info, newName);
                        }
                    } catch (IllegalAccessException
                            | InvocationTargetException
                            | NoSuchMethodException
                            | NoSuchFieldException e) {
                        e.printStackTrace();
                    }
                }
            } catch (ClassCastException e) {
                // ignore this
            }
        }
    }

    private Map<String, String> buildTransformTable(String variantName) {
        String targetPackage = targetPackageMap.get(variantName);
        Collection<String> libraryPackages = libraryPackageMap.get(variantName);
        if (targetPackage == null || libraryPackages == null) {
            return null;
        }

        final List<String> resourceTypes = Arrays.asList("anim", "animator", "array", "attr", "bool", "color", "dimen",
                "drawable", "font", "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "navigation",
                "plurals", "raw", "string", "style", "styleable", "transition", "xml");

        HashMap<String, String> map = new HashMap<>();
        for (String resource : resourceTypes) {
            String targetClass = targetPackageMap.get(variantName).replace(".", "/") + "/R$" + resource;
            for (String libraryPackage : libraryPackageMap.get(variantName)) {
                String fromClass = libraryPackage.replace(".", "/") + "/R$" + resource;
                map.put(fromClass, targetClass);
            }
        }

        return map;
    }

    private List<File> getChangedClassesList(final DirectoryInput directoryInput) throws IOException {
        final Map<File, Status> changedFiles = directoryInput.getChangedFiles();
        if (changedFiles.isEmpty()) {
            // we're in non incremental mode
            return Files.walk(directoryInput.getFile().toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } else {
            changedFiles.entrySet().stream()
                    .filter(it -> it.getValue() == Status.REMOVED)
                    .forEach(it -> it.getKey().delete());

            return changedFiles.entrySet().stream()
                    .filter(it -> it.getValue() == Status.ADDED || it.getValue() == Status.CHANGED)
                    .map(Map.Entry::getKey)
                    .filter(File::isFile)
                    .collect(Collectors.toList());
        }
    }

    private String filePathToClassname(File file) {
        // com/classify/module/a.class -> com.classify.module.a.class -> comify.module.a is not expected
        // so must be replace .class first
        return file.getPath().replace(".class", "")
                .replace("/", ".")
                .replace("\\", ".");
    }
}
