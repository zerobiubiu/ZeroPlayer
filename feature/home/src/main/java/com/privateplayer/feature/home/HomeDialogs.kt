package com.privateplayer.feature.home

import com.privateplayer.domain.repository.FileTreeEntry

/** 首页对话框状态；名称/移动/删除对话框本体在 core:design 共享，详情页复用同一实现。 */
internal sealed interface HomeDialog {
    data class CreateDirectory(val parent: String) : HomeDialog
    data class Rename(val entry: FileTreeEntry) : HomeDialog
    data class Move(val entry: FileTreeEntry) : HomeDialog
    data class Delete(val entry: FileTreeEntry) : HomeDialog
}
