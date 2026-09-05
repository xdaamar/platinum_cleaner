package com.example.platinumcleaner.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platinumcleaner.ui.theme.AccentPurple
import com.example.platinumcleaner.ui.theme.PrimaryCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
    var isCleaning by remember { mutableStateOf(false) }
    var cleanProgress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = if (isCleaning) 1.1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCleaning) 400 else 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Platinum",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Cleaner",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = PrimaryCyan
        )

        Spacer(modifier = Modifier.height(60.dp))

        // System Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                title = "Storage",
                value = if (isCleaning) "Optimizing..." else "45% Used",
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            StatusCard(
                title = "RAM",
                value = if (isCleaning) "Boosting..." else "78% Used",
                icon = Icons.Default.Memory,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Central Clean Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(PrimaryCyan, AccentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (!isCleaning) {
                        isCleaning = true
                        coroutineScope.launch {
                            for (i in 1..100) {
                                delay(30)
                                cleanProgress = i / 100f
                            }
                            isCleaning = false
                            cleanProgress = 0f
                        }
                    }
                },
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isCleaning) "${(cleanProgress * 100).toInt()}%" else "CLEAN",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    if (!isCleaning) {
                        Text(
                            text = "TAP TO BOOST",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (isCleaning) {
            LinearProgressIndicator(
                progress = { cleanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PrimaryCyan,
                trackColor = Color.DarkGray
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun StatusCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
