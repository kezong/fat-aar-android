package com.kezong.fataar;

class FatAarExtension {

    static final String NAME = "fataar"

    /**
     * Plan A: using bytecode patching to process the merging problem of R files
     * Plan B: generate sub module's R class to process the merging problem of R files
     * if transformR is true, use Plan A, else use Plan B.
     * In the future, Plan B maybe deprecated
     */
    boolean transformR = true
}
