# Run this with: wsl python3 /home/rararararmax/mingrifangzhou/gen_full.py
import os, sys
sys.stdout.reconfigure(encoding='utf-8')

source = r'/home/rararararmax/mingrifangzhou/shared/src/commonMain/kotlin/com/rhodes/privatechat/shared/data/OperatorRepository.kt'
backup = source + '.bak'

# Read existing file
with open(source, 'r', encoding='utf-8') as f:
    content = f.read()

# Save backup
import shutil
shutil.copy2(source, backup)
print(f'Backup saved to {backup}')

# Read add_ops files
all_adds = []
for fn in ['add_ops_1.txt', 'add_ops_2.txt']:
    fp = f'/home/rararararmax/mingrifangzhou/{fn}'
    if os.path.exists(fp):
        with open(fp, 'r', encoding='utf-8-sig') as f:
            all_adds.append(f.read())
        print(f'Read {fn} ({len(all_adds[-1])} bytes)')
    else:
        print(f'File not found: {fn}')

# Insert operators after the 16 base ones
marker = 'print(f"Starting generation with'
idx = content.find(marker)
if idx == -1:
    print('ERROR: marker not found')
    sys.exit(1)
# Find the line after the marker's print statement
insert_pos = content.find('\nadd("thorns"', idx)
if insert_pos == -1:
    print('ERROR: thorns not found')
    sys.exit(1)
# Find end of thorns add() call
end_of_thorns = content.find(')', insert_pos) + 1

# Insert the rest after thorns
rest = '\n'.join(all_adds)
new_content = content[:end_of_thorns] + '\n' + rest + content[end_of_thorns:]

with open(source, 'w', encoding='utf-8') as f:
    f.write(new_content)

print('Inserted operator data. File updated.')
