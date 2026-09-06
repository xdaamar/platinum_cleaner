package com.example.platinumcleaner.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platinumcleaner.R
import com.example.platinumcleaner.ui.theme.Dimens
import com.example.platinumcleaner.ui.theme.PlatinumAccent
import com.example.platinumcleaner.ui.theme.PlatinumOnPrimary
import com.example.platinumcleaner.ui.theme.PlatinumOnPrimaryContainer
import com.example.platinumcleaner.ui.theme.PlatinumOnSurface
import com.example.platinumcleaner.ui.theme.PlatinumOnSurfaceVariant
import com.example.platinumcleaner.ui.theme.PlatinumOutlineVariant
import com.example.platinumcleaner.ui.theme.PlatinumPrimary
import com.example.platinumcleaner.ui.theme.PlatinumSurface
import com.example.platinumcleaner.ui.theme.PlatinumSurfaceContainer
import com.example.platinumcleaner.ui.theme.PlatinumSurfaceContainerHigh
import com.example.platinumcleaner.ui.theme.PlatinumSurfaceContainerLow
import com.example.platinumcleaner.ui.theme.PlatinumSurfaceContainerLowest
import com.example.platinumcleaner.ui.theme.PlatinumSurfaceVariant

@Composable
fun TopHeaderBar(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.HeaderHeight),
        color = PlatinumSurface.copy(alpha = 0.92f),
        shadowElevation = Dimens.ElevationSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingLG),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSM)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_platinum_emblem),
                    contentDescription = null,
                    tint = PlatinumPrimary,
                    modifier = Modifier.size(Dimens.Spacing2XL)
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.header_subtitle).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = PlatinumOnSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = stringResource(id = R.string.header_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = PlatinumOnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(Dimens.Spacing2XL)
                    .clip(CircleShape)
                    .background(PlatinumPrimary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = onProfileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_person),
                    contentDescription = stringResource(id = R.string.cd_profile),
                    tint = PlatinumOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun GreetingSection(
    modifier: Modifier = Modifier,
    onQuickSettingsClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.greeting_status).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = PlatinumOnSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
            Text(
                text = stringResource(id = R.string.greeting_title),
                style = MaterialTheme.typography.headlineLarge,
                color = PlatinumOnSurface
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(elevation = Dimens.ElevationSoft, shape = CircleShape)
                .clip(CircleShape)
                .background(PlatinumSurfaceContainerLowest)
                .border(
                    width = Dimens.BorderThin,
                    color = PlatinumOutlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = onQuickSettingsClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tune),
                contentDescription = stringResource(id = R.string.cd_quick_settings),
                tint = PlatinumPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HeroStorageCard(
    state: StorageMetricState,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (state.isCleaned) 0f else state.sweepProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "GaugeAnimation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusLG),
        color = PlatinumSurfaceContainerLowest,
        shadowElevation = Dimens.ElevationSoft
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle tactile accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingXL)
                    .height(2.dp)
                    .background(PlatinumAccent)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpacingXL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Minimal Circular Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(144.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(144.dp)
                            .rotate(-90f)
                    ) {
                        val strokeWidth = 5.dp.toPx()
                        // Track Arc
                        drawCircle(
                            color = PlatinumSurfaceContainerHigh,
                            style = Stroke(width = strokeWidth)
                        )
                        // Active Arc
                        drawArc(
                            color = PlatinumPrimary,
                            startAngle = 0f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.metric_to_reclaim).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (state.isCleaned) "0.0" else state.reclaimableAmount,
                            style = MaterialTheme.typography.displayLarge,
                            color = PlatinumOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.metric_unit_gb),
                            style = MaterialTheme.typography.labelMedium,
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpacingXS))

                Text(
                    text = if (state.isCleaned) {
                        stringResource(id = R.string.clean_button_done)
                    } else {
                        stringResource(id = R.string.metric_subtext)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlatinumOnSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.SpacingLG))

                // Micro-Stat Breakdown Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusMD))
                        .background(PlatinumSurfaceContainerLow)
                        .padding(horizontal = Dimens.SpacingMD, vertical = Dimens.SpacingSM),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.system_cache_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
                        Text(
                            text = if (state.isCleaned) "0 B" else stringResource(id = R.string.system_cache_value),
                            style = MaterialTheme.typography.titleSmall,
                            color = PlatinumOnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(PlatinumOutlineVariant.copy(alpha = 0.3f))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.SpacingMD)
                    ) {
                        Text(
                            text = stringResource(id = R.string.user_cache_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
                        Text(
                            text = if (state.isCleaned) "0 B" else stringResource(id = R.string.user_cache_value),
                            style = MaterialTheme.typography.titleSmall,
                            color = PlatinumOnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    state: StorageMetricState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ActionButtonHeight)
            .shadow(elevation = Dimens.ElevationSoft, shape = RoundedCornerShape(Dimens.RadiusLG))
            .clickable(
                enabled = !state.isPurging && !state.isCleaned,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(Dimens.RadiusLG),
        color = if (state.isCleaned) PlatinumSurfaceContainerHigh else PlatinumPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingLG),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSM)
            ) {
                val iconRes = when {
                    state.isCleaned -> R.drawable.ic_done_all
                    else -> R.drawable.ic_auto_mode
                }
                val iconTint = if (state.isCleaned) PlatinumPrimary else PlatinumOnPrimary

                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )

                val label = when {
                    state.isPurging -> stringResource(id = R.string.clean_button_purging)
                    state.isCleaned -> stringResource(id = R.string.clean_button_done)
                    else -> stringResource(id = R.string.clean_button_initial)
                }
                val textColor = if (state.isCleaned) PlatinumPrimary else PlatinumOnPrimary

                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!state.isCleaned) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = PlatinumOnPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun LargestCacheSection(
    items: List<AppCacheItem>,
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SpacingXS),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.largest_cache_title),
                style = MaterialTheme.typography.titleSmall,
                color = PlatinumOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onViewAllClick) {
                Text(
                    text = stringResource(id = R.string.largest_cache_view_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlatinumOnSurfaceVariant
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
        ) {
            items.forEach { item ->
                AppCacheRowItem(item = item)
            }
        }
    }
}

