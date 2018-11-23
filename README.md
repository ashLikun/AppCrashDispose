[![Release](https://jitpack.io/v/ashLikun/AppCrashDispose.svg)](https://jitpack.io/#ashLikun/AppCrashDispose)


AppCrashDispose项目简介
    app异常处理
    1：调试版本使用错误页面提示
    2：线上使用永不crash
## 使用方法

build.gradle文件中添加:
```gradle
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```
并且:

```gradle
    dependencies {
        implementation 'com.github.ashLikun:AppCrashDispose:{latest version}'
     }
```
### 2.API
    AppCrashConfig.Builder.create(this)
                    .enabled(true) //default: true
                    .showRestartButton(true) //default: true
                    .trackActivities(true) //default: false
                    .minTimeBetweenCrashesMs(2000) //default: 3000
                    .isDebug(false)
                    .apply();


