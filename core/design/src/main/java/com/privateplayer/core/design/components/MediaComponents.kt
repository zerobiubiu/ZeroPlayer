package com.privateplayer.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.privateplayer.core.design.theme.PlaceholderPalettes
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.core.design.R
import java.io.File
import kotlin.math.absoluteValue

/**
 * 缩略图容器：真实缩略图 + 稳定占位渐变 + 时长/进度/收藏角标。
 *
 * 占位色按 [fallbackKey] 稳定哈希，同一媒体在任何页面颜色一致，避免列表“闪烁换色”。
 */
@Composable
fun MediaThumbnail(
    thumbnailFile: File?,
    fallbackKey: Long,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = Radius.thumbnail,
    contentScale: ContentScale = ContentScale.Crop,
    durationText: String? = null,
    progress: Float? = null,
    isFavorite: Boolean = false,
    showPlayBadge: Boolean = false,
) {
    val palette = remember(fallbackKey) {
        PlaceholderPalettes[(fallbackKey.hashCode().absoluteValue) % PlaceholderPalettes.size]
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(palette)),
    ) {
        if (thumbnailFile != null) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }

        if (showPlayBadge) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        if (isFavorite) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = stringResource(R.string.design_favorite),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }

        if (durationText != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }

        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}


