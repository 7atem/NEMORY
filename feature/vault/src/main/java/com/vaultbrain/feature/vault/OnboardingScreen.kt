package com.vaultbrain.feature.vault

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val offerModelDownload by viewModel.offerModelDownload.collectAsState()
    val gemmaStatus by viewModel.gemmaStatus.collectAsState()
    val slides = listOf(
        Triple(stringResource(R.string.onboarding_vault_title), stringResource(R.string.onboarding_vault_body), Icons.Default.Storage),
        Triple(stringResource(R.string.onboarding_organize_title), stringResource(R.string.onboarding_organize_body), Icons.Default.DocumentScanner),
        Triple(stringResource(R.string.onboarding_private_title), stringResource(R.string.onboarding_private_body), Icons.Default.Lock)
    )
    // The on-device model offer is appended as a final step on eligible devices only.
    val pageCount = slides.size + if (offerModelDownload) 1 else 0
    val modelPageIndex = if (offerModelDownload) pageCount - 1 else -1

    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == modelPageIndex) {
                    ModelDownloadPage(status = gemmaStatus)
                    return@HorizontalPager
                }
                // Calculate page offset for parallax effect
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val rtlMultiplier = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer {
                                // Parallax and fade effect
                                translationX = pageOffset * 200f * rtlMultiplier
                                alpha = 1f - pageOffset
                                scaleX = 1f - (pageOffset * 0.2f)
                                scaleY = 1f - (pageOffset * 0.2f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (page == 1) {
                            MockScannerAnimation()
                        } else {
                            Icon(
                                imageVector = slides[page].third,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = slides[page].first,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                translationX = pageOffset * 100f * rtlMultiplier
                                alpha = 1f - pageOffset
                            }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = slides[page].second,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .graphicsLayer {
                                translationX = pageOffset * 50f * rtlMultiplier
                                alpha = 1f - pageOffset
                            }
                    )
                }
            }

            // Pager indicators
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                    )
                }
            }

            // Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    pagerState.currentPage == modelPageIndex -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (gemmaStatus == OnDeviceModelStatus.READY) {
                                Button(
                                    onClick = onOnboardingComplete,
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text(stringResource(R.string.onboarding_start))
                                }
                            } else {
                                Button(
                                    onClick = viewModel::downloadModel,
                                    enabled = gemmaStatus == OnDeviceModelStatus.NOT_DOWNLOADED ||
                                        gemmaStatus == OnDeviceModelStatus.ERROR,
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Text(stringResource(R.string.onboarding_model_download))
                                }
                                TextButton(onClick = onOnboardingComplete) {
                                    Text(stringResource(R.string.onboarding_model_later))
                                }
                            }
                        }
                    }
                    pagerState.currentPage == slides.lastIndex && !offerModelDownload -> {
                        Button(
                            onClick = onOnboardingComplete,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_start))
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onOnboardingComplete() }) {
                                Text(stringResource(R.string.onboarding_skip))
                            }
                            Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                                Text(stringResource(R.string.onboarding_next))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDownloadPage(status: OnDeviceModelStatus) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (status == OnDeviceModelStatus.READY) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(
                if (status == OnDeviceModelStatus.READY) R.string.onboarding_model_ready
                else R.string.onboarding_model_title
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_model_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (status is OnDeviceModelStatus.DOWNLOADING) {
            val progressVal = status.progress / 100f
            LinearProgressIndicator(
                progress = { progressVal },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_model_downloading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (status == OnDeviceModelStatus.ERROR) {
            Text(
                text = stringResource(R.string.onboarding_model_error),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        } else if (status != OnDeviceModelStatus.READY) {
            Text(
                text = stringResource(R.string.onboarding_model_trust),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MockScannerAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_position"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.TopCenter
    ) {
        // Document content lines
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
        }

        // Scanner line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .graphicsLayer {
                    translationY = scanPosition
                }
                .background(Color.Green.copy(alpha = 0.8f))
        )
        
        // Scan area overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer {
                    translationY = scanPosition - 40f
                }
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Green.copy(alpha = 0.2f))
                    )
                )
        )
    }
}
