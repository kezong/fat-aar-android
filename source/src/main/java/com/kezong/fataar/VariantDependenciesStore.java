package com.kezong.fataar;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VariantDependenciesStore {

    private static final Map<String, Collection<AndroidArchiveLibrary>> store = new HashMap<>();

    public static void putLibraries(String variantName, Collection<AndroidArchiveLibrary> libraries) {
        store.put(variantName, libraries);
    }

    public static Collection<AndroidArchiveLibrary> getLibraries(String variantName) {
        return store.get(variantName);
    }

}
