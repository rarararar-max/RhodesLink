#!/usr/bin/env python3
"""Replace AsyncImage avatar displays with OperatorAvatarImage in all UI files."""
import os, re, subprocess

WSL_HOME = "/home/rararararmax/mingrifangzhou"

def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def add_import(content, import_stmt):
    """Add import if not present."""
    if import_stmt in content:
        return content
    # Find last import line and add after it
    lines = content.split("\n")
    last_import = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import = i
    if last_import >= 0:
        lines.insert(last_import + 1, import_stmt)
    return "\n".join(lines)

def replace_pattern(content, pattern, replacement):
    """Simple regex replacement."""
    return re.sub(pattern, replacement, content, flags=re.DOTALL)

# Map of WSL paths to work on
files_to_fix = []

# Files to scan for AsyncImage avatar patterns
base = "/app/src/main/java/com/rhodes/privatechat/ui"
targets = [
    # (WSL path, replacement descriptions)
    ("detail/OperatorDetailScreen.kt", "done"),  # Already done
    ("contacts/ContactsScreen.kt", "TODO"),
    ("sessions/SessionListScreen.kt", "TODO"),
    ("chat/component/MessageList.kt", "TODO"),
    ("chat/component/ChatHeader.kt", "TODO"),
    ("settings/PermissionsScreen.kt", "TODO"),
    ("ranking/RankingScreen.kt", "TODO"),
    ("diary/DiaryScreen.kt", "TODO"),
    ("moments/MomentsScreen.kt", "TODO"),
    ("moments/MomentDetailScreen.kt", "TODO"),
    ("group/GroupDetailScreen.kt", "TODO"),
    ("chat/ChatExportDialog.kt", "TODO"),
]

IMPORT = "import com.rhodes.privatechat.ui.common.OperatorAvatarImage\n"

for target, _ in targets:
    path = f"{WSL_HOME}{base}/{target}"
    if not os.path.exists(path):
        print(f"SKIP: {path} not found")
        continue
    
    content = read_file(path)
    original = content
    
    # Add import
    content = add_import(content, IMPORT)
    
    # Replace common patterns
    # Pattern 1: if(avatarUri.isNotBlank()){AsyncImage(model=avatarUri,...)}else{Box(...)Text(name.take(1)}
    content = re.sub(
        r'if\s*\(\s*(\w+)\.avatarUri\.isNotBlank\(\s*\)\s*\)\s*\{\s*AsyncImage\(\s*model\s*=\s*\1\.avatarUri[^}]*\}\s*else\s*\{\s*Box\s*\([^}]*\)\s*\{\s*Text\(\s*\1\.name\.take\(1\)[^}]*\}\s*\}',
        r'OperatorAvatarImage(avatarUri = \1.avatarUri, name = \1.name, modifier = Modifier.size(\2))',
        content
    )
    
    # Pattern 2: if(xxx.avatarUri.isNotBlank()){AsyncImage(model=xxx.avatarUri,modifier=Modifier.size(XX.dp)...)} 
    # followed by else with name.take(1)
    content = re.sub(
        r'if\s*\(\s*(\w+)\.avatarUri\.isNotBlank\(\s*\)\s*\)\s*\{\s*AsyncImage\(\s*model\s*=\s*\1\.avatarUri,\s*contentDescription\s*=\s*null,\s*modifier\s*=\s*Modifier\.size\((\d+)\.dp\)[^}]*\}\s*else\s*\{\s*Box\s*\([^}]*clip\(CircleShape\)[^}]*\)\s*\{\s*Text\(\s*\1\.name\.take\(1\)[^}]*\}\s*\}',
        r'OperatorAvatarImage(avatarUri = \1.avatarUri, name = \1.name, modifier = Modifier.size(\2.dp))',
        content
    )
    
    # Pattern 3: simpler - just AsyncImage with ops.avatarUri
    content = re.sub(
        r'AsyncImage\(\s*model\s*=\s*(\w+)\.avatarUri,\s*contentDescription\s*=\s*null,\s*modifier\s*=\s*Modifier\.size\((\d+)\.dp\)\.clip\(CircleShape\)[^)]*\)',
        r'OperatorAvatarImage(avatarUri = \1.avatarUri, name = \1.name, modifier = Modifier.size(\2.dp))',
        content
    )
    
    if content != original:
        write_file(path, content)
        print(f"FIXED: {target}")
    else:
        print(f"NO CHANGE: {target}")

print("Done!")
