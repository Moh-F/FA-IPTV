package com.mohdev.faiptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.RemoteInput

/**
 * Text field with a QR button next to it: scan with the phone, type there, the text lands here.
 * Same parameters as OutlinedTextField (the ones the app uses).
 */
@Composable
fun QrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    title: String = "",
) {
    var qr by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onValueChange)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value, onValueChange, Modifier.weight(1f),
            label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        )
        FocusCard(onClick = { qr = true }, modifier = Modifier.padding(start = 6.dp), shape = RoundedCornerShape(10.dp), zoom = 1.08f) {
            Icon(Icons.Default.QrCode2, stringResource(R.string.qr_input), Modifier.padding(10.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (qr) QrInputDialog(
        title.ifBlank { stringResource(R.string.qr_input) }, value,
        secret = visualTransformation is PasswordVisualTransformation, multi = !singleLine,
        onDismiss = { qr = false },
    ) { change(it); qr = false }
}

@Composable
fun QrInputDialog(label: String, value: String, secret: Boolean = false, multi: Boolean = false, onDismiss: () -> Unit, onValue: (String) -> Unit) {
    val ctx = LocalContext.current
    val got by rememberUpdatedState(onValue)
    val session = remember { RemoteInput.start(ctx, label, value, secret, multi) { got(it) } }
    DisposableEffect(Unit) { onDispose { session?.let { RemoteInput.stop(it.first) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_input)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (session == null) Text(stringResource(R.string.qr_no_network), color = MaterialTheme.colorScheme.error)
                else {
                    Text(stringResource(R.string.qr_input_hint), fontSize = 14.sp)
                    QrCode(session.second, Modifier.size(220.dp))
                    Text(session.second, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, style = tabular)
                    if (label.isNotBlank()) Text(label, fontSize = 13.sp, color = Color.Unspecified)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
