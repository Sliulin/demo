/**
 * 根项目插件声明，具体 Android 配置集中在 app 模块。
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
