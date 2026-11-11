# Room / Hilt / Compose 的默认规则由各自插件的 consumer rules 提供。
# 本项目第一阶段未开启混淆；开启时需保留 Room 生成的实现类与 Hilt 绑定。
-keep class com.privateplayer.core.database.**_Impl { *; }
