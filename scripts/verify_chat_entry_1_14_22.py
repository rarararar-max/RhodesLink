"""Static regression guard for the two independent initial-message loading paths."""
from pathlib import Path
import re
import json

root = Path(__file__).resolve().parents[1]
sources = root / "app/src/main/java/com/rhodes/privatechat"
vm = (sources / "viewmodel/ChatViewModel.kt").read_text(encoding="utf-8")
ui = (sources / "ui/chat/component/MessageList.kt").read_text(encoding="utf-8")
history = (sources / "ui/chat/ChatHistoryScreen.kt").read_text(encoding="utf-8")
checks = []
assert vm.count("mergeMessagesFromDatabase(initialMessages)") == 2
assert "_scrollToMessageId.value = initialMessages.lastOrNull()?.id" not in vm
checks.append("Both initial-load paths leave bottom positioning to MessageList")
assignments = re.findall(r"_scrollToMessageId\.value\s*=\s*([^\r\n]+)", vm)
assert assignments.count("messageId") == 1
assert all(value in ("messageId", "null") for value in assignments), assignments
checks.append("Only explicit message navigation sets a non-null scroll target")
assert "viewModel.jumpToCurrentSessionMessage(msg.id)" in history
assert "displayMessages.indexOfFirst { it.originalMessageId == target }" in ui
checks.append("History navigation still resolves the requested original message")
assert "listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))" in ui
assert "if (initialContentSettled && !followLatest && listState.canScrollForward)" in ui
assert '"↓ 回到底部"' in ui
checks.append("Bottom scrolling targets the footer and the action has explicit wording")
result = {"checks": checks, "real_device_tested": False, "method": "static regression guard"}
(root / "docs/chat-entry-verification-1.14.22.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(result, ensure_ascii=False, indent=2))