@Composable
fun AppCacheRowItem(
    item: AppCacheItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusMD),
        color = PlatinumSurfaceContainerLowest,
        shadowElevation = Dimens.ElevationSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingMD),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMD),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Dimens.RadiusSM))
                        .background(PlatinumSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = PlatinumPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = item.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = PlatinumOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(id = item.packageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(Dimens.SpacingSM))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
            ) {
                if (item.isPrimary) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.RadiusXS))
                            .background(PlatinumSurfaceContainer)
                            .padding(horizontal = Dimens.SpacingXS, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.badge_primary),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurface,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = stringResource(id = item.sizeRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlatinumOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun InsightCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusMD),
        color = PlatinumSurfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingMD),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSM),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield_check),
                contentDescription = null,
                tint = PlatinumOnSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Text(
                text = stringResource(id = R.string.whitelist_insight_text),
                style = MaterialTheme.typography.bodySmall,
                color = PlatinumOnSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun PlatinumBottomNav(
    selectedTab: DashboardNavTab,
    onTabSelected: (DashboardNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.BottomNavHeight),
        color = PlatinumSurfaceContainerLowest.copy(alpha = 0.94f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingXS),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardNavTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor = if (isSelected) PlatinumPrimary else PlatinumOnSurfaceVariant

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusSM))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = false, radius = 28.dp),
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(horizontal = Dimens.SpacingSM, vertical = Dimens.SpacingXXS),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = tab.iconRes),
                        contentDescription = stringResource(id = tab.titleRes),
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
                    Text(
                        text = stringResource(id = tab.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
