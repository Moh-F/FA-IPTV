package com.mohdev.faiptv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.LanServer
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Account
import com.mohdev.faiptv.data.AccountType
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun WelcomeScreen() {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Brand.Navy, Brand.Navy2))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.acc_welcome), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.acc_welcome_sub), color = androidx.compose.ui.graphics.Color(0xFFB6BEDF), textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            AddButtons()
            Spacer(Modifier.height(24.dp))
            PhoneInputHint()
        }
    }
}

@Composable
private fun AddButtons() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
        Button(onClick = { Nav.push(Route.AccountEdit(null, AccountType.XTREAM)) }, Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acc_add_xtream))
        }
        OutlinedButton(onClick = { Nav.push(Route.AccountEdit(null, AccountType.M3U)) }, Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acc_add_m3u))
        }
    }
}

@Composable
private fun PhoneInputHint() {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.acc_from_phone), fontWeight = FontWeight.Bold)
        if (Prefs.s.lanServer && LanServer.url.isNotBlank()) {
            Text(stringResource(R.string.acc_from_phone_sub), fontSize = 13.sp, textAlign = TextAlign.Center)
            Text(LanServer.url, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        } else {
            TextButton(onClick = { Prefs.update { it.copy(lanServer = true) } }) { Text(stringResource(R.string.acc_from_phone_enable)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen() {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<Account?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.nav_accounts), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        items(Prefs.accounts, key = { it.id }) { a ->
            val active = Prefs.active?.id == a.id
            FocusCard(onClick = { Prefs.update { it.copy(activeAccount = a.id) } }, modifier = Modifier.fillMaxWidth(), selected = active, zoom = 1.01f) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                        Text(a.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(if (a.type == AccountType.XTREAM) "Xtream" else "M3U", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    val lines = buildList {
                        if (a.type == AccountType.XTREAM) {
                            add(a.server.substringAfter("://"))
                            if (a.status.isNotBlank()) add(a.status)
                            add(if (a.expires > 0) stringResource(R.string.acc_expires, fmtDay(a.expires)) else stringResource(R.string.acc_unlimited))
                            if (a.maxConnections > 0) add(stringResource(R.string.acc_connections, a.maxConnections))
                        } else add(if (a.m3uUrl.startsWith("file:")) stringResource(R.string.acc_add_file) else a.m3uUrl.substringAfter("://").substringBefore("?").take(60))
                    }
                    Text(lines.joinToString("  •  "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    if (active) {
                        Repo.lib?.raw?.let { l ->
                            Text(
                                stringResource(R.string.acc_counts, l.live.size, l.movies.size, l.series.size) + "  •  " + fmtDay(l.loadedAt) + " " + fmtTime(l.loadedAt),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!active) OutlinedButton(onClick = { Prefs.update { it.copy(activeAccount = a.id) } }) { Text(stringResource(R.string.acc_use)) }
                        else OutlinedButton(onClick = { scope.launch { Repo.refresh(a) } }, enabled = !Repo.loading) {
                            if (Repo.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.refresh))
                        }
                        OutlinedButton(onClick = { Nav.push(Route.AccountEdit(a, a.type)) }) { Text(stringResource(R.string.action_edit)) }
                        OutlinedButton(onClick = { confirmDelete = a }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); AddButtons() }
        item { PhoneInputHint() }
    }
    confirmDelete?.let { a ->
        ConfirmDialog(stringResource(R.string.acc_delete_confirm, a.name), onDismiss = { confirmDelete = null }) {
            Prefs.deleteAccount(a.id)
            File(Repo.playlistDir(), "${a.id}.m3u").delete()
            if (Repo.lib?.raw?.accountId == a.id) Repo.clear()
        }
    }
}

@Composable
fun AccountEditScreen(existing: Account?, type0: AccountType) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(type0) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var server by rememberSaveable { mutableStateOf(existing?.server ?: "") }
    var user by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.m3uUrl?.takeUnless { it.startsWith("file:") } ?: "") }
    var file by rememberSaveable { mutableStateOf(existing?.m3uUrl?.takeIf { it.startsWith("file:") } ?: "") }
    var epg by rememberSaveable { mutableStateOf(existing?.epgUrl ?: "") }
    var ua by rememberSaveable { mutableStateOf(existing?.userAgent ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { ins -> File(Repo.playlistDir(), "$id.m3u").outputStream().use { ins.copyTo(it) } }
                }
                file = "file:$id.m3u"
                if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "M3U"
            }.onFailure { error = it.message; AppLog.e("acc", "file import failed", it) }
            busy = false
        }
    }

    val errAuth = stringResource(R.string.acc_err_auth)
    val errNet = stringResource(R.string.acc_err_net)
    val errFormat = stringResource(R.string.acc_err_format)
    val errEmpty = stringResource(R.string.acc_err_empty)
    val errStatus = stringResource(R.string.acc_err_status, "%s")
    val detected = stringResource(R.string.acc_detected)

    fun save() {
        error = null
        // An M3U link that is really an Xtream "get.php" link becomes an Xtream account (EPG, catch-up, VOD info).
        if (type == AccountType.M3U && file.isBlank()) {
            Xtream.fromM3uLink(url)?.let { c ->
                type = AccountType.XTREAM; server = c.server; user = c.user; pass = c.pass
                Nav.toast = detected
            }
        }
        val acc = Account(
            id = id,
            name = name.ifBlank { if (type == AccountType.XTREAM) server.substringAfter("://").substringBefore(":").substringBefore("/") else "M3U" },
            type = type,
            server = if (type == AccountType.XTREAM) Xtream.normalizeServer(server) else "",
            username = user.trim(), password = pass.trim(),
            m3uUrl = if (type == AccountType.M3U) file.ifBlank { url.trim() } else "",
            epgUrl = epg.trim(), userAgent = ua.trim(),
        )
        if (type == AccountType.XTREAM && (acc.server.isBlank() || acc.username.isBlank())) return
        if (type == AccountType.M3U && acc.m3uUrl.isBlank()) return
        busy = true
        scope.launch {
            try {
                val final = if (type == AccountType.XTREAM) Xtream.login(acc) else acc
                Prefs.saveAccount(final)
                Prefs.update { it.copy(activeAccount = final.id) }
                Repo.clear()
                val r = Repo.refresh(final)
                if (r.isFailure) throw r.exceptionOrNull()!!
                val l = Repo.lib?.raw
                if (l == null || (l.live.isEmpty() && l.movies.isEmpty() && l.series.isEmpty())) { error = errEmpty; return@launch }
                Nav.go(Tab.HOME)
            } catch (e: Xtream.LoginError) {
                error = when {
                    e.reason == "auth" -> errAuth
                    e.reason.startsWith("status:") -> errStatus.replace("%s", e.reason.removePrefix("status:"))
                    e.reason == "format" -> errFormat
                    else -> "$errNet (${e.reason})"
                }
            } catch (e: Net.HttpError) {
                error = "$errNet (HTTP ${e.code})"
            } catch (e: Exception) {
                AppLog.e("acc", "save failed", e)
                error = "$errNet (${e.javaClass.simpleName})"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(
                stringResource(if (type == AccountType.XTREAM) R.string.acc_add_xtream else R.string.acc_add_m3u),
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Xtream Codes", type == AccountType.XTREAM, { type = AccountType.XTREAM })
                Chip("M3U / M3U8", type == AccountType.M3U, { type = AccountType.M3U })
            }
            Field(name, { name = it }, stringResource(R.string.acc_name))
            if (type == AccountType.XTREAM) {
                Field(server, { server = it }, stringResource(R.string.acc_server), "http://example.com:8080", KeyboardType.Uri)
                Field(user, { user = it }, stringResource(R.string.acc_user))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text(stringResource(R.string.acc_pass)) }, singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (file.isBlank()) Field(url, { url = it }, stringResource(R.string.acc_m3u_url), "http://…/get.php?username=…", KeyboardType.Uri)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.acc_add_file)) }
                    if (file.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text("✓ " + file.removePrefix("file:"), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { file = "" }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
                Field(epg, { epg = it }, stringResource(R.string.acc_epg_url), "http://…/epg.xml.gz", KeyboardType.Uri)
            }
            Field(ua, { ua = it }, stringResource(R.string.acc_ua), Net.defaultUserAgent)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.acc_login))
            }
            Text(stringResource(R.string.acc_privacy_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PhoneInputHint()
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, placeholder: String = "", kb: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, maxLines = 1) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        modifier = Modifier.fillMaxWidth(),
    )
}
