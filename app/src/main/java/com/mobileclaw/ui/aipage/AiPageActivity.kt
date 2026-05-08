package com.mobileclaw.ui.aipage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.mobileclaw.ClawApplication
import com.mobileclaw.ui.ClawTheme
import com.mobileclaw.ui.LocalClawColors

/**
 * Standalone Activity that hosts an AI page, launched from a launcher shortcut or
 * from ui_builder(action=open). Receives the page ID via intent extra "page_id".
 */
class AiPageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pageId = intent?.getStringExtra(EXTRA_PAGE_ID) ?: run { finish(); return }
        val store = ClawApplication.instance.aiPageStore
        val config = ClawApplication.instance.agentConfig
        val initialConfig = config.snapshot()

        setContent {
            val pages by store.pages.collectAsState()
            val configFlow by config.configFlow.collectAsState(initial = initialConfig)
            val def = pages.firstOrNull { it.id == pageId }

            ClawTheme(darkTheme = configFlow.darkTheme, accentColor = configFlow.accentColor) {
                val c = LocalClawColors.current
                if (def != null) {
                    AiPageHost(
                        def = def,
                        onBack = { finish() },
                        onNavigatePage = { targetId ->
                            val targetDef = pages.firstOrNull { it.id == targetId }
                            if (targetDef != null) {
                                startActivity(intent(this@AiPageActivity, targetId))
                            }
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(c.bg).systemBarsPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Page not found: $pageId", color = c.subtext, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PAGE_ID = "page_id"

        fun intent(context: android.content.Context, pageId: String) =
            android.content.Intent(context, AiPageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_ID, pageId)
            }
    }
}
