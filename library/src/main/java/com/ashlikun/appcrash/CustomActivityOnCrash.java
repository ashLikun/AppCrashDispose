/*
 * Copyright 2014-2017 Eduard Ereza Martínez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ashlikun.appcrash;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class CustomActivityOnCrash {

    private final static String TAG = "CustomActivityOnCrash";

    //Extras passed to the error activity
    private static final String EXTRA_CONFIG = "com.ashlikun.appcrash.EXTRA_CONFIG";
    private static final String EXTRA_STACK_TRACE = "com.ashlikun.appcrash.EXTRA_STACK_TRACE";
    private static final String EXTRA_ACTIVITY_LOG = "com.ashlikun.appcrash.EXTRA_ACTIVITY_LOG";

    //General constants
    private static final String INTENT_ACTION_ERROR_ACTIVITY = "com.ashlikun.appcrash.ERROR";
    private static final String INTENT_ACTION_RESTART_ACTIVITY = "com.ashlikun.appcrash.RESTART";
    private static final int MAX_STACK_TRACE_SIZE = 131071; //128 KB - 1
    private static final int MAX_ACTIVITIES_IN_LOG = 50;
    private static final String CAOC_HANDLER_PACKAGE_NAME = "com.ashlikun.appcrash";

    //Internal variables
    private static Application application;
    private static AppCrashConfig config;
    private static Deque<String> activityLog = new ArrayDeque<>(MAX_ACTIVITIES_IN_LOG);
    private static WeakReference<Activity> lastActivityCreated = new WeakReference<>(null);
    private static Thread.UncaughtExceptionHandler oldHandler;

    /**
     * 安装
     */
    public static void install(Application app) {
        try {
            if (app != null) {
                final Thread.UncaughtExceptionHandler ool = Thread.getDefaultUncaughtExceptionHandler();
                if (ool != null && ool.getClass().getName().startsWith(CAOC_HANDLER_PACKAGE_NAME)) {
                    Log.e(TAG, "CustomActivityOnCrash was already installed, doing nothing!");
                    return;
                }
                oldHandler = ool;
                application = app;
                if (config.getBackgroundMode() == AppCrashConfig.BACKGROUND_MODE_NO_CRASH) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            //主线程异常拦截
                            while (true) {
                                try {
                                    //主线程的异常会从这里抛出
                                    Looper.loop();
                                } catch (Throwable e) {
                                    if (config.getEventListener() != null) {
                                        config.getEventListener().onNoCrashError(Looper.getMainLooper().getThread(), e);
                                    }
                                }
                            }
                        }
                    });
                }
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, final Throwable throwable) {
                        if (config.isEnabled()) {
                            Class<? extends Activity> errorActivityClass = config.getErrorActivityClass();
                            if (errorActivityClass == null) {
                                errorActivityClass = guessErrorActivityClass(application);
                            }
                            if (isStackTraceLikelyConflictive(throwable, errorActivityClass)) {
                                if (oldHandler != null) {
                                    oldHandler.uncaughtException(thread, throwable);
                                    return;
                                }
                            } else if (config.getBackgroundMode() == AppCrashConfig.BACKGROUND_MODE_NO_CRASH) {
                                if (config.getEventListener() != null) {
                                    config.getEventListener().onNoCrashError(thread, throwable);
                                }
                                return;
                            } else if (config.getBackgroundMode() == AppCrashConfig.BACKGROUND_MODE_SHOW_CUSTOM) {
                                final Intent intent = new Intent(application, errorActivityClass);
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                throwable.printStackTrace(pw);
                                String stackTraceString = sw.toString();

                                //Reduce data to 128KB so we don't get a TransactionTooLargeException when sending the intent.
                                //The limit is 1MB on Android but some devices seem to have it lower.
                                //See: http://developer.android.com/reference/android/os/TransactionTooLargeException.html
                                //And: http://stackoverflow.com/questions/11451393/what-to-do-on-transactiontoolargeexception#comment46697371_12809171
                                if (stackTraceString.length() > MAX_STACK_TRACE_SIZE) {
                                    String disclaimer = " [stack trace too large]";
                                    stackTraceString = stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length()) + disclaimer;
                                }
                                intent.putExtra(EXTRA_STACK_TRACE, stackTraceString);

                                if (config.isTrackActivities()) {
                                    String activityLogString = "";
                                    while (!activityLog.isEmpty()) {
                                        activityLogString += activityLog.poll();
                                    }
                                    intent.putExtra(EXTRA_ACTIVITY_LOG, activityLogString);
                                }
                                intent.putExtra(EXTRA_CONFIG, config);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                if (config.getEventListener() != null) {
                                    config.getEventListener().onLaunchErrorActivity();
                                }
                                application.startActivity(intent);
                            } else if (config.getBackgroundMode() == AppCrashConfig.BACKGROUND_MODE_CRASH) {
                                if (oldHandler != null) {
                                    oldHandler.uncaughtException(thread, throwable);
                                    return;
                                }
                            }
                            final Activity lastActivity = lastActivityCreated.get();
                            if (lastActivity != null) {
                                lastActivity.finish();
                                lastActivityCreated.clear();
                            }
                            killCurrentProcess();
                        } else if (oldHandler != null) {
                            oldHandler.uncaughtException(thread, throwable);
                        }
                    }
                });
                application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        if (activity.getClass() != config.getErrorActivityClass()) {
                            lastActivityCreated = new WeakReference<>(activity);
                        }
                        if (config.isTrackActivities()) {
                            activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " created\n");
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (config.isTrackActivities()) {
                            activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " resumed\n");
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (config.isTrackActivities()) {
                            activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " paused\n");
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                        //Do nothing
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (config.isTrackActivities()) {
                            activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " destroyed\n");
                        }
                    }
                });
            }
        } catch (Throwable t) {
        }
    }

    public static synchronized void uninstall() {
        //卸载后恢复默认的异常处理逻辑，否则主线程再次抛出异常后将导致ANR，并且无法捕获到异常位置
        if (oldHandler != null && oldHandler.getClass().getName().startsWith(CAOC_HANDLER_PACKAGE_NAME)) {
            Thread.setDefaultUncaughtExceptionHandler(oldHandler);
            if (config.getBackgroundMode() == AppCrashConfig.BACKGROUND_MODE_NO_CRASH) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        //主线程抛出异常，迫使 while (true) {}结束
                        throw new RuntimeException("Quit Cockroach.....");
                    }
                });
            }
        }
    }

    /**
     * Given an Intent, returns the stack trace extra from it.
     *
     * @param intent The Intent. Must not be null.
     * @return The stacktrace, or null if not provided.
     */

    public static String getStackTraceFromIntent(Intent intent) {
        return intent.getStringExtra(CustomActivityOnCrash.EXTRA_STACK_TRACE);
    }

    /**
     * Given an Intent, returns the config extra from it.
     *
     * @param intent The Intent. Must not be null.
     * @return The config, or null if not provided.
     */
    public static AppCrashConfig getConfigFromIntent(Intent intent) {
        return (AppCrashConfig) intent.getSerializableExtra(CustomActivityOnCrash.EXTRA_CONFIG);
    }

    /**
     * Given an Intent, returns the activity log extra from it.
     *
     * @param intent The Intent. Must not be null.
     * @return The activity log, or null if not provided.
     */
    public static String getActivityLogFromIntent(Intent intent) {
        return intent.getStringExtra(CustomActivityOnCrash.EXTRA_ACTIVITY_LOG);
    }

    /**
     * Given an Intent, returns several error details including the stack trace extra from the intent.
     *
     * @param context A valid context. Must not be null.
     * @param intent  The Intent. Must not be null.
     * @return The full error details.
     */
    public static String getAllErrorDetailsFromIntent(Context context, Intent intent) {
        //I don't think that this needs localization because it's a development string...

        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        //Get build date
        // String buildDateAsString = getBuildDateAsString(context, dateFormat);

        //Get app version
        String versionName = getVersionName(context);

        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("软件版本:");
        errorDetails.append(versionName);
        errorDetails.append("\n");
        errorDetails.append("当前时间:");
        errorDetails.append(dateFormat.format(currentDate));
        errorDetails.append("\n");
        //Added a space between line feeds to fix #18.
        //Ideally, we should not use this method at all... It is only formatted this way because of coupling with the default error activity.
        //We should move it to a method that returns a bean, and let anyone format it as they wish.
        errorDetails.append("设备:");
        errorDetails.append(getDeviceModelName());
        errorDetails.append("\n");
        errorDetails.append("\n");
        errorDetails.append("错误细节:  ");
        errorDetails.append("\n");
        errorDetails.append(getStackTraceFromIntent(intent));
        String activityLog = getActivityLogFromIntent(intent);
        if (activityLog != null) {
            errorDetails.append("\nUser actions: \n");
            errorDetails.append(activityLog);
        }
        return errorDetails.toString();
    }

    public static void restartApplication(Activity activity, AppCrashConfig config) {
        final Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        if (config.getEventListener() != null) {
            config.getEventListener().onRestartAppFromErrorActivity();
        }
    }


    /**
     * Closes the app.
     * If an event listener is provided, the close app event is invoked.
     * Must only be used from your error activity.
     *
     * @param activity The current error activity. Must not be null.
     * @param config   The config object as obtained by calling getConfigFromIntent.
     */
    public static void closeApplication(Activity activity, AppCrashConfig config) {
        if (config.getEventListener() != null) {
            config.getEventListener().onCloseAppFromErrorActivity();
        }
        activity.finish();
        killCurrentProcess();
    }

    /**
     * INTERNAL method that returns the current configuration of the library.
     * If you want to check the config, use CaocConfig.Builder.get();
     *
     * @return the current configuration
     */
    public static AppCrashConfig getConfig() {
        return config;
    }

    /**
     * INTERNAL method that sets the configuration of the library.
     * You must not use this, use CaocConfig.Builder.apply()
     *
     * @param config the configuration to use
     */
    public static void setConfig(AppCrashConfig config) {
        CustomActivityOnCrash.config = config;
        uninstall();
    }

    /**
     * INTERNAL method that checks if the stack trace that just crashed is conflictive. This is true in the following scenarios:
     * - The application has crashed while initializing (handleBindApplication is in the stack)
     * - The error activity has crashed (activityClass is in the stack)
     *
     * @param throwable     The throwable from which the stack trace will be checked
     * @param activityClass The activity class to launch when the app crashes
     * @return true if this stack trace is conflictive and the activity must not be launched, false otherwise
     */
    private static boolean isStackTraceLikelyConflictive(Throwable throwable, Class<? extends Activity> activityClass) {
        do {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if ((element.getClassName().equals("android.app.ActivityThread") && element.getMethodName().equals("handleBindApplication")) || element.getClassName().equals(activityClass.getName())) {
                    return true;
                }
            }
        } while ((throwable = throwable.getCause()) != null);
        return false;
    }

    /**
     * INTERNAL method that returns the build date of the current APK as a string, or null if unable to determine it.
     *
     * @param context    A valid context. Must not be null.
     * @param dateFormat DateFormat to use to convert from Date to String
     * @return The formatted date, or "Unknown" if unable to determine it.
     */

    private static String getBuildDateAsString(Context context, DateFormat dateFormat) {
        long buildDate;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            ZipFile zf = new ZipFile(ai.sourceDir);

            //If this failed, try with the old zip method
            ZipEntry ze = zf.getEntry("classes.dex");
            buildDate = ze.getTime();


            zf.close();
        } catch (Exception e) {
            buildDate = 0;
        }

        if (buildDate > 312764400000L) {
            return dateFormat.format(new Date(buildDate));
        } else {
            return null;
        }
    }

    /**
     * INTERNAL method that returns the version name of the current app, or null if unable to determine it.
     *
     * @param context A valid context. Must not be null.
     * @return The version name, or "Unknown if unable to determine it.
     */

    private static String getVersionName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * INTERNAL method that returns the device model name with correct capitalization.
     * Taken from: http://stackoverflow.com/a/12707479/1254846
     *
     * @return The device model name (i.e., "LGE Nexus 5")
     */

    private static String getDeviceModelName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    /**
     * INTERNAL method that capitalizes the first character of a string
     *
     * @param s The string to capitalize
     * @return The capitalized string
     */

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    /**
     * INTERNAL method used to guess which activity must be called from the error activity to restart the app.
     * It will first get activities from the AndroidManifest with intent filter <action android:name="com.ashlikun.appcrash.RESTART" />,
     * if it cannot find them, then it will get the default launcher.
     * If there is no default launcher, this returns null.
     *
     * @param context A valid context. Must not be null.
     * @return The guessed restart activity class, or null if no suitable one is found
     */

    private static Class<? extends Activity> guessRestartActivityClass(Context context) {
        Class<? extends Activity> resolvedActivityClass;

        //If action is defined, use that
        resolvedActivityClass = getRestartActivityClassWithIntentFilter(context);

        //Else, get the default launcher activity
        if (resolvedActivityClass == null) {
            resolvedActivityClass = getLauncherActivity(context);
        }

        return resolvedActivityClass;
    }

    /**
     * INTERNAL method used to get the first activity with an intent-filter <action android:name="com.ashlikun.appcrash.RESTART" />,
     * If there is no activity with that intent filter, this returns null.
     *
     * @param context A valid context. Must not be null.
     * @return A valid activity class, or null if no suitable one is found
     */
    @SuppressWarnings("unchecked")

    private static Class<? extends Activity> getRestartActivityClassWithIntentFilter(Context context) {
        Intent searchedIntent = new Intent().setAction(INTENT_ACTION_RESTART_ACTIVITY).setPackage(context.getPackageName());
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(searchedIntent,
                PackageManager.GET_RESOLVED_FILTER);

        if (resolveInfos != null && resolveInfos.size() > 0) {
            ResolveInfo resolveInfo = resolveInfos.get(0);
            try {
                return (Class<? extends Activity>) Class.forName(resolveInfo.activityInfo.name);
            } catch (ClassNotFoundException e) {
                //Should not happen, print it to the log!
                Log.e(TAG, "Failed when resolving the restart activity class via intent filter, stack trace follows!", e);
            }
        }

        return null;
    }

    /**
     * INTERNAL method used to get the default launcher activity for the app.
     * If there is no launchable activity, this returns null.
     *
     * @param context A valid context. Must not be null.
     * @return A valid activity class, or null if no suitable one is found
     */
    @SuppressWarnings("unchecked")

    private static Class<? extends Activity> getLauncherActivity(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            try {
                return (Class<? extends Activity>) Class.forName(intent.getComponent().getClassName());
            } catch (ClassNotFoundException e) {
                //Should not happen, print it to the log!
                Log.e(TAG, "Failed when resolving the restart activity class via getLaunchIntentForPackage, stack trace follows!", e);
            }
        }

        return null;
    }

    /**
     * INTERNAL method used to guess which error activity must be called when the app crashes.
     * It will first get activities from the AndroidManifest with intent filter <action android:name="com.ashlikun.appcrash.ERROR" />,
     * if it cannot find them, then it will use the default error activity.
     *
     * @param context A valid context. Must not be null.
     * @return The guessed error activity class, or the default error activity if not found
     */

    private static Class<? extends Activity> guessErrorActivityClass(Context context) {
        Class<? extends Activity> resolvedActivityClass;

        //If action is defined, use that
        resolvedActivityClass = getErrorActivityClassWithIntentFilter(context);

        //Else, get the default error activity
        if (resolvedActivityClass == null) {
            resolvedActivityClass = DefaultErrorActivity.class;
        }

        return resolvedActivityClass;
    }

    /**
     * INTERNAL method used to get the first activity with an intent-filter <action android:name="com.ashlikun.appcrash.ERROR" />,
     * If there is no activity with that intent filter, this returns null.
     *
     * @param context A valid context. Must not be null.
     * @return A valid activity class, or null if no suitable one is found
     */
    @SuppressWarnings("unchecked")

    private static Class<? extends Activity> getErrorActivityClassWithIntentFilter(Context context) {
        Intent searchedIntent = new Intent().setAction(INTENT_ACTION_ERROR_ACTIVITY).setPackage(context.getPackageName());
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(searchedIntent,
                PackageManager.GET_RESOLVED_FILTER);

        if (resolveInfos != null && resolveInfos.size() > 0) {
            ResolveInfo resolveInfo = resolveInfos.get(0);
            try {
                return (Class<? extends Activity>) Class.forName(resolveInfo.activityInfo.name);
            } catch (ClassNotFoundException e) {
                //Should not happen, print it to the log!
                Log.e(TAG, "Failed when resolving the error activity class via intent filter, stack trace follows!", e);
            }
        }

        return null;
    }

    /**
     * INTERNAL method that kills the current process.
     * It is used after restarting or killing the app.
     */
    private static void killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }


    public interface EventListener extends Serializable {
        /**
         * 当启动错误页面
         */
        void onLaunchErrorActivity();

        /**
         * 当重新启动
         */
        void onRestartAppFromErrorActivity();

        /**
         * 当关闭
         */
        void onCloseAppFromErrorActivity();

        /**
         * 当不启动错误页面时候的异常
         */
        void onNoCrashError(Thread t, Throwable e);
    }
}
