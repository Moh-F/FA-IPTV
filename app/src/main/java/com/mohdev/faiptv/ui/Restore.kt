package com.mohdev.faiptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Sync
import kotlinx.coroutines.launch

/** New device or reinstalled app: bring back servers, profiles, lists, favourites and settings from the cloud. */
@Composable
fun RestoreDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf(Prefs.s.ghRepo) }
    var token by remember { mutableStateOf(Prefs.s.ghToken) }
    var pass by remember { mutableStateOf(Prefs.s.syncPass) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    fun go() {
        val k = key.takeIf { it.isNotBlank() }?.let { Sync.parseKey(it) }
        if (key.isNotBlank() && k == null) { err = "✗ key"; return }
        val (r, t, p) = k ?: Triple(repo, token, pass)
        if (r.isBlank() || t.isBlank() || p.length < 8) { err = "✗"; return }
        busy = true; err = null
        scope.launch {
            val e = Sync.restore(r, t, p)
            busy = false
            if (e == null) { Nav.toast = "✓"; onDismiss() } else err = e
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QrTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.restore_key)) }, singleLine = true, title = stringResource(R.string.restore_key))
                Text(stringResource(R.string.restore_or), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QrTextField(repo, { repo = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_gh_repo)) }, placeholder = { Text("Moh-F/FA-IPTV-sync") }, singleLine = true, title = stringResource(R.string.sync_gh_repo))
                QrTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_gh_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, title = stringResource(R.string.sync_gh_token))
                QrTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_pass)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, title = stringResource(R.string.sync_pass))
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (busy) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = { TextButton(onClick = ::go, enabled = !busy) { Text(stringResource(R.string.restore_go)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The key to keep (photo / password manager): with it, any device gets everything back. */
@Composable
fun RestoreKeyDialog(onDismiss: () -> Unit) {
    val key = remember { Sync.restoreKey() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_key)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.restore_key_hint), fontSize = 13.sp)
                QrCode(key, Modifier.size(220.dp))
                Text(key, fontSize = 11.sp, style = tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
