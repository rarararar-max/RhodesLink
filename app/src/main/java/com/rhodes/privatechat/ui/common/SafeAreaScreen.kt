package com.rhodes.privatechat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rhodes.privatechat.ui.theme.BG

/**
 * Screen wrapper that keeps safe areas (status bar / navigation bar) transparent.
 * The background color only fills the content area after safe-area insets.
 */
@Composable
fun SafeAreaScreen(
    modifier: Modifier = Modifier,
    bgColor: Color = BG,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(bgColor).systemBarsPadding()) {
            content()
        }
    }
}
