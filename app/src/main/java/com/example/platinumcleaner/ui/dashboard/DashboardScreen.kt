package com.example.platinumcleaner.ui.dashboard

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.R
import com.example.platinumcleaner.ui.theme.Dimens
import com.example.platinumcleaner.ui.theme.PlatinumAccent
import com.example.platinumcleaner.ui.theme.PlatinumBackground
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
import com.example.platinumcleaner.util.PermissionHelper

// Import components from DashboardComponents
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(DashboardNavTab.DASHBOARD) }
    val appsState by viewModel.appsState.collectAsState()
    val metricState by viewModel.metricState.collectAsState()
    val needsPermission by viewModel.needsPermission.collectAsState()

    // Reload data saat layar kembali fokus (e.g., setelah user kembali dari Settings)
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopHeaderBar() },
        bottomBar = {
            PlatinumBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = PlatinumBackground
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PlatinumBackground)
                .padding(
                    top = innerPadding.calculateTopPadding() + Dimens.SpacingMD,
                    bottom = innerPadding.calculateBottomPadding() + Dimens.Spacing2XL,
                    start = Dimens.SpacingLG,
                    end = Dimens.SpacingLG
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXL)
        ) {
            item { GreetingSection() }

            // Permission Onboarding Banner (jika Usage Access belum diberikan)
            if (needsPermission) {
                item {
                    PermissionBanner(
                        onEnableClick = {
                            context.startActivity(PermissionHelper.buildUsageAccessIntent())
                        }
                    )
                }
            }

            item { HeroStorageCard(state = metricState) }

            item {
                PrimaryActionButton(
                    state = metricState,
                    onClick = { viewModel.triggerClean() }
                )
            }

            // Dynamic app list from real data
            when (val state = appsState) {
                is UiState.Loading -> {
                    item { LoadingShimmer() }
                }
                is UiState.Success -> {
                    val topApps = state.data.take(Constants.MAX_DASHBOARD_APP_ITEMS)
                    if (topApps.isNotEmpty()) {
                        item {
                            RealAppListSection(
                                apps = topApps,
                                totalCount = state.data.size
                            )
                        }
                    }
                    item { InsightCard() }
                }
                is UiState.PermissionRequired -> {
                    item { InsightCard() }
                }
                is UiState.Error -> {
                    item {
                        ErrorCard(message = state.message)
                    }
                    item { InsightCard() }
                }
            }

            item { Spacer(modifier = Modifier.height(Dimens.SpacingLG)) }
        }
    }
}

@Composable
fun PermissionBanner(
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusLG),
        color = PlatinumPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingMD),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.RadiusXS))
                            .background(PlatinumOnPrimaryContainer.copy(alpha = 0.2f))
                            .padding(horizontal = Dimens.SpacingXS, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permission_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnPrimary.copy(alpha = 0.8f),
                            fontSize = 9.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlatinumOnPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimens.SpacingXXS))
                Text(
                    text = stringResource(R.string.permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlatinumOnPrimary.copy(alpha = 0.75f),
                    lineHeight = 17.sp
                )
            }
            Spacer(modifier = Modifier.width(Dimens.SpacingMD))
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlatinumSurface,
                    contentColor = PlatinumPrimary
                ),
                shape = RoundedCornerShape(Dimens.RadiusMD)
            ) {
                Text(
                    text = stringResource(R.string.permission_button),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LoadingShimmer(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        PlatinumSurfaceContainerHigh,
        PlatinumSurfaceContainer,
        PlatinumSurfaceContainerHigh
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .width(120.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSM))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .height(14.dp)
                    .width(80.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSM))
                    .background(brush)
            )
        }

        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusMD))
                    .background(brush)
            )
        }
    }
}

@Composable
fun RealAppListSection(
    apps: List<AppInfo>,
    totalCount: Int,
    modifier: Modifier = Modifier
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
                text = stringResource(R.string.largest_cache_title),
                style = MaterialTheme.typography.titleSmall,
                color = PlatinumOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {}) {
                Text(
                    text = "View All ($totalCount)",
                    style = MaterialTheme.typography.labelMedium,
                    color = PlatinumOnSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)) {
            apps.forEachIndexed { index, app ->
                RealAppCacheRowItem(
                    app = app,
                    isPrimary = index == 0
                )
            }
        }
    }
}

@Composable
fun RealAppCacheRowItem(
    app: AppInfo,
    isPrimary: Boolean = false,
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
                // App icon placeholder — monogram dari huruf pertama nama app
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Dimens.RadiusSM))
                        .background(PlatinumSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = PlatinumOnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = PlatinumOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
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
                if (isPrimary) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.RadiusXS))
                            .background(PlatinumSurfaceContainer)
                            .padding(horizontal = Dimens.SpacingXS, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_primary),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurface,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = app.cacheSizeFormatted,
                    style = MaterialTheme.typography.titleSmall,
                    color = PlatinumOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                tint = PlatinumOnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.error_text),
                style = MaterialTheme.typography.bodySmall,
                color = PlatinumOnSurfaceVariant
            )
        }
    }
}
