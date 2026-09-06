package com.example.platinumcleaner.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.platinumcleaner.R
import com.example.platinumcleaner.ui.theme.Dimens
import com.example.platinumcleaner.ui.theme.PlatinumBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(DashboardNavTab.DASHBOARD) }
    var metricState by remember { mutableStateOf(StorageMetricState()) }
    val coroutineScope = rememberCoroutineScope()

    val mockAppList = remember {
        listOf(
            AppCacheItem(
                id = "telegram",
                nameRes = R.string.app_telegram_name,
                packageRes = R.string.app_telegram_package,
                sizeRes = R.string.app_telegram_size,
                iconRes = R.drawable.ic_send,
                isPrimary = true
            ),
            AppCacheItem(
                id = "spotify",
                nameRes = R.string.app_spotify_name,
                packageRes = R.string.app_spotify_package,
                sizeRes = R.string.app_spotify_size,
                iconRes = R.drawable.ic_graphic_eq
            ),
            AppCacheItem(
                id = "chrome",
                nameRes = R.string.app_chrome_name,
                packageRes = R.string.app_chrome_package,
                sizeRes = R.string.app_chrome_size,
                iconRes = R.drawable.ic_public
            )
        )
    }

    val onCleanClick: () -> Unit = {
        metricState = metricState.copy(isPurging = true)
        coroutineScope.launch {
            delay(1400)
            metricState = metricState.copy(
                isPurging = false,
                isCleaned = true,
                sweepProgress = 0f,
                reclaimableAmount = "0.0"
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopHeaderBar()
        },
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
                .background(PlatinumBackground),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + Dimens.SpacingMD,
                bottom = innerPadding.calculateBottomPadding() + Dimens.Spacing2XL,
                start = Dimens.SpacingLG,
                end = Dimens.SpacingLG
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXL)
        ) {
            item {
                GreetingSection()
            }

            item {
                HeroStorageCard(state = metricState)
            }

            item {
                PrimaryActionButton(
                    state = metricState,
                    onClick = onCleanClick
                )
            }

            item {
                LargestCacheSection(items = mockAppList)
            }

            item {
                InsightCard()
            }

            item {
                Spacer(modifier = Modifier.height(Dimens.SpacingLG))
            }
        }
    }
}
