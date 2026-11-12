package com.rhodes.privatechat.ui.model

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    models: List<String>, selected: String, onDismiss: () -> Unit,
    onSelect: (String) -> Unit, onManual: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(models, query) { models.filter { it.contains(query.trim(), ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选择模型", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                if (visible.isEmpty()) item { Text("没有匹配的模型，可改用手动输入。", Modifier.padding(vertical = 20.dp)) }
                items(visible, key = { it }) { id ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = id == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(id, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
            TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手动输入模型名称") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
