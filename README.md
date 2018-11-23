# **AndroidEventBus**
    android 异常捕获

### 1.用法
使用前，对于Android Studio的用户，可以选择添加:
    
	compile 'com.github.ashLikun.AppCrashDispose:0.0.1'

### 2.API
    AppCrashConfig.Builder.create(this)
                    .enabled(true) //default: true
                    .showRestartButton(true) //default: true
                    .trackActivities(true) //default: false
                    .minTimeBetweenCrashesMs(2000) //default: 3000
                    .isDebug(false)
                    .apply();


