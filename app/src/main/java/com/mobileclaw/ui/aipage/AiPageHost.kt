package com.mobileclaw.ui.aipage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.LocalClawColors

/**
 * Full-screen composable that hosts an AI page.
 * Creates an AiPageRuntime, renders the page layout, handles back navigation.
 */
@Composable
fun AiPageHost(
    def: AiPageDef,
    onBack: () -> Unit,
    onNavigatePage: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val runtime = remember(def.id) { AiPageRuntime(def, context, onNavigatePage) }
    DisposableEffect(def.id) { onDispose { runtime.dispose() } }

    val c = LocalClawColors.current

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = c.subtext,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                ClawSymbolIcon(def.icon, tint = c.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = def.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                if (runtime.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = c.accent,
                    )
                }
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        // ── Page content ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AiPageRenderer(layout = def.layout, runtime = runtime)
        }
    }
}
