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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
 * Screenshot protection and the PIN gate are disabled at the owner's request.
 *
 * Auto-lock: tracks the timestamp when the activity is paused. On resume, if a PIN
 * is set and the elapsed time exceeds [AuthManager.autoLockMinutes], the activity
 * restarts. Inert while the PIN gate is disabled (no PIN is ever set).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authManager: AuthManager


    @Inject
    lateinit var gemmaModelManager: com.vaultbrain.core.ai.llm.gemma.GemmaModelManager

    @Inject
    lateinit var gemmaDownloadPromptManager: com.vaultbrain.core.notifications.GemmaDownloadPromptManager

    private var pausedAtMillis: Long = 0L
    private var navigationRequest by mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = savedInstanceState?.getInt("navigation_request") ?: 0
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Screenshot protection is disabled at the owner's request.

        setupShortcuts()
        reportShortcutUsage(intent)
        handleGemmaPromptAction(intent)

        com.vaultbrain.core.common.preferences.AppearancePreferences.init(applicationContext)
        setContent {
            val crashFile = java.io.File(filesDir, "vaultbrain_crash.txt")
            if (crashFile.exists()) {
                val crashText = crashFile.readText()
                MaterialTheme {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        val lines = crashText.lines()
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                androidx.compose.material3.Text(
                                    text = getString(R.string.crash_log_found),
                                    color = androidx.compose.ui.graphics.Color.Red,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            item {
                                androidx.compose.foundation.layout.Row {
                                    androidx.compose.material3.Button(
                                        onClick = { crashFile.delete(); finish(); startActivity(Intent(this@MainActivity, MainActivity::class.java)) },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        androidx.compose.material3.Text(getString(R.string.crash_clear_restart))
                                    }
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Crash Log", getString(R.string.crash_copy_warning) + "\n\n" + crashText)
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, getString(R.string.crash_copied_toast), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    ) {
                                        androidx.compose.material3.Text(getString(R.string.crash_copy_log))
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

            val windowSize = calculateWindowSizeClass(this@MainActivity)

            VaultBrainTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasSeenOnboarding = com.vaultbrain.core.common.OnboardingState.hasSeenOnboarding(this@MainActivity)
                    VaultBrainNavGraph(
                        windowWidthSizeClass = windowSize.widthSizeClass,
                        startDestination = if (hasSeenOnboarding) {
                            // PIN gate disabled at the owner's request; go straight to Today.
                            com.vaultbrain.app.navigation.Screen.Home.route
                        } else {
                            com.vaultbrain.app.navigation.Screen.Onboarding.route
                        },
                        destinationAfterUnlock = shortcutDestination(intent),
                        launchRequestId = navigationRequest
                    )
                }
            }
        }
    }

    private fun setupShortcuts() {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
            
            val captureShortcut = ShortcutInfo.Builder(this, "shortcut_capture")
                .setShortLabel(getString(R.string.nav_capture))
                .setLongLabel(getString(R.string.shortcut_capture_long))
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_camera))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = ACTION_CAPTURE
                })
                .build()

            val expiringShortcut = ShortcutInfo.Builder(this, "shortcut_expiring")
                .setShortLabel(getString(R.string.shortcut_expiring))
                .setLongLabel(getString(R.string.shortcut_expiring))
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
                    ?.let(com.vaultbrain.shared.domain.LensId::canonicalOrNull)
                    ?.let { "lens/${android.net.Uri.encode(it)}" }
                    ?: com.vaultbrain.app.navigation.Screen.Home.route
                "brain" -> com.vaultbrain.app.navigation.Screen.Brain.route
                "collection" -> deepLink.pathSegments.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let { "collection/${android.net.Uri.encode(it)}" }
                    ?: com.vaultbrain.app.navigation.Screen.Home.route
                else -> com.vaultbrain.app.navigation.Screen.Home.route
            }
        }

        return when (intent?.action) {
            ACTION_CAPTURE -> {
                val source = intent.getStringExtra("EXTRA_CAPTURE_SOURCE")
                if (source != null) {
                    "${com.vaultbrain.app.navigation.Screen.Capture.route}?source=${android.net.Uri.encode(source)}"
                } else {
                    com.vaultbrain.app.navigation.Screen.Capture.route
                }
            }
            ACTION_VIEW_EXPIRING -> "${com.vaultbrain.app.navigation.Screen.Search.route}?filter=expiring"
            ACTION_OPEN_SETTINGS -> com.vaultbrain.app.navigation.Screen.Settings.route
            ACTION_ASK_NEMORY -> com.vaultbrain.app.navigation.Screen.Brain.route
            ACTION_VIEW_VAULT -> com.vaultbrain.app.navigation.Screen.Search.route
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
        navigationRequest++
        reportShortcutUsage(intent)
        handleGemmaPromptAction(intent)
    }

    override fun onPause() {
        super.onPause()
        pausedAtMillis = System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("navigation_request", navigationRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (pausedAtMillis > 0L && authManager.isPinSet && authManager.autoLockMinutes > 0) {
            val elapsedMs = System.currentTimeMillis() - pausedAtMillis
            val thresholdMs = authManager.autoLockMinutes * 60_000L
            if (elapsedMs >= thresholdMs) {
                pausedAtMillis = 0L
                finish()
                startActivity(Intent(this@MainActivity, MainActivity::class.java))
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
        const val ACTION_ASK_NEMORY = "com.nemory.app.ACTION_ASK_NEMORY"
        const val ACTION_VIEW_VAULT = "com.nemory.app.ACTION_VIEW_VAULT"
        private const val SHORTCUT_CAPTURE = "shortcut_capture"
        private const val SHORTCUT_EXPIRING = "shortcut_expiring"
    }
}
