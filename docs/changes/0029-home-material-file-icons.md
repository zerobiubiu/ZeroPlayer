# 首页按文件名显示 Material Icon Theme 图标

状态：图标集、实现和离线来源校验已完成。影响范围：`feature/home` 首页文件行的普通态前导图形，以及 APK 已有的第三方许可声明；不改变文件类型判定、打开方式、导入、存储或数据库。随同批 1.2.0 交付，本项不单独升版本。

## 正式来源与许可

使用 [VS Code Marketplace 的 PKief.material-icon-theme](https://marketplace.visualstudio.com/items?itemName=PKief.material-icon-theme) **5.40.0**，作者是 [Material Extensions](https://github.com/material-extensions/vscode-material-icon-theme)。从本机 VS Code 已缓存的官方 VSIX 读取原始字节：`extension.vsixmanifest` 的 `Publisher/Id/Version` 是 `PKief/material-icon-theme/5.40.0`；包内 `extension/package.json` 的主题入口为 `./dist/material-icons.json`，声明 MIT，并指回该组织的仓库。已安装副本的 manifest、许可及本次用到的全部原始 SVG 与该 VSIX 一致。未安装包、执行扩展脚本或上传任何文件名、媒体和路径。

| 校验对象 | SHA-256 |
|---|---|
| 原始 Marketplace VSIX（893,199 字节） | `55b565f200881abfd807baf9be52fce45fd52fef013ba71b0f8394984ce1ae06` |
| `extension/dist/material-icons.json`（453,305 字节） | `4b43c31b9e54d99ac14afb36027cf1699167dc56fc33f524dcb0ef112fcd65c2` |
| `extension/LICENSE.txt` | `cdab3014d4f69b49dde2b85e81792208c72de613aa6aed7f7a9b5c6609b89670` |
| 636 个用到的上游 SVG 集合（共 526,894 字节） | `4720d27c2654e04ee35daea5cb1d96b86c3feb04992eb4c113ba60c3d0e75d33` |

集合摘要按原始 SVG 文件名升序，依次写入 UTF-8 文件名、一个 NUL、该文件 SHA-256 的 32 个原始字节，再整体计算 SHA-256。实际发行许可为 **The MIT License (MIT)，Copyright (c) 2025 Material Extensions**。没有借用近似图形或其他同名图标库的授权。原文逐字节保留在 [APK 的 NOTICE](../../app/src/main/assets/third-party-licenses/NOTICE.txt)，原有声明不变；应用内「设置 → 关于 → 开源许可」与仓库 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) 均可查。

## 映射与界面

- 读取真实 manifest 的默认 `fileNames` **2,156 条**（其中 204 条含直接父目录）、`fileExtensions` **1,391 条**，以及浅色主题实际覆盖的 179 条专名、32 条后缀。589 个默认图形（含 `file`）和 47 个有效浅色图形全部保留；不导入目录、展开态、可选包、扩展程序、编辑器语言检测或未引用图形。
- [解析器](../../feature/home/src/main/java/com/privateplayer/feature/home/fileicons/MaterialFileIcon.kt)使用 `FileTreeEntry.name`（包含旧 UUID 媒体的展示名），按与设备语言无关的小写匹配。直接父目录限定的专名优先（如 `.github/FUNDING.yml`），再用普通专名（`Dockerfile`、`package.json`、`README.md`、`.gitignore`），最后从长到短试后缀（`index.d.ts` 优先于 `.ts`）。不把无点的 `PDF` 当作 `.pdf`。没有命中时，已有 `isVideo` 标记的文件仍用 `video`，其余用上游 `file`。
- 上游本身已把 `.srt/.ass/.ssa/.vtt/.sub/.ttml` 映射到 `subtitles`；另填其空白 `.smi/.sami → subtitles`、`.aab → android`，只用于显示，不声称扩充媒体播放或挂载字幕的能力。音频、图片、PDF、Office、压缩档、代码及配置按上游直接匹配；例如 `.mp3 → audio`、`.pdf → pdf` 看形状也可区分。
- [首页文件行](../../feature/home/src/main/java/com/privateplayer/feature/home/FileTreeRow.kt)用未染色 `Image` 以 **24dp** 展示，保留原图的外形、渐变、色彩与透明度。浅色覆盖跟随**应用实际主题**，不依赖系统是否处于夜间模式。文件名不变，图形不单独获得读屏焦点；目录仍用原文件夹图标，多选仍用原勾选/空圈。未改按身份选择/删除的代码、长按或菜单动作。
- [生成的索引](../../feature/home/src/main/java/com/privateplayer/feature/home/fileicons/MaterialIconAssociations.kt)将专名、后缀各拆成 16 个哈希桶，最大桶 150 个键；按字符串匹配，哈希碰撞不会误认文件。只在该行的条目或主题变化时求值。上游后缀最长 25 个 UTF-16 单元、最多 3 段；不逐行扫描大表、不创建全量 Map、反射找资源、读取文件内容或加载 manifest。

