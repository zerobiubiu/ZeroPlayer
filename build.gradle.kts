plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * AGP 9.5 的内置 Kotlin 使用 KGP 2.2.10，而部分第三方库（如 Coil）会引入
 * 更高版本的 kotlin-stdlib，其元数据版本超出 2.2 编译器可读范围。
 * 统一把 stdlib 固定到编译器版本，避免 "incompatible version of Kotlin" 编译失败。
 */
allprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:${libs.versions.kotlin.get()}")
        }
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}
