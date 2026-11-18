# 全页面文案密度审计与整改

状态：1.3.0（10300）。范围为 Android 导航可达页面、浮层、对话框、空/错误态、系统通知及随包 Wi-Fi 网页。未改用户数据、三方许可原文、配色或导航体系。本记录描述本轮实际取舍；[旧审计](../ui-ux-audit/README.md)仍按当时版本保留。

## 日常规则

默认不加说明。标题标明任务，分组和间距负责关联，状态用实际值，按钮以短动词/对象命名（中文通常 2–4 字，不为字数牺牲对象辨识）。不写标题导语、卡片介绍、复述开关的副文或「点击下方…」。示例放占位符，已有值直接预填；占位符不能代替无障碍名称。可从界面看出的不重复；不直观且影响当前决定的最多一句。只有错误、约束或真实风险需要立即知道时展开；低频技术材料用已有信息、详情、关于或选项。成功报结果，错误报可确认的原因并提供能执行的恢复入口。不能为简短删除不可逆后果、权限与隐私、单位和计数口径、读屏专属名称、署名。

## 内容台账

下表对照实际渲染位置与其资源；同一行内列举的是同一目的的短语/资源族。页面名、文件名、实际选值、按钮和读屏专属操作不是说明段，不计成「精简了 N 条」。Android 为原生 Compose/Material 3；沿用 `core/design` 的令牌与系统浅/深主题。网页是独立原生 HTML/CSS/JS，不采用 Tailwind。

