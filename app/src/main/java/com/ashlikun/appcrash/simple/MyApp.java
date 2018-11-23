package com.ashlikun.appcrash.simple;

import android.app.Application;

import com.ashlikun.appcrash.AppCrashConfig;

/**
 * 作者　　: 李坤
 * 创建时间: 2017/11/7　17:43
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCrashConfig.Builder.create(this)
                .enabled(true) //default: true
                .showRestartButton(true) //default: true
                .trackActivities(true) //default: false
                .minTimeBetweenCrashesMs(2000) //default: 3000
                .isDebug(false)
                .apply();
    }
}
