package com.mobileclaw.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mobileclaw.vpn.ProxyConfig
import com.mobileclaw.vpn.VpnStatus
import com.mobileclaw.vpn.VpnSubscription
import androidx.compose.ui.res.stringResource
import com.mobileclaw.R
import com.mobileclaw.str

private val V_BG = Color(0xFF050505)
private val V_SURFACE = Color(0xFF0D0D0D)
private val V_CARD = Color(0xFF151515)
private val V_BORDER = Color(0xFF292929)
private val V_TEXT = Color(0xFFF7F7F4)
private val V_SUB = Color(0xFFA0A0A0)
private val V_ACCENT = Color(0xFFC7F43A)
private val V_GREEN = Color(0xFF56D6BA)

@Composable
fun VpnPage(
    uiState: MainUiState,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedSubId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val vpnPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selected = uiState.vpnSubscriptions.firstNotNullOfOrNull { sub ->
                val proxy = sub.proxies.firstOrNull { it.id == sub.entity.selectedProxyId }
                if (proxy != null) sub to proxy else null
            }
            selected?.let { (sub, proxy) -> vm.startVpn(sub, proxy) }
        }
    }

    LaunchedEffect(Unit) {
        vm.syncVpnStatus()
        vm.initVpn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V_BG)
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = null, tint = V_SUB)
            }
            Text(
                str(R.string.vpn_23cd83),
                color = V_TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = str(R.string.user_config_add), tint = V_ACCENT)
            }
        }

        // Global VPN switch
        VpnToggleCard(
            status = uiState.vpnStatus,
            activeProxyName = uiState.vpnActiveProxyName
                ?: uiState.vpnSubscriptions.firstNotNullOfOrNull { sub ->
                    sub.proxies.firstOrNull { it.id == sub.entity.selectedProxyId }?.name
                },
            onToggle = { enable ->
                if (enable) {
                    val prepareIntent = vm.vpnPrepareIntent()
                    if (prepareIntent != null) {
                        vpnPermLauncher.launch(prepareIntent)
                    } else {
                        val selected = uiState.vpnSubscriptions.firstNotNullOfOrNull { sub ->
                            val proxy = sub.proxies.firstOrNull { it.id == sub.entity.selectedProxyId }
                            if (proxy != null) sub to proxy else null
                        }
                        selected?.let { (sub, proxy) -> vm.startVpn(sub, proxy) }
                    }
                } else {
                    vm.stopVpn()
                }
            },
        )

        if (uiState.vpnStatus == VpnStatus.CONNECTED) {
            Spacer(Modifier.height(6.dp))
            DiagnosticRow(vm)
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.vpnSubscriptions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(str(R.string.vpn_empty), color = V_SUB, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(str(R.string.vpn_tap), color = V_SUB.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.vpnSubscriptions, key = { it.entity.id }) { sub ->
                    SubscriptionCard(
                        sub = sub,
                        expanded = expandedSubId == sub.entity.id,
                        latencies = uiState.vpnLatencies,
                        onExpand = {
                            expandedSubId = if (expandedSubId == sub.entity.id) null else sub.entity.id
                        },
                        onUpdate = { vm.updateVpnSubscription(sub) },
                        onDelete = { deleteConfirmId = sub.entity.id },
                        onSelectProxy = { proxyId -> vm.selectVpnProxy(sub.entity.id, proxyId) },
                        onSpeedTest = { vm.testAllVpnLatencies(sub) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            adding = uiState.vpnAddingSubscription,
            onAdd = { name, url ->
                vm.addVpnSubscription(name, url)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    deleteConfirmId?.let { id ->
        Dialog(onDismissRequest = { deleteConfirmId = null }) {
            Column(
                modifier = Modifier
                    .background(V_CARD, RoundedCornerShape(16.dp))
                    .border(1.dp, V_BORDER, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(str(R.string.vpn_delete), color = V_TEXT, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(str(R.string.vpn_ok), color = V_SUB, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { deleteConfirmId = null },
                        modifier = Modifier.weight(1f),
                    ) { Text(str(R.string.btn_cancel), color = V_SUB) }
                    Button(
                        onClick = { vm.deleteVpnSubscription(id); deleteConfirmId = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101010)),
                        modifier = Modifier.weight(1f),
                    ) { Text(str(R.string.skills_delete_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun VpnToggleCard(
    status: VpnStatus,
    activeProxyName: String?,
    onToggle: (Boolean) -> Unit,
) {
    val isConnected = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING
    val statusColor = when (status) {
        VpnStatus.CONNECTED -> V_GREEN
        VpnStatus.CONNECTING -> V_ACCENT
        else -> V_SUB
    }
    val statusText = when (status) {
        VpnStatus.CONNECTED -> if (activeProxyName != null) str(R.string.vpn_connected, activeProxyName) else str(R.string.vpn_connected_no_proxy)
        VpnStatus.CONNECTING -> str(R.string.vpn_connect)
        VpnStatus.ERROR -> str(R.string.vpn_connect_2)
        VpnStatus.IDLE -> str(R.string.vpn_not)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(V_CARD, RoundedCornerShape(12.dp))
            .border(1.dp, if (isConnected) V_GREEN.copy(alpha = 0.3f) else V_BORDER, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.12f))
                .border(1.5.dp, statusColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = V_ACCENT, strokeWidth = 2.dp)
            } else {
                ClawSymbolIcon("vpn", tint = V_TEXT, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(str(R.string.vpn_fbb570), color = V_TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(statusText, color = statusColor, fontSize = 12.sp)
        }
        Switch(
            checked = isConnected || isConnecting,
            onCheckedChange = { enabled ->
                if (!isConnecting || !enabled) onToggle(enabled)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = V_GREEN,
                checkedTrackColor = V_GREEN.copy(alpha = 0.3f),
                uncheckedThumbColor = V_SUB,
                uncheckedTrackColor = V_BORDER,
            ),
        )
    }
}

@Composable
private fun DiagnosticRow(vm: MainViewModel) {
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(V_CARD, RoundedCornerShape(12.dp))
            .border(1.dp, V_BORDER, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.vpn_ddb832), color = V_TEXT, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                result ?: stringResource(R.string.vpn_8dde85),
                color = V_SUB,
                fontSize = 11.sp,
            )
        }
        TextButton(
            onClick = {
                if (!testing) {
                    testing = true
                    result = str(R.string.vpn_6c501b)
                    vm.testVpnProxyReachable { msg ->
                        result = msg
                        testing = false
                    }
                }
            },
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = V_ACCENT, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.vpn_db06c7), color = V_ACCENT, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    sub: VpnSubscription,
    expanded: Boolean,
    latencies: Map<String, Long>,
    onExpand: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onSelectProxy: (String) -> Unit,
    onSpeedTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(V_CARD, RoundedCornerShape(14.dp))
            .border(1.dp, V_BORDER, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sub.entity.name, color = V_TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    str(R.string.proxy_count, sub.proxies.size),
                    color = V_SUB,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onSpeedTest, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Speed, contentDescription = str(R.string.vpn_c7f8d9), tint = V_ACCENT, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onUpdate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = str(R.string.vpn_update), tint = V_SUB, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = str(R.string.skills_delete_confirm), tint = V_SUB.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = V_SUB,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                HorizontalDivider(color = V_BORDER, thickness = 0.5.dp)
                if (sub.proxies.isEmpty()) {
                    Text(
                        str(R.string.vpn_empty_2),
                        color = V_SUB,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    sub.proxies.forEach { proxy ->
                        ProxyRow(
                            proxy = proxy,
                            selected = proxy.id == sub.entity.selectedProxyId,
                            latencyMs = latencies[proxy.id],
                            onSelect = { onSelectProxy(proxy.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyRow(
    proxy: ProxyConfig,
    selected: Boolean,
    latencyMs: Long?,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .background(if (selected) V_GREEN.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) V_GREEN else V_BORDER),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(proxy.name, color = V_TEXT, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text("${proxy.typeName}  ${proxy.server}:${proxy.port}", color = V_SUB, fontSize = 11.sp)
        }
        LatencyChip(latencyMs)
    }
}

@Composable
private fun LatencyChip(latencyMs: Long?) {
    when {
        latencyMs == null -> {}
        latencyMs == LATENCY_TESTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = V_ACCENT,
                strokeWidth = 1.5.dp,
            )
        }
        latencyMs == LATENCY_ERROR -> {
            Text(stringResource(R.string.vpn_e944c7), color = V_SUB, fontSize = 11.sp)
        }
        else -> {
            val color = when {
                latencyMs < 200  -> V_GREEN
                latencyMs < 500  -> V_ACCENT
                else             -> V_SUB
            }
            Text("${latencyMs}ms", color = color, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    adding: Boolean,
    onAdd: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!adding) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(V_CARD, RoundedCornerShape(16.dp))
                .border(1.dp, V_BORDER, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(str(R.string.vpn_add), color = V_TEXT, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(str(R.string.role_field_name), color = V_SUB) },
                placeholder = { Text(str(R.string.vpn_f3cb07), color = V_SUB.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = dialogTextFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(str(R.string.vpn_701515), color = V_SUB) },
                placeholder = { Text("https://...", color = V_SUB.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = dialogTextFieldColors(),
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !adding,
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.btn_cancel), color = V_SUB) }
                Button(
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) onAdd(name.trim(), url.trim())
                    },
                    enabled = !adding && name.isNotBlank() && url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = V_ACCENT),
                    modifier = Modifier.weight(1f),
                ) {
                    if (adding) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(str(R.string.user_config_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = V_TEXT,
    unfocusedTextColor = V_TEXT,
    focusedBorderColor = V_ACCENT.copy(alpha = 0.6f),
    unfocusedBorderColor = V_BORDER,
    cursorColor = V_ACCENT,
)
