package com.kezong.fataar

import org.gradle.api.Project

import java.lang.ref.WeakReference

/**
 * Utils
 * @author kezong @since 2018-12-10 17:28
 */
class Utils {

    private static WeakReference<Project> mProjectRef

    def static setProject(Project p) {
        mProjectRef = new WeakReference<>(p)
    }

    def static logError(def msg) {
        Project p = mProjectRef.get()
        if (p != null) {
            p.logger.error("[fat-aar]${msg}")
        }
    }

    def static logInfo(def msg) {
        Project p = mProjectRef.get()
        if (p != null) {
            p.logger.info("[fat-aar]${msg}")
        }
    }

    def static logAnytime(def msg) {
        Project p = mProjectRef.get()
        if (p != null) {
            p.println("[fat-aar]${msg}")
        }
    }

    static int compareVersion(String v1, String v2) {
        if (v1.equals(v2)) {
            return 0
        }
        String[] version1Array = v1.split("[._]")
        String[] version2Array = v2.split("[._]")
        int index = 0
        int minLen = Math.min(version1Array.length, version2Array.length)
        long diff = 0

        while (index < minLen
                && (diff = Long.parseLong(version1Array[index])
                - Long.parseLong(version2Array[index])) == 0) {
            index++
        }
        if (diff == 0) {
            for (int i = index; i < version1Array.length; i++) {
                if (Long.parseLong(version1Array[i]) > 0) {
                    return 1
                }
            }

            for (int i = index; i < version2Array.length; i++) {
                if (Long.parseLong(version2Array[i]) > 0) {
                    return -1
                }
            }
            return 0
        } else {
            return diff > 0 ? 1 : -1
        }
    }
}