## 再生成与体积

正式 [图标资源](../../feature/home/src/main/res/drawable-xxxhdpi/) 与关联索引归 `feature/home`。维护时使用[离线生成器](../../tools/generate-home-file-icons.cjs)：输入为上述未定制发行包解出的 `extension` 目录（或字节相同的 VS Code 扩展目录），从 `iconDefinitions.iconPath` 找真正的 SVG。工具检查包身份、manifest、图形集合与 APK 声明中的许可原文；从原始 viewBox 直接渲染 96×96 像素、保留透明度，编码为无损 WebP（xxxhdpi 的 24dp）。Gradle 构建和应用运行都不调用这个工具，不需要 Node、VSIX、网络或额外 Android 依赖。

维护环境：Windows x64，Node 26.13.0，使用已具备的 sharp 0.35.5（librsvg 2.63.2、libvips 8.18.7、libwebp 1.6.0）。`NODE_PATH` 可指向现有依赖目录，不需要 `npm install`。首次转换及只读重放的命令分别是：

```text
node tools/generate-home-file-icons.cjs <扩展目录>
node tools/generate-home-file-icons.cjs <扩展目录> --check
```

实际生成并重放：**636 张无损 WebP 共 663,708 B（648.15 KiB）**；Kotlin 关联表源码 **171,081 B（167.07 KiB）**，编译进 DEX，不随 APK 原样打包。两个数字分别代表资源载荷和源码体积，不是 APK 整体增量。`--check` 对这 636 张图和整份索引逐字节相等；额外独立按原始 VSIX 的默认及浅色关联逐项比对，**7,094** 次上游匹配、**6** 次自补后缀匹配、**204** 个父目录不匹配的反例全部一致；没有打包无关联图形，NOTICE 中许可原文恰好一份。

## 应用验证

[MaterialFileIconTest](../../feature/home/src/test/java/com/privateplayer/feature/home/MaterialFileIconTest.kt) 包含 5 组用例：常见导入类型与字幕、专名/复合后缀、父目录和旧展示名、未知/无扩展名影片、浅深主题与大小写。上面的重放只验证静态素材与关联；不能替代 JVM、APK 或实际文件列表的验收。

1.2.0 的离线合并中 `:feature:home:testDebugUnitTest` 实际执行；该类 5 项全过、无跳过。读取已安装调试 APK 对应的 ZIP：636 个 `res/drawable-xxxhdpi*/home_mit_*.webp`，合计 663,708 B（ZIP 内存储 663,708 B），`assets/third-party-licenses/NOTICE.txt` 4,499 B。不是用源码文件数量代替 APK 结果。

用同一冷装 APK 的 `File-icons` 临时文件夹验证，真实写入 MKV、MP3、SRT、PNG、PDF、ZIP、JSON 及一份 `.zpx`，没有给首页组件注入图片。[实际普通态](../ui-ux-audit/implementation/evidence/home-material-files-1.2.0.png)：在 1080×2400、字号 1.3 下八行同屏；从上到下可见橙色场记板、红色音符、橙色字幕框、青色图片、红色 PDF、绿色拉链、橙色花括号和灰色通用文件。物理长按 `05-report.pdf` 750 ms 后，[八行改回多选图形](../ui-ux-audit/implementation/evidence/home-material-selected-1.2.0.png)，顶栏为「已选择 1 项」；8 个 checkable 行中仅 PDF 行 `[0,1003][1080,1196]` 为 checked，其余 7 行为空圈，文件名不变。

同一设备的 `FileMenuAndDetailActionsInstrumentedTest#ordinaryFileLongPressRenamesDirectlyAndMoreExportsAndShares` 返回仪器状态 0，保留原有重命名、导出和分享入口断言。该文件操作回归不替代上面真实文件夹的逐行图形截图。

根 `lintDebug` 通过。`feature:home` 新增 1 条 `IconDuplicates` 警告：上游两个不同名称 `angular_guard`、`angular_resolver` 的导出 WebP 内容相同。两项保留各自正式资源名；没有隐藏警告或据此声称 636 张图具有 636 种不同外形。
