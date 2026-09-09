package com.example.platinumcleaner.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.platinumcleaner.Constants
import com.example.platinumcleaner.R
import com.example.platinumcleaner.ui.theme.Dimens
import com.example.platinumcleaner.ui.theme.PlatinumBackground
import com.example.platinumcleaner.ui.theme.PlatinumOnPrimary
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by remember { mutableStateOf(DashboardNavTab.DASHBOARD) }
    val appsState by viewModel.appsState.collectAsState()
    val metricState by viewModel.metricState.collectAsState()
    val needsPermission by viewModel.needsPermission.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showPermissionSheet by remember { mutableStateOf(false) }
    var showViewAllSheet by remember { mutableStateOf(false) }
    val inventorySummary by viewModel.inventorySummary.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ===================================================
    // Sprint 6: ON_RESUME Lifecycle Observer
    // Dua skenario: (1) ada pending verification → verify, (2) tidak → refresh biasa.
    // DisposableEffect memastikan observer di-remove saat Composable keluar.
    // ===================================================
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed() // Sprint 6: verification-aware resume
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tampilkan bottom sheet saat permission dibutuhkan
    LaunchedEffect(needsPermission) {
        if (needsPermission) showPermissionSheet = true
    }

    // Sprint 4: Snackbar one-shot — tampilkan dan reset state
    LaunchedEffect(metricState.snackbarMessage) {
        val msg = metricState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.onSnackbarShown()
        }
    }

    // Permission Bottom Sheet
    if (showPermissionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPermissionSheet = false },
            sheetState = sheetState,
            containerColor = PlatinumSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            PermissionBottomSheetContent(
                onEnableClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showPermissionSheet = false
                    }
                    context.startActivity(PermissionHelper.buildUsageAccessIntent())
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showPermissionSheet = false
                    }
                }
            )
        }
    }

    // V8: Full Real Inventory Bottom Sheet (View All)
    if (showViewAllSheet) {
        val allApps = (appsState as? UiState.Success)?.data ?: emptyList()
        ViewAllInventorySheet(
            apps = allApps,
            inventorySummary = inventorySummary,
            onCleanApp = { pkg ->
                showViewAllSheet = false
                viewModel.initiateCleanForApp(pkg)
            },
            onDismiss = { showViewAllSheet = false }
        )
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
        containerColor = PlatinumBackground,
        // Sprint 4: Snackbar dengan styling Quiet Luxury
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PlatinumSurface,
                    contentColor = PlatinumOnSurface,
                    shape = RoundedCornerShape(Dimens.RadiusMD)
                )
            }
        }
    ) { innerPadding ->

        // Sprint 5: Box wrapper agar overlay bisa diposisikan di BottomStart
        Box(modifier = Modifier.fillMaxSize()) {
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
            item { HeroStorageCard(state = metricState) }
            item {
                PrimaryActionButton(
                    state = metricState,
                    onClick = { viewModel.triggerSmartClean() } // Sprint 6: Orchestrator-driven
                )
            }

            if (metricState.isCleaning) {
                item { CleaningStatusBanner(targetPackage = metricState.cleaningTarget) }
            }

            when (val state = appsState) {
                is UiState.Loading -> item { LoadingShimmer() }
                is UiState.Success -> {
                    val topApps = state.data.take(Constants.MAX_DASHBOARD_APP_ITEMS)
                    if (topApps.isNotEmpty()) {
                        item {
                            RealAppListSection(
                                apps = topApps,
                                totalCount = state.data.size,
                                onViewAllClick = { showViewAllSheet = true },
                                onCleanApp = { viewModel.initiateCleanForApp(it) }
                            )
                        }
                    }
                    item { InsightCard() }
                }
                is UiState.PermissionRequired -> item { InsightCard() }
                is UiState.Error -> {
                    item { ErrorCard(message = state.message) }
                    item { InsightCard() }
                }
            }

            item { Spacer(modifier = Modifier.height(Dimens.SpacingLG)) }
        } // end LazyColumn

        // Sprint 5: Progress overlay — muncul di kiri bawah saat cleaning aktif
        CleaningProgressOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            viewModel = viewModel
        )

        } // end Box
    } // end Scaffold
}

