package com.kezong.fataar

import org.gradle.api.Project

class FatUtils {

    private static Project sProject

    def static attach(Project p) {
        sProject = p
    }

    def static logError(def msg) {
        sProject.logger.error("[fat-aar]${msg}")
    }

    def static logInfo(def msg) {
        sProject.logger.info("[fat-aar]${msg}")
    }

    def static logAnytime(def msg) {
        sProject.println("[fat-aar]${msg}")
    }

    def static showDir(int indent, File file) throws IOException {
        for (int i = 0; i < indent; i++)
            System.out.print('-')
        println(file.getName() + " " + file.size())
        if (file.isDirectory()) {
            File[] files = file.listFiles()
            for (int i = 0; i < files.length; i++)
                showDir(indent + 4, files[i])
        }
    }

    static void deleteEmptyDir(final File file) {
        file.listFiles().each { x ->
            if (x.isDirectory()) {
                if (x.listFiles().size() == 0) {
                    x.delete()
                } else {
                    deleteEmptyDir(x)
                    if (x.listFiles().size() == 0) {
                        x.delete()
                    }
                }
            }
        }
    }

    static int compareVersion(String v1, String v2) {
        if (v1.equals(v2)) {
            return 0
        }

        String[] version1 = v1.split("-")
        String[] version2 = v2.split("-")
        String[] version1Array = version1[0].split("[._]")
        String[] version2Array = version2[0].split("[._]")

        String preRelease1 = new String()
        String preRelease2 = new String()
        if (version1.length > 1) {
            preRelease1 = version1[1]
        }
        if (version2.length > 1) {
            preRelease2 = version2[1]
        }

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
            //compare pre-release
            if (!preRelease1.isEmpty() && preRelease2.isEmpty()) {
                return -1
            } else if (preRelease1.isEmpty() && !preRelease2.isEmpty()) {
                return 1
            } else if (!preRelease1.isEmpty() && !preRelease2.isEmpty()) {
                int preReleaseDiff = preRelease1.compareTo(preRelease2);
                if (preReleaseDiff > 0) {
                    return 1
                } else if (preReleaseDiff < 0) {
                    return -1
                }
            }
            return 0
        } else {
            return diff > 0 ? 1 : -1
        }
    }

    static String formatDataSize(long size) {
        String result
        if (size < 1024) {
            result = size + "Byte"
        } else if (size < (1024 * 1024)) {
            result = String.format("%.0fK", size / 1024)
        } else if (size < 1024 * 1024 * 1024) {
            result = String.format("%.2fM", size / (1024 * 1024.0))
        } else {
            result = String.format("%.2fG", size / (1024 * 1024 * 1024.0))
        }
        return result
    }

    def static mergeFiles(Collection<File> inputFiles, File output) {
        if (inputFiles == null) {
            return
        }
        // filter out any non-existent files
        Collection<File> existingFiles = inputFiles.findAll { it ->
            it.exists()
        }

        // no input? done.
        if (existingFiles.isEmpty()) {
            return
        }

        if (!output.exists()) {
            output.createNewFile()
        }

        // otherwise put all the files together append to output file
        for (File file in existingFiles) {
            output.append("\n${file.getText("UTF-8")}\n")
        }
    }
}