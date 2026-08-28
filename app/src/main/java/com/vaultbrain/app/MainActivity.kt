package com.vaultbrain.app

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.vaultbrain.app.navigation.VaultBrainNavGraph
import com.vaultbrain.app.ui.theme.VaultBrainTheme
import com.vaultbrain.core.security.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * Main activity hosts the Compose navigation graph.
 *
 * [FLAG_SECURE] is applied to all activities to prevent screenshots in the recents
 * overview and by third-party tools.
 *
 * Auto-lock: tracks the timestamp when the activity is paused. On resume, if
 * the elapsed time exceeds [AuthManager.autoLockMinutes], the activity navigates
 * back to the auth gate.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var featureGate: com.vaultbrain.core.billing.FeatureGate

    @Inject
    lateinit var gemmaModelManager: com.vaultbrain.core.ai.llm.gemma.GemmaModelManager

    @Inject
    lateinit var gemmaDownloadPromptManager: com.vaultbrain.core.notifications.GemmaDownloadPromptManager

    private var pausedAtMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setupShortcuts()
        reportShortcutUsage(intent)
        handleGemmaPromptAction(intent)

        com.vaultbrain.core.common.preferences.AppearancePreferences.init(applicationContext)
        setContent {
            val crashFile = java.io.File(getExternalFilesDir(null), "vaultbrain_crash.txt")
            if (crashFile.exists()) {
                val crashText = crashFile.readText()
                MaterialTheme {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        val lines = crashText.lines()
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                androidx.compose.material3.Text(
                                    text = "CRASH LOG FOUND:",
                                    color = androidx.compose.ui.graphics.Color.Red,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            item {
                                androidx.compose.foundation.layout.Row {
                                    androidx.compose.material3.Button(
                                        onClick = { crashFile.delete(); finish(); startActivity(intent) },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        androidx.compose.material3.Text("Clear & Restart")
                                    }
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, crashText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Crash Log"))
                                        }
                                    ) {
                                        androidx.compose.material3.Text("Share Log")
                                    }
                                }
                            }
                            items(lines.size) { index ->
                                androidx.compose.material3.Text(
                                    text = lines[index],
                                    color = androidx.compose.ui.graphics.Color.Red,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                return@setContent
            }

            val themeMode by com.vaultbrain.core.common.preferences.AppearancePreferences.themeMode.collectAsState()
            val dynamicColor by com.vaultbrain.core.common.preferences.AppearancePreferences.dynamicColor.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                com.vaultbrain.core.common.preferences.ThemeMode.LIGHT -> false
                com.vaultbrain.core.common.preferences.ThemeMode.DARK -> true
                com.vaultbrain.core.common.preferences.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            VaultBrainTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasSeenOnboarding = com.vaultbrain.core.common.OnboardingState.hasSeenOnboarding(this@MainActivity)
                    VaultBrainNavGraph(
                        startDestination = if (hasSeenOnboarding) {
                            com.vaultbrain.app.navigation.Screen.AuthGate.route
                        } else {
                            com.vaultbrain.app.navigation.Screen.Onboarding.route
                        },
                        destinationAfterUnlock = shortcutDestination(intent)
                    )
                }
            }
        }
    }

    private fun setupShortcuts() {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
            
            val captureShortcut = ShortcutInfo.Builder(this, "shortcut_capture")
                .setShortLabel("Quick Dump")
                .setLongLabel("Capture to Vault")
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_camera))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = ACTION_CAPTURE
                })
                .build()

            val expiringShortcut = ShortcutInfo.Builder(this, "shortcut_expiring")
                .setShortLabel("Expiring")
                .setLongLabel("View Expiring Items")
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_today))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = ACTION_VIEW_EXPIRING
                })
                .build()

        shortcutManager.dynamicShortcuts = listOf(captureShortcut, expiringShortcut)
    }

    private fun shortcutDestination(intent: Intent?): String {
        val deepLink = intent?.data
        if (deepLink?.scheme == "nemory") {
            return when (deepLink.host) {
                "item" -> deepLink.pathSegments.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let { "detail/${android.net.Uri.encode(it)}" }
                    ?: com.vaultbrain.app.navigation.Screen.Home.route
                "lens" -> deepLink.pathSegments.firstOrNull()
                    ?.let(com.vaultbrain.core.common.model.LensId::canonicalOrNull)
                    ?.let { "lens/${android.net.Uri.encode(it)}" }
                    ?: com.vaultbrain.app.navigation.Screen.Home.route
                "brain" -> com.vaultbrain.app.navigation.Screen.Brain.route
                else -> com.vaultbrain.app.navigation.Screen.Home.route
            }
        }

        return when (intent?.action) {
            ACTION_CAPTURE -> {
                val source = intent.getStringExtra("EXTRA_CAPTURE_SOURCE")
                if (source != null) {
                    "${com.vaultbrain.app.navigation.Screen.Capture.route}?source=$source"
                } else {
                    com.vaultbrain.app.navigation.Screen.Capture.route
                }
            }
            ACTION_VIEW_EXPIRING -> "${com.vaultbrain.app.navigation.Screen.Search.route}?filter=expiring"
            ACTION_OPEN_SETTINGS -> com.vaultbrain.app.navigation.Screen.Settings.route
            else -> com.vaultbrain.app.navigation.Screen.Home.route
        }
    }

    /** The notification's Download action enqueues the model download without navigation. */
    private fun handleGemmaPromptAction(intent: Intent?) {
        if (intent?.action != ACTION_DOWNLOAD_GEMMA_MODEL) return
        runCatching {
            gemmaModelManager.downloadModel()
            gemmaDownloadPromptManager.dismissPrompt()
        }
    }

    private fun reportShortcutUsage(intent: Intent?) {
        val shortcutId = when (intent?.action) {
            ACTION_CAPTURE -> SHORTCUT_CAPTURE
            ACTION_VIEW_EXPIRING -> SHORTCUT_EXPIRING
            else -> return
        }
        getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(shortcutId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reportShortcutUsage(intent)
        handleGemmaPromptAction(intent)
    }

    override fun onPause() {
        super.onPause()
        pausedAtMillis = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { featureGate.syncEntitlementWithBilling() }
        }
        if (pausedAtMillis > 0L && authManager.autoLockMinutes > 0) {
            val elapsedMs = System.currentTimeMillis() - pausedAtMillis
            val thresholdMs = authManager.autoLockMinutes * 60_000L
            if (elapsedMs >= thresholdMs) {
                pausedAtMillis = 0L
                finish()
                startActivity(intent)
                return
            }
        }
        pausedAtMillis = 0L
    }

    companion object {
        const val ACTION_CAPTURE = "com.nemory.app.ACTION_CAPTURE"
        const val ACTION_VIEW_EXPIRING = "com.nemory.app.ACTION_VIEW_EXPIRING"
        const val ACTION_OPEN_SETTINGS = "com.nemory.app.ACTION_OPEN_SETTINGS"
        const val ACTION_DOWNLOAD_GEMMA_MODEL = "com.nemory.app.ACTION_DOWNLOAD_GEMMA_MODEL"
        private const val SHORTCUT_CAPTURE = "shortcut_capture"
        private const val SHORTCUT_EXPIRING = "shortcut_expiring"
    }
}
