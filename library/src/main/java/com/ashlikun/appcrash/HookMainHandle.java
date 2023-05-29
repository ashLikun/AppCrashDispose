package com.ashlikun.appcrash;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ashlikun.appcrash.compat.ActivityKillerV15_V20;
import com.ashlikun.appcrash.compat.ActivityKillerV21_V23;
import com.ashlikun.appcrash.compat.ActivityKillerV24_V25;
import com.ashlikun.appcrash.compat.ActivityKillerV26;
import com.ashlikun.appcrash.compat.ActivityKillerV28;
import com.ashlikun.appcrash.compat.IActivityKiller;

import java.lang.reflect.Field;

import me.weishu.reflection.Reflection;

/**
 * 作者　　: 李坤
 * 创建时间: 2022/9/7　14:30
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：Hook main线程，异常接收当前页面
 */
class HookMainHandle {
    private static IActivityKiller sActivityKiller;
    private static boolean sIsSafeMode;
    private static boolean isHookHOk = false;

    /**
     * 替换ActivityThread.mH.mCallback，实现拦截Activity生命周期，直接忽略生命周期的异常的话会导致黑屏，目前
     * 会调用ActivityManager的finishActivity结束掉生命周期抛出异常的Activity
     */
    static void initActivityKiller(Application app) {
        //解除 android P 反射限制
        try {
            Reflection.unseal(app);
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }
        //各版本android的ActivityManager获取方式，finishActivity的参数，token(binder对象)的获取不一样
        if (Build.VERSION.SDK_INT >= 28) {
            sActivityKiller = new ActivityKillerV28();
        } else if (Build.VERSION.SDK_INT >= 26) {
            sActivityKiller = new ActivityKillerV26();
        } else if (Build.VERSION.SDK_INT == 25 || Build.VERSION.SDK_INT == 24) {
            sActivityKiller = new ActivityKillerV24_V25();
        } else if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT <= 23) {
            sActivityKiller = new ActivityKillerV21_V23();
        } else if (Build.VERSION.SDK_INT >= 15 && Build.VERSION.SDK_INT <= 20) {
            sActivityKiller = new ActivityKillerV15_V20();
        } else if (Build.VERSION.SDK_INT < 15) {
            sActivityKiller = new ActivityKillerV15_V20();
        }

        try {
            hookmH();
            isHookHOk = true;
        } catch (Throwable e) {
            e.printStackTrace();
            isHookHOk = false;
        }
    }

    private static void hookmH() throws Exception {

        final int LAUNCH_ACTIVITY = 100;
        final int PAUSE_ACTIVITY = 101;
        final int PAUSE_ACTIVITY_FINISHING = 102;
        final int STOP_ACTIVITY_HIDE = 104;
        final int RESUME_ACTIVITY = 107;
        final int DESTROY_ACTIVITY = 109;
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null);

        Field mhField = activityThreadClass.getDeclaredField("mH");
        mhField.setAccessible(true);
        final Handler mhHandler = (Handler) mhField.get(activityThread);
        Field callbackField = Handler.class.getDeclaredField("mCallback");
        callbackField.setAccessible(true);
        callbackField.set(mhHandler, new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (Build.VERSION.SDK_INT >= 28) {//android P 生命周期全部走这
                    final int EXECUTE_TRANSACTION = 159;
                    if (msg.what == EXECUTE_TRANSACTION) {
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishLaunchActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    }
                    return false;
                }
                switch (msg.what) {
                    case LAUNCH_ACTIVITY:// startActivity--> activity.attach  activity.onCreate  r.activity!=null  activity.onStart  activity.onResume
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishLaunchActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    case RESUME_ACTIVITY://回到activity onRestart onStart onResume
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishResumeActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    case PAUSE_ACTIVITY_FINISHING://按返回键 onPause
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishPauseActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    case PAUSE_ACTIVITY://开启新页面时，旧页面执行 activity.onPause
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishPauseActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    case STOP_ACTIVITY_HIDE://开启新页面时，旧页面执行 activity.onStop
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            sActivityKiller.finishStopActivity(msg);
                            notifyException(throwable);
                        }
                        return true;
                    case DESTROY_ACTIVITY:// 关闭activity onStop  onDestroy
                        try {
                            mhHandler.handleMessage(msg);
                        } catch (Throwable throwable) {
                            notifyException(throwable);
                        }
                        return true;
                }
                return false;
            }
        });
    }


    private static void notifyException(Throwable throwable) {
        AppOnCrash.startErrorAcctivity(throwable);
        if (AppOnCrash.getConfig().getEventListener() == null) {
            return;
        }
        if (isSafeMode()) {
            AppOnCrash.getConfig().getEventListener().bandageExceptionHappened(throwable);
        } else {
            AppOnCrash.getConfig().getEventListener().uncaughtExceptionHappened(Looper.getMainLooper().getThread(), throwable);
            safeMode();
        }
    }

    public static boolean isSafeMode() {
        return sIsSafeMode;
    }
    public static boolean isHookHOk() {
        return isHookHOk;
    }

    static void safeMode() {
        if (sIsSafeMode) {
            return;
        }
        sIsSafeMode = true;
        if (AppOnCrash.getConfig().getEventListener() != null) {
            AppOnCrash.getConfig().getEventListener().enterSafeMode();
        }
        while (true) {
            try {
                Looper.loop();
            } catch (Throwable e) {
                //只要进入安全模式，那么以后异常就会进入这个方法
                e.printStackTrace();
                if (AppOnCrash.getConfig().getEventListener() != null) {
                    AppOnCrash.getConfig().getEventListener().bandageExceptionHappened(e);
                }
                if (HookMainHandle.isChoreographerException(e)) {
                    //重启APP
                    AppOnCrash.restarteApp();
                    break;
                }
            }
        }
        sIsSafeMode = false;
    }

    /**
     * view measure layout draw时抛出异常会导致Choreographer挂掉
     * <p>
     * 建议直接杀死app。以后的版本会只关闭黑屏的Activity
     *
     * @param e
     */
    static boolean isChoreographerException(Throwable e) {
        if (e == null) {
            return false;
        }
        StackTraceElement[] elements = e.getStackTrace();
        if (elements == null) {
            return false;
        }

        for (int i = elements.length - 1; i > -1; i--) {
            if (elements.length - i > 20) {
                return false;
            }
            StackTraceElement element = elements[i];
            if ("android.view.Choreographer".equals(element.getClassName())
                    && "Choreographer.java".equals(element.getFileName())
                    && "doFrame".equals(element.getMethodName())) {
//                AppOnCrash.getConfig().getEventListener().mayBeBlackScreen(e);
                return true;
            }

        }
        return false;
    }
}
