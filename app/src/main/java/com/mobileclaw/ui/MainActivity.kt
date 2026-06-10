package com.mobileclaw.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileclaw.ClawApplication
import com.mobileclaw.ui.shell.MainPageHost
import com.mobileclaw.ui.shell.ClassicScaffold
import com.mobileclaw.ui.shell.ClassicShellContent
import com.mobileclaw.ui.shell.rememberClassicShellController
import java.util.Locale
import com.mobileclaw.R
import com.mobileclaw.str

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private var debugPageRequest by mutableStateOf<String?>(null)
    private var debugGoalRequest by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        val language = ClawApplication.instance.agentConfig.language
        super.attachBaseContext(wrapLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readDebugIntent(intent)

        val permissionManager = ClawApplication.instance.permissionManager

        setContent {
            val vm: MainViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            LaunchedEffect(debugPageRequest) {
                debugPageRequest?.let { pageName ->
                    runCatching { AppPage.valueOf(pageName.uppercase(Locale.ROOT)) }
                        .getOrNull()
                        ?.let { vm.navigate(it) }
                    debugPageRequest = null
                }
            }

            LaunchedEffect(debugGoalRequest) {
                debugGoalRequest?.let { goal ->
                    vm.navigate(AppPage.CHAT)
                    vm.runTask(goal)
                    debugGoalRequest = null
                }
            }

            val initialConfig = remember { ClawApplication.instance.agentConfig.snapshot() }
            val configSnapshot by uiState.config.collectAsState(initial = initialConfig)

            LaunchedEffect(Unit) {
                vm.languageChanged.collect { lang ->
                    ClawApplication.instance.applyLanguage(lang)
                    recreate()
                }
            }

            ClawTheme(
                darkTheme = configSnapshot.darkTheme,
                accentColor = configSnapshot.accentColor,
                language = configSnapshot.language,
            ) {
                val lightStatusBars = uiState.currentPage != AppPage.AI_TOWN && !configSnapshot.darkTheme
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = lightStatusBars
                }

                    // Launch MiniAppActivity when AI opens an app (e.g. after creation)
                    val pendingAppId = uiState.openAppId
                    LaunchedEffect(pendingAppId) {
                        if (pendingAppId != null) {
                            startActivity(MiniAppActivity.intent(this@MainActivity, pendingAppId))
                            vm.clearPendingAppOpen()
                        }
                    }

                    // Launch AiPageActivity or pin shortcut when AI requests it
                    val pendingAiPageId = uiState.openAiPageId
                    LaunchedEffect(pendingAiPageId) {
                        if (pendingAiPageId != null) {
                            if (pendingAiPageId.startsWith("pin:")) {
                                val pageId = pendingAiPageId.removePrefix("pin:")
                                val def = uiState.aiPages.firstOrNull { it.id == pageId }
                                if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                            } else {
                                startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, pendingAiPageId))
                            }
                            vm.clearAiPageOpen()
                        }
                    }

                    val htmlViewerOpen = uiState.openHtmlAttachment != null

                    // Back stack navigation
                    BackHandler(enabled = !htmlViewerOpen && uiState.canNavigateBack && uiState.currentPage != AppPage.BROWSER) {
                        vm.navigateBack()
                    }
                    // HOME -> exit: double-press within 2 s to exit
                    var lastBackPressMs by remember { mutableStateOf(0L) }
                    BackHandler(enabled = !htmlViewerOpen && !uiState.canNavigateBack && uiState.currentPage == AppPage.HOME) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastBackPressMs < 2000L) {
                            finish()
                        } else {
                            lastBackPressMs = now
                            Toast.makeText(this@MainActivity, str(R.string.mainactivity_515fdc), Toast.LENGTH_SHORT).show()
                        }
                    }

                    val classicShell = rememberClassicShellController(uiState.currentPage)

                    Box(modifier = Modifier.fillMaxSize()) {
                        val classicShowsRoot = classicShell.shouldRenderShellRoot(uiState.currentPage)
                        if (classicShowsRoot) {
                            ClassicScaffold(
                                selected = classicShell.tab,
                                onSelect = { tab ->
                                    classicShell.tab = tab
                                    classicShell.currentPageForBottomTab(tab)?.let(vm::navigate)
                                },
                                title = classicShell.title,
                                tabs = classicShell.topTabs.map { it.label to it.selected },
                                onTab = { index -> classicShell.applyTopTabSelection(index)?.let(vm::navigate) },
                            ) {
                                ClassicShellContent(
                                    uiState = uiState,
                                    classicShell = classicShell,
                                    vm = vm,
                                    onOpenApp = { appId -> startActivity(MiniAppActivity.intent(this@MainActivity, appId)) },
                                    onOpenAiPage = { startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, it)) },
                                    onPinAiPage = {
                                        val def = uiState.aiPages.firstOrNull { p -> p.id == it }
                                        if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                                    },
                                    onOpenAccessibilitySettings = { startActivity(permissionManager.openAccessibilitySettings()) },
                                )
                            }
                        } else {
                            // 经典模式下二级页不再强塞进 tab 根壳，否则 currentPage 已经切了，内容区仍停留在根页。
                            MainPageHost(
                                uiState = uiState,
                                vm = vm,
                                isClassicStyle = true,
                                darkTheme = configSnapshot.darkTheme,
                                onOpenDrawer = { vm.navigateBack() },
                                onOpenApp = { appId -> startActivity(MiniAppActivity.intent(this@MainActivity, appId)) },
                                onOpenAiPage = { startActivity(com.mobileclaw.ui.aipage.AiPageActivity.intent(this@MainActivity, it)) },
                                onPinAiPage = {
                                    val def = uiState.aiPages.firstOrNull { p -> p.id == it }
                                    if (def != null) com.mobileclaw.ui.aipage.ShortcutHelper.pinShortcut(this@MainActivity, def)
                                },
                                onOpenAccessibilitySettings = { startActivity(permissionManager.openAccessibilitySettings()) },
                            )
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDebugIntent(intent)
    }

    private fun readDebugIntent(intent: Intent?) {
        debugPageRequest = intent?.getStringExtra(DEBUG_PAGE_EXTRA)?.trim()?.takeIf { it.isNotBlank() }
        debugGoalRequest = intent?.getStringExtra(DEBUG_GOAL_B64_EXTRA)
            ?.let { encoded ->
                runCatching {
                    String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull()
            }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(DEBUG_GOAL_EXTRA)?.trim()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val DEBUG_PAGE_EXTRA = "mobileclaw.debug.page"
        private const val DEBUG_GOAL_EXTRA = "mobileclaw.debug.goal"
        private const val DEBUG_GOAL_B64_EXTRA = "mobileclaw.debug.goal.b64"

        fun wrapLocale(context: Context, language: String): Context {
            if (language == "auto" || language.isBlank()) return context
            val locale = Locale.forLanguageTag(language)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
