import os

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

# Load additional operators from companion files
for fname in ["/home/rararararmax/mingrifangzhou/add_ops_2.txt"]:
    if os.path.exists(fname):
        with open(fname, "r", encoding="utf-8-sig") as f:
            extra = f.read()
        exec(extra)

result = head + "    private val presetOperators = listOf(\n"
for line in ops:
    result += line
result += "    )\n" + tail

with open(path, "w", encoding="utf-8") as f:
    f.write(result)

print(f"Written {len(ops)} operators")
