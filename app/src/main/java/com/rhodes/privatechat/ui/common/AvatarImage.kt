package com.rhodes.privatechat.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.theme.Primary

@Composable
fun OperatorAvatarImage(
    avatarUri: String,
    name: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    emoji: String = ""
) {
    val num = avatarUri.toIntOrNull()
    if (num != null && num in 1..147) {
        val context = LocalContext.current
        val resId = remember(context, num) {
            context.resources.getIdentifier("avatar_$num", "drawable", context.packageName)
        }
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                modifier = modifier.clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }
    if (avatarUri.isNotBlank() && avatarUri.toIntOrNull() == null) {
        AsyncImage(
            model = avatarUri,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (emoji.isNotBlank()) emoji else name.take(1),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