| 页面与渲染点 | 文案或资源族 | 裁决与原因 |
|---|---|---|
| 根导航 / `PrivatePlayerRoot`、`MainActivity` | 首页、设置、导入任务；分享 chooser；`app_transfer_busy_toast` | 标签和系统选择目标留。仅在传输阻止退出时提示「传输未完成，可按主页键转到后台」，不另教正常返回。系统的通知权限理由仍由平台呈现，拒绝不虚报导入失败。 |
| 导入任务 / `TransferDialogs`、`ImportViewModel` | `app_import_creating/empty`、`app_task_queued/running/committing/completed/canceled/failed`、`import_notice_enqueued/duplicates/rejected` | 启动中、已复制字节、校验保存与完成不能混为一态；通知分条保留接受数量、重复名与拒绝来源/原因。已有具体通知时不再同时显示「暂无导入任务」。 |
| 导入失败与放弃暂存 | `app_task_error_space/source/io/canceled/generic`、详情 / 重试、`app_task_discard_*`；`import_*_failed`、`app_external_*` | 保留清空间、重选来源、重试等现有恢复动作；原始失败详情由用户展开。删除的是本次记录和**应用内暂存**，外部源文件不变；普通取消/移除记录/重试不互相替代。外部分享、保存/恢复请求等错误按实际分支给原因或重新分享入口，不合并独立失败。 |
| 导出 / 分享与系统文件选择 | `app_export*`、`app_share*`、`app_no_*`、`app_picker_failed` | 复制字节/总量、并发等待、非法/自身目标、源在复制时变更、缺 chooser 与原始原因保留；取消/失败后的目标**可能已有未完成文件**，不是成功。成功只报导出完成。 |
| 首页 / `HomeScreen`、`FileTreeRow`、`HomeHeader` | `home_empty_title`、原 `home_empty_message/home_empty_import`；路径、类型/字节数、`home_row_*` | 删「导入视频，或从右上角新建文件夹」及重复资源，只留空态和一个可用「导入文件」主操作。当前文件夹用标题、祖先用面包屑，无行尾重复菜单；普通文件大小保留单位。菜单入口、全选、选择/取消、读屏文件操作不删。 |
| 首页错误 / 单个文件操作 | `home_loading/error_root/error_directory`、`home_unsupported_file`、`home_{open_video,create_folder,rename,move,delete}_failed`、`home_delete_target_changed` | 只显示一次读取失败，存在的来源错误可见，现有「重试/返回上级」承担恢复，不在正文复述按钮。非视频没有可见播放操作，点入才告知可导出/分享；同名、无效名、目标更换等原因或恢复建议只在失败时出现。成功只报结果。 |
| 文件菜单、名称、移动、删除 / `core/design/FileDialogs`、`FileActionMenu` | `design_action_*`、名称输入错误、`design_move_current_*/up/loading/error/empty/confirm`、`design_delete_*` | 分组、动词和符号表达层级；原生名称字段保留空、`.`/`..`、路径分隔符约束。移动去掉「选择目标文件夹」教学，路径、上级和真正不可进入/加载失败状态留。永久删除点名对象，目录包括其中全部内容，不能撤销。 |
| 首页多选、批量结果 | `design_selection_count`、`home_batch_move_title/delete_title/delete_message/result_*/changed_summary/failures/retry` | 数量只在标题写一次；批量移动不能把第一个文件名冒充全部对象。结果分别报告成功/失败数，成功项退出选择；独立失败原因与对应文件名在「失败详情」按需展开，目标被替换保留必须重新选择的提示。 |
| 详情主表 / `MediaDetailScreen` | `detail_progress_format`、原 `detail_no_progress`、`detail_not_probed`、`detail_*codec/bitrate/duration/resolution`、`detail_file_status_missing` | 不重复封面与表格时长、上次播放时间；无进度不填句。只呈现可用参数，整组无视频参数时只报一次未识别；没有音频信息/记录不造空组，不把未知报成零。播放次数、百分比、日期时间、分辨率、码率和已知时长保留原精度。正常文件不逐次宣告「正常」，缺失才标。 |
| 详情·文件信息（既有 i 入口） | `detail_info_*`、`detail_audio_tracks_probe/subtitle_tracks_probe/subtitle_tracks_unknown/probe_source`、`detail_private_storage_note` | 真实路径/磁盘检测、MIME、缩略图、导入探测数与时间归此；探测未认出字幕≠播放器一定没有。原常驻「私有存储说明」卡移入此抽屉，保留他应用访问限制、清数据会失去私有副本、原文件不变；允许滚动查看。 |
| 详情·外挂字幕 | `detail_subtitles_header/empty`、`detail_subtitle_source_*`、`detail_subtitle_delete_*`、`detail_same_dir_subtitle_error/subtitle_op_failed` | 计数和添加按钮已经说明空态，删「还没有…也可添加」；仅保留同目录同名匹配和支持格式。来源决定能否就地删除，保留私有副本/首页管理区别和对象级读屏标签。确认只报永久删除哪一副本以及原字幕、视频不变；读取失败显示真实原因和恢复位置。 |
| 设置总览 / `SettingsEntryRow` | `settings_detail_speed_*/font_scale_*/seek_*/haptics_*/video_usage_*/version_*`，主题实际值；原 `settings_detail_import` | 三个任务分组七个入口；保留可比较的实际选项、开关、字号、容量、版本与引擎。删除静态「缩略图与重复检测」目录简介，不为补齐每行而另造空文案。 |
| 设置·播放 / 进度、画面比例和默认速度 | `settings_auto_play_next_desc`、`option_progress_disabled/record_only/resume`、`option_aspect_*`、`settings_numeric_* / settings_range_*` | 删除复述「自动播放下一个视频」的副文。三个进度规则影响下一次打开，保留完整选项；比例、常规/临时速度的不同下限及单位、预填当前值、聚焦范围和越界错误不删。 |
| 设置·字幕 / 共享 `SubtitleAppearanceControls` | 原 `settings_subtitle_memory_note`、`design_subtitle_format_note`、字号/字体/字形/前景背景/位置与重置、延迟步长 | 去掉常驻字幕记忆介绍（每片记住选轨，见[播放架构](../architecture/playback-abstraction.md)）。ASS 关闭覆盖即保留作者样式、位图不改字体/颜色会影响选择，紧邻开关保留。方向、百分比、整数毫秒和上下限不可省略。 |
| 设置·手势与控制 | `settings_gesture_double_tap_zones/seek_desc/gesture_hold`、原 `settings_gesture_haptics_desc` | 三区和快慢滑的非可见映射各一句；长按**画面**属于独立设置。移除马达机型解释，震动开关直称「进度与倍速震动」。简介不重复标签；倍速扇的操作详见 [0034](0034-speed-fan-live-preview.md)。 |
| 设置·外观 / 设置·文件导入 | 原 `settings_theme/theme_note`、`design_import_thumbnails_desc/duplicates_desc` | 主题只需选择器及当前值，删除重复分组和配色介绍。删缩略图失败只影响封面的静态说明（缩略图生成失败不妨碍文件导入）。保留重复检测的口径与**仍保留副本**，与 Wi-Fi 面板使用同一开关名。 |
| 设置·存储与清理 / 关于 / 许可弹窗 | `settings_storage_*`、`settings_clear_note`、`settings_about_*` | 数量是视频数据库记录数，容量是私有文件树的总字节；不把两个口径合并为文件个数。缓存、可用空间及清理不碰媒体或待恢复暂存保留。关于保留版本、引擎、私有存储/不扫描/手动开启网络、FreeType 署名和完整许可入口；删「下方查看」和分发操作介绍，未改第三方许可原文。 |
| Wi-Fi 手机面板 / `WifiImportSheet` | `wifi_status_*`、目录/可用空间和端口、`wifi_browser_addresses/network_*/copy_*`、`wifi_usage_note`、`wifi_uploads_* / transfer_*` | 只在有地址时显示同网浏览的非直观入口，网卡/地址与复制结果一一对应。关闭面板不等于停服，保留该后果及通知停止入口；两个设置不再重复解释。保留本次上传的接收/保存、字节数、失败原因以及适用的保存重试/取消。 |
| 手机权限与网络风险、系统通知 | `wifi_permission_denied/manual/go_settings`、`wifi_risk_title/detail`；`ImportWorker`、`WebUploadService`、`PlayerMediaSessionService` | 被拒与不能打开设置分支分别留恢复方法；启动服务前仍明确 HTTP 明文、无鉴权、能读写删除、只在可信网络用。系统渠道删重述名称的描述；Wi-Fi 常驻通知不暗示网络访问受认证，保留「停止」。导入通知保留真实文件/进度；播放通知保留媒体标题/控制和失败原因。 |
| 打包网页·上传区、导航和队列 | `index.html#upload-hint/#connection-error/.notice`；`app.js` 的 `setConnection/updateControls/runQueue/trackTask/restoreTasks` | 简化多选/拖放，不删除可见 label、跳至主要内容、目标目录、当前网路状态、读屏 live 状态。保留明文、无需登录、有权读写删除的风险。队列总数/待传/已保存分别计；「传输完成」不是「已保存」，确认/保存失败/未知各给可行恢复，取消发送不能承诺设备没收。恢复记录失败告知在手机查看，不要求重新上传；清记录前仍说无法从本页追查/恢复、不删手机文件。 |
| 打包网页·文件浏览、管理、名称和移动/删除 | `loadFiles/initializeFiles/openDialog/nameField/createFolder/renameEntry/moveEntry/deleteEntry` | 面包屑、对象名称、大小、修改时间、可访问名称保留。空目录只报「目录为空」。创建只给目标路径；重命名原值预填，不复述「输入新名称」；移动保留源、当前目标和原地/自身禁入原因，不教「点击下方」。删除确认保留名称、文件夹内全部内容、永久不可撤销；冲突保留服务原始原因，失败对话框不消失。 |
| 共享层 | `Bars/Dialogs/States/SettingValueRow/MediaComponents`、`domain` 选项资源 | 删强制空态说明和泛化错误标题；控件没有说明就不为其留空。保留所选状态、单选/动作分工、独立按钮名称、面板和导航名称、单位/时间/合法值，没有以图标或颜色替代独有语义。 |

