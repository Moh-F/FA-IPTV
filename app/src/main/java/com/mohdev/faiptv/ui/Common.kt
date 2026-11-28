package com.mohdev.faiptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.Prefs
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.mohdev.faiptv.core.Locales

/** Device facts set by MainActivity. */
object UiEnv {
    var isTv = false
    var pipSupported = false
    var inPip by mutableStateOf(false)
    @Volatile var foreground = false
}

/** Card that works with touch AND the TV remote: focus ring, slight zoom, Menu key = long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    focusRequester: FocusRequester? = null,
    zoom: Float = 1.03f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) zoom else 1f, label = "focus")
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.18f) else color)
            .then(if (focused) Modifier.border(2.dp, primary, shape) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocus?.invoke()
                }
            }
            .onPreviewKeyEvent { e ->
                when {
                    onLongClick != null && e.key == Key.Menu && e.type == KeyEventType.KeyUp -> { onLongClick(); true }
                    // game pads / some remotes: A = OK, Y = menu
                    e.key == Key.ButtonA && e.type == KeyEventType.KeyUp -> { onClick(); true }
                    onLongClick != null && e.key == Key.ButtonY && e.type == KeyEventType.KeyUp -> { onLongClick(); true }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) { content(focused) }
}

@Composable
fun NetImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: String = "") {
    if (url.isBlank()) {
        Placeholder(fallback, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        loading = { Placeholder(fallback, Modifier.fillMaxSize()) },
        error = { Placeholder(fallback, Modifier.fillMaxSize()) },
    )
}

@Composable
fun Placeholder(text: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(
            text.trim().take(2).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold, fontSize = 16.sp,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, locked: Boolean = false) {
    FocusCard(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50), selected = selected,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            (if (locked) "🔒 " else "") + text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

fun fmtTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locales.num()).format(Date(ms))
fun fmtDay(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locales.num()).format(Date(ms))
fun fmtDayShort(ms: Long): String = java.text.SimpleDateFormat("EEE d.M", Locales.num()).format(Date(ms))

fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, sec) else "%02d:%02d".format(Locale.US, m, sec)
}

fun fmtSize(bytes: Long): String = when {
    bytes > 1L shl 30 -> "%.2f GB".format(Locale.US, bytes / (1L shl 30).toDouble())
    bytes > 1L shl 20 -> "%.1f MB".format(Locale.US, bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}

/** Enter a PIN, or create one first when none exists. Calls [onOk] when the user may pass. */
@Composable
fun PinDialog(onDismiss: () -> Unit, onOk: () -> Unit, forceCreate: Boolean = false) {
    val create = forceCreate || !Prefs.hasPin()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun submit() {
        if (create) {
            if (pin.length >= 4) { Prefs.setPin(pin); Prefs.unlocked = true; onOk() } else error = true
        } else if (Prefs.checkPin(pin)) { Prefs.unlocked = true; onOk() } else { error = true; pin = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (create) R.string.pin_new else R.string.pin_enter)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); error = false },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    modifier = Modifier.focusRequester(fr).fillMaxWidth(),
                )
                if (error) Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Single-choice list dialog. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (v, label) ->
                    FocusCard(onClick = { onPick(v) }, Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent, zoom = 1f) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = v == selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


/** Per-item focus requesters for lazy lists, so focus can enter a list at the first fully visible item. */
class ItemFocus {
    private val map = HashMap<Any, FocusRequester>()
    fun req(key: Any): FocusRequester = map.getOrPut(key) { FocusRequester() }
    fun has(key: Any) = map.containsKey(key)
}

