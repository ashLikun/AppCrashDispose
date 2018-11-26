package com.ashlikun.appcrash;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/11/23　15:32
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：
 */
public abstract class CrashEventListener implements AppOnCrash.EventListener {
    @Override
    public void onLaunchErrorActivity() {

    }

    @Override
    public void onRestartAppFromErrorActivity() {

    }

    @Override
    public void onCloseAppFromErrorActivity() {

    }
}