本次仅审查到达上述表面的原因/状态（含 `FileTreeRepository`、`ImportRepository`/Worker、SAF、字幕存储、HTTP 返回和 Media3），没有改写底层错误协议、日志、平台自带教学或第三方库文本。终端不同、真实硬件触感和真人 TalkBack 需另验。

### 播放器（逐个生产渲染点）

| 播放面 / 说明资源 | 留、删与去向 |
|---|---|
| `PlayerScreen` 顶底栏、锁定、加载/错误、结束 | 留片名、当前/总/剩余时间（不是同一量）、图标按钮读屏名、真实失败及其返回/重试/关闭路径。片尾按钮直接称「重播」；锁后恢复动作「解锁屏幕」而非重述当前状态。 |
| 画面手势 / `PlayerGestures`、`PlayerOverlays` | 只在操作中或短时反馈亮度/音量百分比、画面长按实际倍数、进度目标时间、带符号跳幅/刻度秒数与快慢（分别是位置、变化和精度）。没有常驻教学。 |
| 扇面 / `PlayerSpeedControl`、`PlayerSpeedMenu` | 去第二标题、重复常速/松手说明。13 个倍率含单位，常规已选和试播轮廓/下划线可区分；非预设才另显精确「已选 N×」，不把长按前的常规档叫作实时速度。候选、回原点及关闭提示保留为读屏状态/操作。 |
| 更多 / 比例 / 音轨 | 标题改「更多」，不复写片名；删「隐藏控制，避免误触」及无当前轨时的「音轨/音轨」。比例实际模式、音轨名/编解码（区分同名）、无轨状态仅在主动打开时显示。 |
| 字幕 / `PlayerSubtitleSheet` 及共享外观 | 无轨只留状态及有效的详情页导入路径，去无轨的「关闭字幕」和格式枚举；有轨保留真实名称、关闭。延迟/步长用 ms 并可重置，归该视频。外观按需展开，保留方向、百分比、ASS 与图像字幕的适用边界、返回/完成分流。 |
| 队列与播放模式 | 空队列只报「队列为空」，删重复说明和不存在的 1/1；非空显示真实索引/总数、文件名，不显示私有路径。上一/下一、四种模式及当前选中均保留。 |