/** When the remote moves focus into this list from outside, land on the first fully visible item (not the one opposite). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.enterAtFirstVisible(state: androidx.compose.foundation.lazy.LazyListState, items: ItemFocus): Modifier =
    this.focusProperties {
        enter = {
            val info = state.layoutInfo.visibleItemsInfo
            val k = (info.firstOrNull { it.offset >= state.layoutInfo.viewportStartOffset } ?: info.firstOrNull())?.key
            if (k != null && items.has(k)) items.req(k) else FocusRequester.Default
        }
    }.focusGroup()

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.enterAtFirstVisible(state: androidx.compose.foundation.lazy.grid.LazyGridState, items: ItemFocus): Modifier =
    this.focusProperties {
        enter = {
            val info = state.layoutInfo.visibleItemsInfo
            val k = (info.firstOrNull { it.offset.y >= state.layoutInfo.viewportStartOffset } ?: info.firstOrNull())?.key
            if (k != null && items.has(k)) items.req(k) else FocusRequester.Default
        }
    }.focusGroup()

/** Simple action list dialog (long-press menus). */
@Composable
fun ActionMenu(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(actions.size) { i ->
                    val (label, action) = actions[i]
                    FocusCard(
                        onClick = { onDismiss(); action() }, modifier = Modifier.fillMaxWidth(), zoom = 1f,
                        focusRequester = if (i == 0) first else null,
                    ) { Text(label, Modifier.padding(14.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(t); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** QR code (for opening the phone page). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bmp = remember(text) {
        runCatching {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
            val w = m.width
            val px = IntArray(w * w) { i -> if (m.get(i % w, i / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            android.graphics.Bitmap.createBitmap(px, w, w, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bmp, null, modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        )
    }
}

/** Digits that do not jump around while they change (tabular figures). */
val tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/** Date and time as configured (German order dd.MM.yyyy, 24/12 h, seconds). */
fun clockText(ms: Long = System.currentTimeMillis()): Pair<String, String> {
    val s = Prefs.s
    val loc = Locales.num()
    val time = when {
        s.clock24 && s.clockSeconds -> "HH:mm:ss"
        s.clock24 -> "HH:mm"
        s.clockSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    val date = when (s.dateFormat) { "day" -> "EE, dd.MM."; "long" -> "EEEE, d. MMMM yyyy"; else -> "dd.MM.yyyy" }
    return java.text.SimpleDateFormat(date, loc).format(Date(ms)) to java.text.SimpleDateFormat(time, loc).format(Date(ms))
}

@Composable
fun ClockView(color: Color, size: Float = 18f, modifier: Modifier = Modifier) {
    if (!Prefs.s.showClock && !Prefs.s.showDate) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(if (Prefs.s.clockSeconds) 1000 else 5000); now = System.currentTimeMillis() } }
    val (d, t) = clockText(now)
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (Prefs.s.showClock) Text(t, color = color, fontSize = size.sp, fontWeight = FontWeight.Medium, style = tabular)
        if (Prefs.s.showDate) Text(d, color = color.copy(alpha = 0.8f), fontSize = (size * 0.62f).sp, style = tabular)
    }
}

/** Small, unobtrusive "updating channels and media" indicator in a corner. */
@Composable
fun RefreshBadge(modifier: Modifier = Modifier) {
    val p = com.mohdev.faiptv.data.Repo.progress ?: return
    val s = Prefs.s
    if (!s.refreshBadge) return
    val scale = when (s.badgeSize) { "s" -> 0.8f; "l" -> 1.3f; else -> 1f }
    val c = when (s.badgeColor) { "white" -> Color.White; "amber" -> Brand.Amber; "green" -> Color(0xFF34D399); else -> MaterialTheme.colorScheme.primary }
    Row(
        modifier.padding(12.dp).clip(RoundedCornerShape(50))
            .background(Color(0xFF0B1220).copy(alpha = s.badgeOpacity / 100f))
            .padding(horizontal = (10 * scale).dp, vertical = (5 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p < 0f) CircularProgressIndicator(Modifier.size((12 * scale).dp), strokeWidth = 2.dp, color = c)
        else androidx.compose.material3.LinearProgressIndicator(
            progress = { p }, modifier = Modifier.width((60 * scale).dp).height((4 * scale).dp).clip(RoundedCornerShape(2.dp)),
            color = c, trackColor = Color(0x33FFFFFF),
        )
        Spacer(Modifier.width((8 * scale).dp))
        Text(
            if (p < 0f) "⟳" else "${(p * 100).toInt()}%", color = Color.White, fontSize = (11 * scale).sp, style = tabular,
        )
    }
}
