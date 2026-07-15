package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.rhodes.privatechat.ui.common.GradientHeader
import com.rhodes.privatechat.ui.common.SoftCard
import com.rhodes.privatechat.ui.common.ThemedAlertDialog
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
        GradientHeader(title = title, onBack = { requestBack() }, actions = {
            Button(onClick = { settings.saveDraft(); onBack() }, colors = ButtonDefaults.buttonColors(containerColor = Blue400), shape = RoundedCornerShape(999.dp)) { Text("保存", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
        })
        content()
    }

    if (showDiscardDialog) {
        AlertDialog(onDismissRequest = { showDiscardDialog = false }, containerColor = ElevatedSurface, shape = RoundedCornerShape(24.dp), title = { Text("有未保存的修改", color = TextPrimary) }, text = { Text("你已经修改了设置。要保存后离开，还是放弃这些修改？", color = TextSecondary) }, confirmButton = { TextButton(onClick = { settings.saveDraft(); showDiscardDialog = false; onBack() }) { Text("保存修改", color = Primary) } }, dismissButton = { Row { TextButton(onClick = { settings.discardDraft(); showDiscardDialog = false; onBack() }) { Text("放弃修改", color = ErrorRed) }; TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑", color = TextSecondary) } } })
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
    SoftCard(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), shadow = false) { Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
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
    } }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsParamSlider(settings: SettingsRepository, key: String, label: String, defaultVal: Int, range: ClosedFloatingPointRange<Float>, tip: String, step: Float = 1f, pairKey: String? = null, isMinSide: Boolean = true, enabled: Boolean = true) {
    var value by remember(key) { mutableFloatStateOf(settings.getInt(key, defaultVal).toFloat().coerceIn(range)) }
    fun pairedDefault(key: String, fallback: Int): Int = when (key) {
        "dia_min" -> 10; "dia_max" -> 300
        "nar_min" -> 50; "nar_max" -> 300
        "group_msg_min" -> 10; "group_msg_max" -> 100
        "group_nar_min" -> 20; "group_nar_max" -> 100
        "moment_min_chars" -> 50; "moment_max_chars" -> 200
        "diary_min_chars" -> 50; "diary_max_chars" -> 300
        "dispatch_min_chars" -> 50; "dispatch_max_chars" -> 300
        else -> fallback
    }
    var pairValue by remember(pairKey) { mutableFloatStateOf(if (pairKey != null) settings.getInt(pairKey, pairedDefault(pairKey, defaultVal)).toFloat() else 0f) }
    SoftCard(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), shadow = false) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.width(2.dp))
            SettingsHelpButton(tip)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue400)
        }
        Slider(value = value, enabled = enabled, onValueChange = { v ->
            if (pairKey != null) {
                // Reload the paired setting at drag start so the two separate controls stay in sync.
                pairValue = settings.getInt(pairKey, pairedDefault(pairKey, defaultVal)).toFloat()
                if (isMinSide) {
                    // Clamp at the paired thumb. Swapping values makes the other slider appear stuck.
                    value = v.coerceAtMost(pairValue)
                } else {
                    value = v.coerceAtLeast(pairValue)
                }
            } else value = v
        }, onValueChangeFinished = {
            val oldValue = settings.getInt(key, defaultVal)
            settings.putInt(key, value.toInt())
            DebugLogger.log("Settings/Param", "参数调整: $label($key) $oldValue -> ${value.toInt()}")
        }, valueRange = range, steps = ((range.endInclusive - range.start) / step).toInt(), colors = SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
    } }
    Spacer(Modifier.height(4.dp))
}