// ===================================================
// Permission Bottom Sheet — Quiet Luxury
// ===================================================

@Composable
fun PermissionBottomSheetContent(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTutorialExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isTutorialExpanded) 90f else 0f,
        animationSpec = tween(300),
        label = "arrow_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 32.dp)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(PlatinumOutlineVariant)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMD)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PlatinumPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = PlatinumPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = PlatinumOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.permission_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = PlatinumOnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXL))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PlatinumOnSurfaceVariant,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(Dimens.SpacingXL))

        // Expandable Tutorial
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.RadiusMD),
            color = PlatinumSurfaceContainerLow
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTutorialExpanded = !isTutorialExpanded }
                        .padding(Dimens.SpacingMD),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lihat Panduan Lengkap",
                        style = MaterialTheme.typography.labelLarge,
                        color = PlatinumOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = PlatinumOnSurfaceVariant,
                        modifier = Modifier.size(18.dp).rotate(arrowRotation)
                    )
                }

                // AnimatedVisibility — 60fps GPU-friendly sesuai 01_design_rules.md
                AnimatedVisibility(
                    visible = isTutorialExpanded,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = Dimens.SpacingMD,
                            end = Dimens.SpacingMD,
                            bottom = Dimens.SpacingMD
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMD)
                    ) {
                        TutorialStep(1, "Ketuk tombol di bawah untuk membuka Pengaturan.")
                        TutorialStep(2, "Cari dan pilih 'Platinum Cleaner' di daftar aplikasi.")
                        TutorialStep(3, "Aktifkan toggle 'Izinkan akses data penggunaan'.")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingXL))
        Button(
            onClick = onEnableClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(Dimens.RadiusMD),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlatinumPrimary,
                contentColor = PlatinumOnPrimary
            )
        ) {
            Text(
                text = stringResource(R.string.permission_button),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(Dimens.SpacingSM))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Nanti saja",
                style = MaterialTheme.typography.labelLarge,
                color = PlatinumOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun TutorialStep(step: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMD),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(PlatinumPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = PlatinumPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = PlatinumOnSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ===================================================
// Cleaning Status Banner
// ===================================================

@Composable
fun CleaningStatusBanner(targetPackage: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusMD),
        color = PlatinumSurfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingMD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMD)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "pulse_alpha"
            )
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(PlatinumPrimary.copy(alpha = alpha))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Membersihkan cache...",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlatinumOnSurface,
                    fontWeight = FontWeight.Medium
                )
                if (targetPackage != null) {
                    Text(
                        text = targetPackage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ===================================================
// Shimmer Loading
// ===================================================

@Composable
fun LoadingShimmer(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        PlatinumSurfaceContainerHigh,
        PlatinumSurfaceContainer,
        PlatinumSurfaceContainerHigh
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_translate"
    )
    val brush = Brush.linearGradient(
        shimmerColors,
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f)
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Box(Modifier.height(18.dp).width(120.dp).clip(RoundedCornerShape(Dimens.RadiusSM)).background(brush))
            Box(Modifier.height(14.dp).width(80.dp).clip(RoundedCornerShape(Dimens.RadiusSM)).background(brush))
        }
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(Dimens.RadiusMD)).background(brush))
        }
    }
}

// ===================================================
// Real App List Section
// ===================================================

@Composable
fun RealAppListSection(
    apps: List<AppInfo>,
    totalCount: Int,
    onViewAllClick: () -> Unit,
    onCleanApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Dimens.SpacingXS),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.largest_cache_title),
                style = MaterialTheme.typography.titleSmall,
                color = PlatinumOnSurface,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onViewAllClick) {
                Text("Lihat Semua ($totalCount)", style = MaterialTheme.typography.labelMedium, color = PlatinumOnSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)) {
            apps.forEach { app ->
                RealAppCacheRowItem(app = app, onClean = { onCleanApp(app.packageName) })
            }
        }
    }
}

