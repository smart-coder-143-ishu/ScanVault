package com.scanvault.app.admob

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Clean wrapper for Google AdMob Adaptive Banner Ads.
 * Uses official Google Test Ad Unit ID for banners: ca-app-pub-3940256099942544/6300978111
 * If ad fails to load or device is offline, renders a clean, non-deceptive placeholder.
 */
object AdMobConfig {
    // Standard Google AdMob test banner ad unit ID
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    // Set to true in release builds once real AdMob account & unit IDs are configured
    const val ENABLE_LIVE_ADS = false
}

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobConfig.TEST_BANNER_AD_UNIT_ID
) {
    var adFailedToLoad by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!adFailedToLoad) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context: Context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                super.onAdFailedToLoad(loadAdError)
                                adFailedToLoad = true
                            }

                            override fun onAdLoaded() {
                                super.onAdLoaded()
                                adFailedToLoad = false
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        } else {
            // Elegant, transparent development placeholder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "RESERVED ADVERTISING SLOT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Google AdMob Adaptive Banner (Test ID configured)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
