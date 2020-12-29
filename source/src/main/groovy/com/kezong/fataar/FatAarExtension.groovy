package com.kezong.fataar;

class FatAarExtension {

    /**
     * Used in RClassesTransform.java by reflection, don't change the name.
     */
    static final String NAME = "fataar"

    /**
     * Plan A: using bytecode patching to process the merging problem of R files
     * Plan B: generate sub module's R class to process the merging problem of R files
     * if transformR is true, use Plan A, else use Plan B.
     * In the future, Plan B maybe deprecated.
     *
     * Used in RClassesTransform.java by reflection, don't change the field name.
     * @since 1.3.0
     */
    boolean transformR = true

    /**
     * If transitive is true, local jar module and remote library's dependencies will be embed. (local aar module does not support)
     * If transitive is false, just embed first level dependency
     * Default value is false
     * @since 1.3.0
     */
    boolean transitive = false


    /**
     * support delete sub aar <declare-style/> , avoid bundle aar failed
     * */
    public HashMap<String, HashSet<String>> excludeDeclareStyleAttrs = new HashMap<>()

    /**
     * support delete sub aar <application/> attribute, avoid bundle aar failed
     * */
    public List<String> excludeApplicationAttr = new ArrayList<>()

    /**
     * support delete sub aar so, avoid bundle aar failed
     * */
    public List<String> abiFilter = new ArrayList<>()

    /**
     * support delete duplicate sub aar so, avoid bundle aar failed
     * */
    public HashMap<String, HashSet<String>> excludeSos = new HashMap<>()


    /**
     * support exclude classes, avoid class duplicated
     * */
    public List<String> excludeClasses = new ArrayList<>()


    /**
     * 特殊abi兼容
     *
     * 将v5的so替换为v7的so
     *
     * 为true时默认启用abiFilter=v7
     * */
    public boolean replaceV5WithV7So = false

}
