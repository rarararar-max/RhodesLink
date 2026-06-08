#!/usr/bin/env python3
# -*- coding: utf-8 -*-
path = "/home/rararararmax/mingrifangzhou/shared/src/commonMain/kotlin/com/rhodes/privatechat/shared/data/OperatorRepository.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

start_marker = "    private val presetOperators = listOf("
end_marker = "    )\n\n    suspend fun deleteOperator"
start_idx = content.find(start_marker)
end_idx = content.find(end_marker)
head = content[:start_idx]
tail = content[end_idx:]

ops = []
def add(id, name, title, desc, loc, act, emo, priv, grp):
    priv = priv.replace('\u201c', '\u300c').replace('\u201d', '\u300d')
    grp = grp.replace('\u201c', '\u300c').replace('\u201d', '\u300d')
    ops.append(f'        Operator(id = "{id}", name = "{name}", title = "{title}", description = "{desc}", location = "{loc}", activity = "{act}", emotion = "{emo}", privatePrompt = "{priv}", groupPrompt = "{grp}", avatarUri = avatarRes("{id}")),\n')

# Read add_ops.txt (operators ~34-116)
add_path = "/home/rararararmax/mingrifangzhou/add_ops.txt"
with open(add_path, "r", encoding="utf-8-sig") as f:
    exec(f.read())

# Operators 101-147 (from add_ops_2.txt content - manually added where needed)

print(f"Total operators: {len(ops)}")

result = head + "    private val presetOperators = listOf(\n"
for line in ops:
    result += line
result += "    )\n" + tail

with open(path, "w", encoding="utf-8") as f:
    f.write(result)
print("Done!")
