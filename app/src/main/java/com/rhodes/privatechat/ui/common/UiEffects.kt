package com.rhodes.privatechat.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*

val ScreenShape = RoundedCornerShape(24.dp)
val CardShape = RoundedCornerShape(18.dp)
val ControlShape = RoundedCornerShape(14.dp)
val TerminalPanelShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

private fun Modifier.terminalGrid() = drawBehind {
    val grid = 28.dp.toPx()
    val lineColor = TextTertiary.copy(alpha = 0.055f)
    var x = 0f
    while (x < size.width) {
        drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
        x += grid
    }
    var y = 0f
    while (y < size.height) {
        drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
        y += grid
    }
    var diagonal = -size.height
    while (diagonal < size.width) {
        drawLine(TextTertiary.copy(alpha = 0.035f), androidx.compose.ui.geometry.Offset(diagonal, size.height), androidx.compose.ui.geometry.Offset(diagonal + size.height, 0f), 1f)
        diagonal += grid * 2
    }
}

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(HeaderEnd.copy(alpha = 0.75f), BG, BG)
                )
            )
            .then(if (isRhodesTerminal) Modifier.terminalGrid() else Modifier)
    ) { content() }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = ElevatedSurface,
    borderColor: Color = Stroke,
    shadow: Boolean = true,
    content: @Composable () -> Unit
) {
    val actualShape = if (isRhodesTerminal && shape == CardShape) TerminalPanelShape else shape
    Box(
        modifier = modifier
            .then(if (shadow) Modifier.shadow(if (isRhodesTerminal) 3.dp else 8.dp, actualShape, clip = false, ambientColor = Glow, spotColor = Glow) else Modifier)
            .clip(actualShape)
            .background(color)
            .border(1.dp, borderColor, actualShape)
    ) { content() }
}

@Composable
fun GradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (isRhodesTerminal) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .background(Brush.horizontalGradient(listOf(HeaderStart, HeaderEnd)))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp).clip(TerminalPanelShape).background(Card).border(1.dp, Stroke, TerminalPanelShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.width(3.dp).height(30.dp).background(Primary))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("RHODES ISLAND // SYSTEM INTERFACE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.8.sp)
                }
                if (icon != null) Icon(icon, null, tint = Primary, modifier = Modifier.size(21.dp))
                actions()
            }
            HorizontalDivider(color = Primary.copy(alpha = 0.72f), thickness = 1.dp)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Brush.horizontalGradient(listOf(HeaderStart, HeaderEnd)))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).clip(CircleShape).background(Card.copy(alpha = 0.55f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                }
            }
            if (icon != null) {
                Box(Modifier.padding(start = 8.dp, end = 8.dp).size(36.dp).clip(ControlShape).background(Primary.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = if (onBack == null && icon == null) 8.dp else 4.dp))
            actions()
        }
        HorizontalDivider(color = Stroke)
    }
}

@Composable
fun SoftIconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.08f))))
            .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
}

@Composable
fun PillButton(text: String, color: Color = Primary, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) ErrorRed else color
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = c,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.copy(alpha = 0.12f))
            .border(1.dp, c.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun SoftSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary, modifier = modifier.padding(horizontal = 18.dp, vertical = 8.dp))
}

@Composable
fun ThemedDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, offset: DpOffset = DpOffset.Zero, content: @Composable () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, offset = offset, containerColor = ElevatedSurface, tonalElevation = 6.dp, shadowElevation = 8.dp) { content() }
}

@Composable
fun ThemedAlertDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    danger: Boolean = false,
    dismissText: String = "取消"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSurface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, color = TextSecondary, lineHeight = 20.sp) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = if (danger) ErrorRed else Primary)) { Text(confirmText, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) { Text(dismissText) } }
    )
}

@Composable
fun softTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary.copy(alpha = 0.72f),
    unfocusedBorderColor = StrokeStrong,
    focusedContainerColor = InputBg.copy(alpha = 0.72f),
    unfocusedContainerColor = InputBg.copy(alpha = 0.58f),
    cursorColor = Primary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

@Composable
fun SoftListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SoftCard(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), shadow = false) { content() }
}

@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun WechatTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (isRhodesTerminal) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .background(Brush.horizontalGradient(listOf(HeaderStart, HeaderEnd)))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(28.dp).background(Primary))
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("RHODES TERMINAL // ${title.uppercase()}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.7.sp)
                }
                actions()
            }
            Row(Modifier.fillMaxWidth().height(3.dp)) {
                Box(Modifier.weight(0.22f).fillMaxSize().background(Primary))
                Box(Modifier.weight(0.78f).fillMaxSize().background(Stroke))
            }
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        actions()
    }
    HorizontalDivider(color = Divider.copy(alpha = 0.55f))
}

@Composable
fun WechatListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (isRhodesTerminal) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                .clip(TerminalPanelShape).background(Surface.copy(alpha = 0.88f))
                .border(1.dp, Stroke, TerminalPanelShape)
        ) { content() }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
    ) { content() }
}

@Composable
fun WechatListItem(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (showDivider) HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 68.dp))
    }
}

@Composable
fun WechatIconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    if (isRhodesTerminal) {
        Box(
            modifier = modifier.size(38.dp).clip(TerminalPanelShape)
                .background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.7f), TerminalPanelShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        return
    }
    Box(
        modifier = modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(color),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
}

@Composable
fun WechatSearchBox(text: String, placeholder: String = "搜索", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(InputBg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(17.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
        Text(if (text.isBlank()) placeholder else text, color = if (text.isBlank()) TextTertiary else TextPrimary, fontSize = 14.sp)
    }
}
