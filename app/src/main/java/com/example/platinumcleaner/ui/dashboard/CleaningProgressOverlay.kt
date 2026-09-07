package com.example.platinumcleaner.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.platinumcleaner.ui.theme.Dimens
import com.example.platinumcleaner.ui.theme.PlatinumAccent
import com.example.platinumcleaner.ui.theme.PlatinumOnSurface
import com.example.platinumcleaner.ui.theme.PlatinumOnSurfaceVariant
import com.example.platinumcleaner.ui.theme.PlatinumOutlineVariant
import com.example.platinumcleaner.ui.theme.PlatinumSurface

/**
 * CleaningProgressOverlay — Real-Time Progress UI (Sprint 5).
 *
 * Posisi: Kiri bawah layar (Alignment.BottomStart).
 * Hanya tampil saat `isCleaning == true`.
 *
 * Desain "Quiet Luxury":
 * - Surface putih/abu muda dengan shadow tipis
 * - Lebar 65% layar (tidak full width)
 * - AnimatedVisibility: slideInVertically / slideOutVertically (60fps GPU-friendly)
 * - LinearProgressIndicator tipis 3dp
 *
 * Sesuai 01_design_rules.md: 8pt grid, PlatinumSurface, smooth transitions.
 */
@Composable
fun CleaningProgressOverlay(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val metricState by viewModel.metricState.collectAsState()
    val isVisible = metricState.isCleaning

    val currentIndex = metricState.currentCleanIndex
    val totalApps = metricState.totalCleanApps
    val appName = metricState.currentCleanAppName ?: metricState.cleaningTarget ?: "..."

    val progress = if (totalApps > 0) {
        (currentIndex.toFloat() / totalApps.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = Dimens.SpacingLG, bottom = 28.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 380)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.65f),
                shape = RoundedCornerShape(16.dp),
                color = PlatinumSurface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.SpacingMD)
                ) {
                    // Header row: label + pulse dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PlatinumAccent)
                        )
                        Text(
                            text = "Membersihkan Cache...",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.SpacingXXS))

                    // Progress text: "X / Y"
                    Text(
                        text = if (totalApps > 0) "$currentIndex / $totalApps" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = PlatinumOnSurface,
                        fontWeight = FontWeight.Bold
                    )

                    // App name
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(Dimens.SpacingXS))

                    // LinearProgressIndicator tipis 3dp (Material3 API < 1.2 compatible)
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PlatinumOnSurface,
                        trackColor = PlatinumOutlineVariant
                    )
                }
            }
        }
    }
}