@Composable
fun RealAppCacheRowItem(
    app: AppInfo,
    onClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusMD),
        color = PlatinumSurfaceContainerLowest,
        shadowElevation = Dimens.ElevationSoft
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingMD),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMD),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(Dimens.RadiusSM))
                        .background(PlatinumSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = PlatinumOnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXXS)
                    ) {
                        Text(
                            app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            color = PlatinumOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isSystemApp) {
                            Surface(
                                shape = RoundedCornerShape(Dimens.RadiusXS),
                                color = PlatinumSurfaceContainerHigh
                            ) {
                                Text(
                                    text = "Sistem",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = PlatinumOnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${app.cacheSizeFormatted} • ${app.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Dimens.SpacingSM))
            TextButton(onClick = onClean, colors = ButtonDefaults.textButtonColors(contentColor = PlatinumPrimary)) {
                Text("Bersihkan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ===================================================
// V8: View All Inventory Bottom Sheet
// ===================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewAllInventorySheet(
    apps: List<AppInfo>,
    inventorySummary: InventorySummary,
    onCleanApp: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PackageCategoryFilter.ALL) }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                PackageCategoryFilter.ALL -> true
                PackageCategoryFilter.USER_ONLY -> !app.isSystemApp
                PackageCategoryFilter.SYSTEM_ONLY -> app.isSystemApp
            }
            matchesSearch && matchesFilter
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlatinumBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingLG)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpacingSM),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inventaris Aplikasi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlatinumOnSurface
                    )
                    Text(
                        text = "${inventorySummary.measurableCacheAppsCount} aplikasi ber-cache terdeteksi",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlatinumOnSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = PlatinumOnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }

            // Summary Metrics Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpacingMD),
                shape = RoundedCornerShape(Dimens.RadiusMD),
                color = PlatinumSurface,
                shadowElevation = Dimens.ElevationSoft
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.SpacingMD),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${inventorySummary.rawPackagesCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PlatinumOnSurface
                        )
                        Text(
                            text = "Dipindai",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${inventorySummary.userAppsCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PlatinumOnSurface
                        )
                        Text(
                            text = "Pengguna",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${inventorySummary.systemAppsCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PlatinumOnSurface
                        )
                        Text(
                            text = "Sistem",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${inventorySummary.measurableCacheAppsCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PlatinumOnSurface
                        )
                        Text(
                            text = "Ber-cache",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                }
            }

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama atau paket aplikasi...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpacingSM),
                shape = RoundedCornerShape(Dimens.RadiusMD),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "✕",
                            modifier = Modifier
                                .clickable { searchQuery = "" }
                                .padding(8.dp),
                            color = PlatinumOnSurfaceVariant
                        )
                    }
                }
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpacingSM),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
            ) {
                FilterChip(
                    selected = selectedFilter == PackageCategoryFilter.ALL,
                    onClick = { selectedFilter = PackageCategoryFilter.ALL },
                    label = { Text("Semua (${apps.size})", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedFilter == PackageCategoryFilter.USER_ONLY,
                    onClick = { selectedFilter = PackageCategoryFilter.USER_ONLY },
                    label = { Text("Pengguna (${apps.count { !it.isSystemApp }})", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedFilter == PackageCategoryFilter.SYSTEM_ONLY,
                    onClick = { selectedFilter = PackageCategoryFilter.SYSTEM_ONLY },
                    label = { Text("Sistem (${apps.count { it.isSystemApp }})", style = MaterialTheme.typography.labelSmall) }
                )
            }

            // App List
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada aplikasi yang cocok",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlatinumOnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXS)
                ) {
                    items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                        val app = filteredApps[index]
                        RealAppCacheRowItem(
                            app = app,
                            onClean = { onCleanApp(app.packageName) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(Dimens.Spacing2XL))
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(Dimens.RadiusMD), color = PlatinumSurfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(Dimens.SpacingMD), Arrangement.spacedBy(Dimens.SpacingSM), Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_shield_check), null, tint = PlatinumOnSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(message.ifBlank { stringResource(R.string.error_text) }, style = MaterialTheme.typography.bodySmall, color = PlatinumOnSurfaceVariant)
        }
    }
}
