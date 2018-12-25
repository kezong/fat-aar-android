package com.kezong.fataar

/**
 * LogUtil
 * @author kezong @since 2018-12-10 17:28
 */
class LogUtil {

    def static logError(def msg) {
        println("【Fat-aar-ERROR】${msg}")
    }

    def static logInfo(def msg) {
        println("【Fat-aar-INFO】${msg}")
    }
}