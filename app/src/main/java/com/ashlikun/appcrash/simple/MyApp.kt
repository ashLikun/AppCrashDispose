package com.ashlikun.appcrash.simple

import android.app.Application
import com.ashlikun.appcrash.AppCrashConfig

/**
 * 作者　　: 李坤
 * 创建时间: 2017/11/7　17:43
 * 邮箱　　：496546144@qq.com
 *
 *
 * 功能介绍：
 */
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCrashConfig.Builder.create(this)
            .eventListener(AppCrashEventListener())
            .enabled(true) //default: true
            .backgroundMode(AppCrashConfig.BACKGROUND_MODE_NO_CRASH)
            .showRestartButton(true) //default: true
            .trackActivities(true) //default: false
            .minTimeBetweenCrashesMs(2000) //default: 3000
            .apply()
    }
}