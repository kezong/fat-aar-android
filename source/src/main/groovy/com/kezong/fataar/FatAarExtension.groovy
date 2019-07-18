package com.kezong.fataar

import org.gradle.api.Project

/**
 * build.gradle可配置的DSL参数
 *
 * @author zhoujian
 */
class FatAarExtension {
    private Project project

    /**
     * 不处理R.txt的依赖列表
     */
    String excludedRLibraries = ""

    FatAarExtension() {}

    FatAarExtension(Project project) {
        this.project = project
    }

    String getExcludedRLibraries() {
        return excludedRLibraries
    }

}
