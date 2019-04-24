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
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;


public class AppCrashConfig implements Serializable {

    @IntDef({BACKGROUND_MODE_CRASH, BACKGROUND_MODE_SHOW_CUSTOM, BACKGROUND_MODE_NO_CRASH})
    @Retention(RetentionPolicy.SOURCE)
    private @interface BackgroundMode {
    }

    /**
     * 永不异常,正式环境
     */
    public static final int BACKGROUND_MODE_NO_CRASH = 0;
    /**
     * 显示错误的界面
     */
    public static final int BACKGROUND_MODE_SHOW_CUSTOM = 1;
    /**
     * 正常异常退出
     */
    public static final int BACKGROUND_MODE_CRASH = 2;
    private int backgroundMode = BACKGROUND_MODE_SHOW_CUSTOM;
    private boolean enabled = true;
    private boolean showRestartButton = true;
    private boolean trackActivities = false;
    private int minTimeBetweenCrashesMs = 3000;
    private Integer errorDrawable = null;
    private Class<? extends Activity> errorActivityClass = null;
    private AppOnCrash.EventListener eventListener = null;

    @BackgroundMode
    public int getBackgroundMode() {
        return backgroundMode;
    }

    public void setBackgroundMode(@BackgroundMode int backgroundMode) {
        this.backgroundMode = backgroundMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    public boolean isShowRestartButton() {
        return showRestartButton;
    }

    public void setShowRestartButton(boolean showRestartButton) {
        this.showRestartButton = showRestartButton;
    }

    public boolean isTrackActivities() {
        return trackActivities;
    }

    public void setTrackActivities(boolean trackActivities) {
        this.trackActivities = trackActivities;
    }

    public int getMinTimeBetweenCrashesMs() {
        return minTimeBetweenCrashesMs;
    }

    public void setMinTimeBetweenCrashesMs(int minTimeBetweenCrashesMs) {
        this.minTimeBetweenCrashesMs = minTimeBetweenCrashesMs;
    }


    @DrawableRes
    public Integer getErrorDrawable() {
        return errorDrawable;
    }

    public void setErrorDrawable(@DrawableRes Integer errorDrawable) {
        this.errorDrawable = errorDrawable;
    }


    public Class<? extends Activity> getErrorActivityClass() {
        return errorActivityClass;
    }

    public void setErrorActivityClass(Class<? extends Activity> errorActivityClass) {
        this.errorActivityClass = errorActivityClass;
    }


    public AppOnCrash.EventListener getEventListener() {
        return eventListener;
    }

    public void setEventListener(AppOnCrash.EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public static class Builder {
        private AppCrashConfig config;
        Application application;

        private Builder(Application application) {
            this.application = application;
        }


        public static Builder create(Application application) {
            Builder builder = new Builder(application);
            AppCrashConfig currentConfig = AppOnCrash.getConfig();
            AppCrashConfig config = new AppCrashConfig();
            if (currentConfig != null) {
                config.backgroundMode = currentConfig.backgroundMode;
                config.enabled = currentConfig.enabled;
                config.showRestartButton = currentConfig.showRestartButton;
                config.trackActivities = currentConfig.trackActivities;
                config.minTimeBetweenCrashesMs = currentConfig.minTimeBetweenCrashesMs;
                config.errorDrawable = currentConfig.errorDrawable;
                config.errorActivityClass = currentConfig.errorActivityClass;
                config.eventListener = currentConfig.eventListener;
            }
            builder.config = config;
            return builder;
        }

        /**
         * Defines if the error activity must be launched when the app is on background.
         * BackgroundMode.BACKGROUND_MODE_SHOW_CUSTOM: launch the error activity when the app is in background,
         * BackgroundMode.BACKGROUND_MODE_CRASH: launch the default system error when the app is in background,
         * BackgroundMode.BACKGROUND_MODE_SILENT: crash silently when the app is in background,
         * The default is BackgroundMode.BACKGROUND_MODE_SHOW_CUSTOM (the app will be brought to front when a crash occurs).
         */

        public Builder backgroundMode(@BackgroundMode int backgroundMode) {
            config.backgroundMode = backgroundMode;
            return this;
        }

        /**
         * Defines if CustomActivityOnCrash crash interception mechanism is enabled.
         * Set it to true if you want CustomActivityOnCrash to intercept crashes,
         * false if you want them to be treated as if the library was not installed.
         * The default is true.
         */

        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        /**
         * Defines if the error activity should show a restart button.
         * Set it to true if you want to show a restart button,
         * false if you want to show a close button.
         * Note that even if restart is enabled but you app does not have any launcher activities,
         * a close button will still be used by the default error activity.
         * The default is true.
         */

        public Builder showRestartButton(boolean showRestartButton) {
            config.showRestartButton = showRestartButton;
            return this;
        }

        /**
         * Defines if the activities visited by the user should be tracked
         * so they are reported when an error occurs.
         * The default is false.
         */

        public Builder trackActivities(boolean trackActivities) {
            config.trackActivities = trackActivities;
            return this;
        }

        /**
         * Defines the time that must pass between app crashes to determine that we are not
         * in a crash loop. If a crash has occurred less that this time ago,
         * the error activity will not be launched and the system crash screen will be invoked.
         * The default is 3000.
         */

        public Builder minTimeBetweenCrashesMs(int minTimeBetweenCrashesMs) {
            config.minTimeBetweenCrashesMs = minTimeBetweenCrashesMs;
            return this;
        }

        /**
         * Defines which drawable to use in the default error activity image.
         * Set this if you want to use an image other than the default one.
         * The default is R.drawable.customactivityoncrash_error_image (a cute upside-down bug).
         */

        public Builder errorDrawable(@DrawableRes Integer errorDrawable) {
            config.errorDrawable = errorDrawable;
            return this;
        }

        /**
         * Sets the error activity class to launch when a crash occurs.
         * If null, the default error activity will be used.
         */

        public Builder errorActivity(Class<? extends Activity> errorActivityClass) {
            config.errorActivityClass = errorActivityClass;
            return this;
        }


        /**
         * Sets an event listener to be called when events occur, so they can be reported
         * by the app as, for example, Google Analytics events.
         * If not set or set to null, no events will be reported.
         *
         * @param eventListener The event listener.
         * @throws IllegalArgumentException if the eventListener is an inner or anonymous class
         */

        public Builder eventListener(AppOnCrash.EventListener eventListener) {
            if (eventListener != null && eventListener.getClass().getEnclosingClass() != null && !Modifier.isStatic(eventListener.getClass().getModifiers())) {
                throw new IllegalArgumentException("The event listener cannot be an inner or anonymous class, because it will need to be serialized. Change it to a class of its own, or make it a static inner class.");
            } else {
                config.eventListener = eventListener;
            }
            return this;
        }

        /**
         * 设置调试模式，如果是正式版，就永远没有退出
         *
         * @param isDebug
         * @return
         */
        public Builder isDebug(boolean isDebug) {
            if (!isDebug) {
                config.backgroundMode = AppCrashConfig.BACKGROUND_MODE_NO_CRASH;
            } else {
                config.backgroundMode = AppCrashConfig.BACKGROUND_MODE_SHOW_CUSTOM;
            }
            return this;
        }


        public AppCrashConfig get() {
            return config;
        }

        public void apply() {
            AppOnCrash.setConfig(config);
            AppOnCrash.install(application);
        }


    }


}
