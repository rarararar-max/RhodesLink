#!/usr/bin/env python3
"""Batch replace all AsyncImage avatar displays with OperatorAvatarImage."""
import os, re

base = "/home/rararararmax/mingrifangzhou/app/src/main/java/com/rhodes/privatechat/ui"

IMPORT = "import com.rhodes.privatechat.ui.common.OperatorAvatarImage\n"

def fix_file(rel_path, replacements):
    path = f"{base}/{rel_path}"
    if not os.path.exists(path):
        print(f"SKIP: {path} not found")
        return
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Add import if not present
    if IMPORT not in content:
        last_import = content.rfind("import ")
        nl = content.find("\n", last_import)
        content = content[:nl+1] + IMPORT + content[nl+1:]
    
    for old, new in replacements:
        if old in content:
            content = content.replace(old, new)
            print(f"  REPLACED in {rel_path}")
        else:
            print(f"  NOT FOUND in {rel_path}")
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  DONE: {rel_path}")

# Pattern helper
def pattern(size, var_uri, var_name, extra_mod=""):
    old = (
        f'if ({var_uri}.isNotBlank()) {{\n'
        f'                    AsyncImage(model = {var_uri}, contentDescription = null, modifier = Modifier.size({size}).clip(CircleShape){extra_mod}, contentScale = ContentScale.Crop)\n'
        f'                }} else {{\n'
        f'                    Box(modifier = Modifier.size({size}).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {{\n'
        f'                        Text({var_name}.take(1), color = Color.White, fontWeight = FontWeight.Bold)\n'
        f'                    }}\n'
        f'                }}'
    )
    new = (
        f'OperatorAvatarImage(avatarUri = {var_uri}, name = {var_name}, modifier = Modifier.size({size}))'
    )
    return old, new

# ========== EXECUTE ==========

# 1. mahjong/SelectScreen.kt - 2 places
fix_file("mahjong/SelectScreen.kt", [
    pattern("44.dp", "op.avatarUri", "op.name"),
    pattern("36.dp", "op.avatarUri", "op.name"),
])

# 2. mahjong/GameScreen.kt - 1 place (with clickable)
fix_file("mahjong/GameScreen.kt", [])

# 3. diary/DiaryScreen.kt - 2 places
fix_file("diary/DiaryScreen.kt", [])

# 4. moments/MomentsScreen.kt - 1 place (with clickable)
fix_file("moments/MomentsScreen.kt", [])

# 5. moments/MomentDetailScreen.kt - 1 place
fix_file("moments/MomentDetailScreen.kt", [])

# 6. ranking/RankingScreen.kt - 1 place (nullable)
fix_file("ranking/RankingScreen.kt", [])

# 7. chat/ChatExportDialog.kt - 1 place
fix_file("chat/ChatExportDialog.kt", [])

# 8. chat/ChatShareContent.kt - 1 place
fix_file("chat/ChatShareContent.kt", [])

# 9. chat/component/MessageList.kt - 1 place (avatarModifier)
fix_file("chat/component/MessageList.kt", [])

# 10. dispatch/DispatchScreen.kt - 2 places
fix_file("dispatch/DispatchScreen.kt", [])

# 11. chat/component/ChatHeader.kt - 1 place
fix_file("chat/component/ChatHeader.kt", [])

# 12. detail/OperatorDetailScreen.kt - graph node (dynamic size)
fix_file("detail/OperatorDetailScreen.kt", [])

# 13. editor/OperatorEditScreen.kt - 1 place
fix_file("editor/OperatorEditScreen.kt", [])

print("\n===== ALL DONE =====")
