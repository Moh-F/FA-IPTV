package com.mohdev.faiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Profile
import com.mohdev.faiptv.core.profileColors
import com.mohdev.faiptv.data.Repo
import java.util.UUID

@Composable
fun profileName(p: Profile): String = p.name.ifBlank { stringResource(R.string.profile_me) }

@Composable
fun Avatar(p: Profile, size: Dp, selected: Boolean = false) {
    val c = Color(p.color.toLong() and 0xFFFFFFFFL)
    Box(
        Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.9f))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (p.kids) Icon(Icons.Default.ChildCare, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        else Text(profileName(p).take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** Switches profile; asks for the profile PIN, and for the parental PIN when leaving a kids profile. */
fun switchProfile(p: Profile, ask: (check: (String) -> Boolean, then: () -> Unit) -> Unit, done: () -> Unit) {
    val fromKids = Prefs.isKids && p.id != Prefs.profileId
    fun go() {
        if (p.pinHash.isNotBlank()) ask({ Prefs.hash(it) == p.pinHash }) { Prefs.selectProfile(p.id); done() }
        else { Prefs.selectProfile(p.id); done() }
    }
    if (fromKids && Prefs.hasPin()) ask({ Prefs.checkPin(it) }) { go() } else go()
}

/** YouTube-like "Who's watching?" screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePicker(onDone: () -> Unit) {
    var pinAsk by remember { mutableStateOf<Pair<(String) -> Boolean, () -> Unit>?>(null) }
    var edit by remember { mutableStateOf<Profile?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.profile_who), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Prefs.profiles.forEachIndexed { i, p ->
                    FocusCard(
                        onClick = { switchProfile(p, { c, t -> pinAsk = c to t }, onDone) },
                        onLongClick = { if (!Prefs.isKids) edit = p },
                        color = Color.Transparent, zoom = 1.08f, focusRequester = if (p.id == Prefs.profileId || (i == 0 && Prefs.profiles.none { it.id == Prefs.profileId })) first else null,
                    ) {
                        Column(Modifier.padding(12.dp).width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(p, 96.dp, selected = p.id == Prefs.profileId)
                            Spacer(Modifier.height(8.dp))
                            Text(profileName(p), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            if (p.kids) Text(stringResource(R.string.profile_kids), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (!Prefs.isKids) FocusCard(onClick = { edit = Profile(UUID.randomUUID().toString(), "", profileColors[Prefs.profiles.size % profileColors.size].toInt()) }, color = Color.Transparent, zoom = 1.08f) {
                    Column(Modifier.padding(12.dp).width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.profile_add))
                    }
                }
            }
            if (!Prefs.isKids) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.profile_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    pinAsk?.let { (check, then) -> ProfilePinDialog(check, onDismiss = { pinAsk = null }) { pinAsk = null; then() } }
    edit?.let { p -> ProfileEditDialog(p, onDismiss = { edit = null }) }
}

@Composable
fun ProfilePinDialog(check: (String) -> Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_enter)) },
        text = {
            QrTextField(
                pin, { v -> pin = v.filter { it.isDigit() }.take(8); err = false }, singleLine = true, isError = err,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        },
        confirmButton = { TextButton(onClick = { if (check(pin)) onOk() else { err = true; pin = "" } }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditDialog(p0: Profile, onDismiss: () -> Unit) {
    val exists = Prefs.profiles.any { it.id == p0.id }
    var name by remember { mutableStateOf(p0.name) }
    var color by remember { mutableStateOf(p0.color) }
    var kids by remember { mutableStateOf(p0.kids) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (exists) R.string.profile_edit else R.string.profile_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QrTextField(name, { name = it }, label = { Text(stringResource(R.string.acc_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileColors.forEach { c ->
                        FocusCard(onClick = { color = c.toInt() }, shape = CircleShape, color = Color.Transparent, zoom = 1.1f) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                    .then(if (color == c.toInt()) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            )
                        }
                    }
                }
                FocusCard(onClick = { kids = !kids }, modifier = Modifier.fillMaxWidth(), zoom = 1f) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_kids))
                            Text(stringResource(R.string.profile_kids_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(kids, null)
                    }
                }
                QrTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(8) }, singleLine = true,
                    label = { Text(stringResource(if (p0.pinHash.isBlank()) R.string.profile_pin_optional else R.string.pin_change)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (p0.pinHash.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(removePin, { removePin = it }); Text(stringResource(R.string.pin_remove))
                }
                if (exists && kids) OutlinedButton(onClick = { onDismiss(); Nav.push(Route.KidsAllowed(p0.id)) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_kids_content))
                }
                if (exists && Prefs.profiles.size > 1) TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hash = when { removePin -> ""; pin.length >= 4 -> Prefs.hash(pin); else -> p0.pinHash }
                Prefs.saveProfile(p0.copy(name = name.trim(), color = color, kids = kids, pinHash = hash))
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmDelete) ConfirmDialog(stringResource(R.string.profile_delete_confirm), onDismiss = { confirmDelete = false }) {
        Prefs.deleteProfile(p0.id); onDismiss()
    }
}

/** Parents choose what a kids profile may see (whitelist per account). */
@Composable
fun KidsAllowedScreen(profileId: String) {
    val acc = Prefs.active ?: return
    val lib = Repo.lib ?: run { LoadingBox(); return }
    val profile = Prefs.profiles.firstOrNull { it.id == profileId } ?: return
    var tab by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    val allowed = Prefs.allowedOf(profileId, acc.id)
    fun toggle(key: String) = Prefs.toggleAllowed(profileId, acc.id, key)
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Avatar(profile, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.profile_kids_content), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(profileName(profile) + " · " + acc.name + " · " + allowed.size, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.nav_live, R.string.nav_movies, R.string.nav_series).forEachIndexed { i, r -> Chip(stringResource(r), tab == i, { tab = i; open = null }) }
        }
        val cats = when (tab) { 0 -> lib.raw.liveCats; 1 -> lib.raw.movieCats; else -> lib.raw.seriesCats }
        val kind = when (tab) { 0 -> "live"; 1 -> "movie"; else -> "series" }
        LazyColumn(Modifier.widthIn(max = 800.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cats.forEach { c ->
                val key = "$kind:cat:${c.id}"
                item(key = key) {
                    val adult = Prefs.looksAdult(c.name)
                    FocusCard(onClick = { toggle(key) }, modifier = Modifier.fillMaxWidth(), zoom = 1f, color = Color.Transparent) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(key in allowed, null)
                            Text((if (adult) "🔞 " else "") + c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tab == 0) {
                                val n = lib.liveByCat[c.id].orEmpty().count { "live:ch:${it.id}" in allowed }
                                if (n > 0) Text("$n", color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { open = if (open == c.id) null else c.id }) {
                                    Icon(if (open == c.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                            }
                        }
                    }
                }
                if (tab == 0 && open == c.id) {
                    items(lib.liveByCat[c.id].orEmpty(), key = { "ch" + it.id }) { ch ->
                        val k = "live:ch:${ch.id}"
                        FocusCard(onClick = { toggle(k) }, modifier = Modifier.fillMaxWidth().padding(start = 32.dp), zoom = 1f, color = Color.Transparent) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(k in allowed || key in allowed, null)
                                Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Manage profiles from the settings. */
@Composable
fun ProfilesManageRow() {
    var edit by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Prefs.profiles.forEach { p ->
            FocusCard(onClick = { edit = p }, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), zoom = 1.01f) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 36.dp, selected = p.id == Prefs.profileId)
                    Spacer(Modifier.width(12.dp))
                    Text(profileName(p) + if (p.kids) " · " + stringResource(R.string.profile_kids) else "", Modifier.weight(1f))
                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FocusCard(onClick = { edit = Profile(UUID.randomUUID().toString(), "", profileColors[Prefs.profiles.size % profileColors.size].toInt()) },
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), zoom = 1.01f) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.profile_add))
            }
        }
    }
    edit?.let { ProfileEditDialog(it) { edit = null } }
}
