package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.DebugLogger

@Composable
fun SaveableSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val settings: SettingsRepository = org.koin.compose.koinInject()
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun requestBack() {
        if (settings.hasDraftChanges()) showDiscardDialog = true else onBack()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { settings.beginDraft() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { settings.discardDraft() } }
    BackHandler { requestBack() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { requestBack() }) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            if (icon != null) {
                icon()
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Button(
                onClick = { settings.saveDraft(); onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = Blue400)
            ) { Text("保存", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
        }
        androidx.compose.material3.HorizontalDivider(color = Divider)
        content()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("有未保存的修改", color = TextPrimary) },
            text = { Text("你已经修改了设置。要保存后离开，还是放弃这些修改？", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { settings.saveDraft(); showDiscardDialog = false; onBack() }) { Text("保存修改", color = Primary) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { settings.discardDraft(); showDiscardDialog = false; onBack() }) { Text("放弃修改", color = ErrorRed) }
                    TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑", color = TextSecondary) }
                }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp))
}

@Composable
fun SettingsHelpButton(message: String) {
    var show by remember { mutableStateOf(false) }
    Box(modifier = Modifier.size(20.dp).clip(CircleShape).clickable { show = true }, contentAlignment = Alignment.Center) {
        Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("知道了", color = Primary) } },
            text = { Text(message, fontSize = 14.sp, color = TextPrimary) }
        )
    }
}

@Composable
fun SettingsSwitchCard(title: String, desc: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    var local by remember(checked) { mutableStateOf(checked) }
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = local, enabled = enabled, onCheckedChange = {
            local = it
            onCheckedChange(it)
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsParamSlider(settings: SettingsRepository, key: String, label: String, defaultVal: Int, range: ClosedFloatingPointRange<Float>, tip: String, step: Float = 1f, pairKey: String? = null, isMinSide: Boolean = true, enabled: Boolean = true) {
    var value by remember { mutableFloatStateOf(settings.getInt(key, defaultVal).toFloat().coerceIn(range)) }
    var pairValue by remember { mutableFloatStateOf(if (pairKey != null) settings.getInt(pairKey, defaultVal).toFloat() else 0f) }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.width(2.dp))
            SettingsHelpButton(tip)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue400)
        }
        Slider(value = value, enabled = enabled, onValueChange = { v ->
            if (pairKey != null) {
                pairValue = settings.getInt(pairKey, defaultVal).toFloat()
                if (isMinSide) {
                    if (v <= pairValue) value = v else { value = pairValue; pairValue = v }
                } else {
                    if (v >= pairValue) value = v else { value = pairValue; pairValue = v }
                }
            } else value = v
        }, onValueChangeFinished = {
            val oldValue = settings.getInt(key, defaultVal)
            val oldPairValue = if (pairKey != null) settings.getInt(pairKey, defaultVal) else null
            settings.putInt(key, value.toInt())
            DebugLogger.log("Settings/Param", "参数调整: $label($key) $oldValue -> ${value.toInt()}")
            if (pairKey != null) {
                settings.putInt(pairKey, pairValue.toInt())
                DebugLogger.log("Settings/Param", "联动参数: $pairKey ${oldPairValue ?: 0} -> ${pairValue.toInt()}")
            }
        }, valueRange = range, steps = ((range.endInclusive - range.start) / step).toInt(), colors = SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
    }
    Spacer(Modifier.height(4.dp))
}
