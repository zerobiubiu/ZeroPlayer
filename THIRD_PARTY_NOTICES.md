# 第三方组件与许可证声明

ZeroPlayer 自身代码以 GPL-3.0-only 发布（见 [LICENSE](LICENSE)）。随 APK 分发的第三方组件如下；本文件是摘要，逐项条件以各上游许可正文为准。

## 静态链接进 `libplayer.so` 的播放栈

| 组件 | 版本 | 许可证 | 说明 |
|---|---|---|---|
| mpv（libmpv） | 0.41.0 | LGPL-2.1-or-later（`-Dgpl=false` 构建） | 关闭 GPL 源文件，未启用网络协议 |
| FFmpeg | 8.1 | LGPL-2.1-or-later（未启用 `--enable-gpl`/`--enable-version3`/`--enable-nonfree`） | 仅本地文件与 pipe |
| libplacebo | 7.360.1 | LGPL-2.1-or-later | 色彩管理与渲染 |
| libass | 0.17.4 | ISC | 字幕渲染 |
| FreeType | 2.14.3 | FreeType License / GPLv2 二选一 | 本项目按 FreeType License 使用 |
| HarfBuzz | 14.1.0 | MIT | 文字整形 |
| Fontconfig | 2.17.1 | MIT 类 | 字体发现 |
| FriBidi | 1.0.16 | LGPL-2.1-or-later | 双向文本 |
| libunibreak | 6.1 | zlib 类 | 换行与断词 |
| Expat | 2.10.0 | MIT | XML 解析 |

JNI 桥接代码来自 [jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android) v1.0.0（提交 `fcf6745703dc1265bca88f12fee8fc355ddf251e`）的 MIT 许可部分，本项目在其上修复了上下文与 Surface 生命周期、错误上报等问题，许可文本见 `tools/mpv-static/LICENSE.libmpv-android`。

## 二进制分发的义务

`libplayer.so` 把上述 LGPL 组件静态链接为单一动态库分发，因此发布 APK 时必须同时提供：

- 各组件对应的完整源码、所应用的修改与构建配置；
- 允许接收者替换 LGPL 库并重新链接所需的材料（JNI 目标文件、静态库与重新链接步骤）。

对应材料由 `tools/mpv-static/build.sh` 生成：脚本在工作区保留完整源码、配置、交叉工具链、JNI 对象文件与最终链接产物，并把各下载来源与 SHA-256 记录在 `tools/mpv-static/sources.tsv`。构建细节与逐项许可提示见 `tools/mpv-static/NOTICE.txt`。

本软件部分基于 FreeType 团队的工作（This software is based in part on the work of the FreeType Team）。

## 应用层依赖

Kotlin 标准库、AndroidX（Compose、Room、WorkManager、Media3 Session 等）、Hilt、Coil 等依赖均来自 Maven Central / Google Maven，遵循其各自的 Apache-2.0 等许可证；依赖版本见 `gradle/libs.versions.toml`。