## 验证方式与边界

- **实际打开的完整应用**：API 37、420 dpi、字号 1.3，从 `MainActivity` 的[首页空态](../ui-ux-audit/implementation/evidence/copy-home-1.3.0.png)进入设置总览、手势、外观、关于，另从首页打开[未授权、未启动的 Wi‑Fi 风险面板](../ui-ux-audit/implementation/evidence/copy-wifi-risk-1.3.0.png)。对照屏幕发现「长按临时倍速」先后充当开关与数值标题；改为「启用临时倍速 / 目标倍速」，覆盖安装后重新进[手势页](../ui-ux-audit/implementation/evidence/copy-gestures-1.3.0.png)逐张查看，看到 2.0× 和独立的「进度与倍速震动」。外观实见「主题模式 / 跟随系统」；关于实见版本、引擎、隐私范围、FreeType 署名和许可入口。
- **不可逆提示抽查**：在同一模拟器的首页长按本次创建的空文件夹 `CopyProofFolder`，「更多 → 删除」实际显示对象名、永久删除所有内容及不可撤销，保留「取消 / 删除」；点取消后夹具仍在。截图 `build/reports/speed-fan-1.3.0/copy-delete-folder.png` 为本地验收归档，不是生产用户的文件。
- **在模拟器的实际组件 / 隔离夹具**：定向 `am instrument -w -r -e class …` 返回 `OK (6 tests)`，14.205 秒。覆盖空队列的不可执行动作、详情添加/区分同目录与私有字幕、仅一项成功时的独立失败详情及文件替换保护、放弃/取消暂存的结果、风险块重开后仍在、随包 WebView 的原生删除确认（对象、永久删除和目录内全部内容）与服务器冲突（保留原身份/原因）。这些组件用例不是 6 次完整应用手工导航；WebView 提供可控的 HTTP 应答，未执行真实服务端删除。
- **仅本轮源码走查**：表中未列入上述实测的每个细分状态和控制（包括系统选择器缺失、各种导出失败、全部字幕格式、所有设置值、网页重连/历史恢复、不同厂商通知和读屏播报）不等于已经截图或真人测过。没有因改字运行无关回归。
- **网页依据**：用本机已缓存的 `modern-web-guidance` 离线检索 `confirmation modal dialog`，读取 `dialog` 和 `accessibility`。按 “name every dialog with `aria-labelledby` pointing at its heading” 保留原生对话框及其标题，把重命名教学删掉而不删输入框标签；风险确认仍有说明。`#file-input` 的可访问名称遵守 “Explicitly associate `<label>` with its control via `for`/`id`”。包内 HTML/JS 的本机检测器返回 `[]`；不将检测结果当作设备读屏或视觉证明。
