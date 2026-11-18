import com.android.build.api.variant.LibraryAndroidComponentsExtension

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

// 这两个模块曾成功缓存不含 Hilt 注册信息的 KSP 输出。两份聚合标记分别供
// ActivityRetained / ViewModel 组件发现 key 和 binding；少一份就不能交给 app 打包。
mapOf(
    ":feature:settings" to "com.privateplayer.feature.settings.SettingsViewModel",
    ":feature:player" to "com.privateplayer.feature.player.PlayerViewModel",
).forEach { (projectPath, viewModel) ->
    val feature = project(projectPath)
    feature.pluginManager.withPlugin("com.android.library") {
        feature.extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
            val prefix = "_${viewModel.replace('.', '_')}_HiltModules"
            val markers = listOf("KeyModule", "BindsModule").associateWith { role ->
                feature.layout.buildDirectory.file("generated/ksp/${variant.name}/java/hilt_aggregated_deps/${prefix}_$role.java")
            }
            val producer = variant.computeTaskName("ksp", "Kotlin")
            val consumer = variant.computeTaskName("compile", "Kotlin")
            feature.tasks.matching { it.name == producer }.configureEach {
                // 新输入隔离旧缓存；生成不完整时任务失败，不会再发布坏缓存条目。
                inputs.property("requiredHiltViewModel", viewModel)
                doLast("verifyHiltViewModelBindings") {
                    for (marker in markers.values) {
                        val file = marker.get().asFile
                        check(file.isFile && file.length() > 0) { "Hilt 未生成 $viewModel 的注册标记：$file" }
                    }
                }
            }
            feature.tasks.matching { it.name == consumer }.configureEach {
                // FROM-CACHE / NO-SOURCE 不执行生产者的 doLast；编译输入也必须实际存在。
                markers.forEach { (role, marker) ->
                    inputs.file(marker).withPropertyName("hiltViewModel$role").withPathSensitivity(PathSensitivity.RELATIVE)
                }
            }
        }
    }
}
