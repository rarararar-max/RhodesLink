"""Read-only source inspection plus isolated SQLite fixtures; never opens user databases."""
from pathlib import Path
import sqlite3,re,json,sys
R=Path(__file__).resolve().parents[1]
B=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(r'C:\Users\Administrator\Documents\ChatGPT\mingrifangzhoutest2\baseline-1.14.17-memory')
Q=Path('shared/src/commonMain/sqldelight/com/rhodes/privatechat/shared/db')
def ddl(p):return re.split(r'^\w+:\s*$',p.read_text(encoding='utf-8-sig'),maxsplit=1,flags=re.M)[0]
def query(file,name):
 s=(R/Q/file).read_text(encoding='utf-8-sig')
 return re.search(r'^'+name+r':\s*\n(.*?);',s,re.M|re.S)[1]
checks=[]
def passed(s):checks.append(s);print('PASS:',s)
for p in (B/Q).glob('*.sq'):assert ddl(p)==ddl(R/Q/p.name),p.name
old_migrations={p.name:p.read_bytes() for p in (B/Q).glob('*.sqm')}
assert old_migrations=={p.name:p.read_bytes() for p in (R/Q).glob('*.sqm')}
passed('Existing table/index definitions and migrations unchanged from 1.14.17')
c=sqlite3.connect(':memory:')
for p in (B/Q).glob('*.sq'):c.executescript(ddl(p))
def insert_memory(**kw):
 d=dict(ownerType='operator',ownerId='op',memoryLevel='L1',memoryType='event',sourceKind='PRIVATE_CHAT',sourceRefId='session:epoch1:1-3',sessionId='session',content='same fact',privacy='private',createdAt=1,updatedAt=1,expiresAt=9999,status='active')
 d.update(kw)
 return c.execute('INSERT INTO memory_items('+','.join(d)+') VALUES('+','.join('?' for _ in d)+')',tuple(d.values())).lastrowid
def equivalent(**kw):
 d=dict(ownerType='operator',ownerId='op',memoryLevel='L1',memoryType='event',content='same fact',sourceKind='PRIVATE_CHAT',sourceRefId='session:epoch1:1-3',sessionId='session',privacy='private',now=100)
 d.update(kw)
 return c.execute(query('MemoryItems.sq','findEquivalentLiveMemory'),tuple(d.values())).fetchone()
mid=insert_memory();assert equivalent()==(mid,)
assert equivalent(now=10000) is None
assert equivalent(privacy='public') is None
assert equivalent(sourceKind='GROUP_CHAT') is None
assert equivalent(sessionId='another') is None
assert equivalent(sourceRefId='session:epoch1:4-6') is None
passed('Dedup rejects only live identical evidence; expired/private/public/session/source boundaries remain separate')
insert_memory(privacy=None);assert equivalent(privacy=None) is not None
passed('Nullable legacy privacy fields remain readable and deduplicated safely')
old_rows=c.execute('SELECT * FROM memory_items ORDER BY id').fetchall()
for p in (R/Q).glob('*.sq'):c.executescript(ddl(p))
assert old_rows==c.execute('SELECT * FROM memory_items ORDER BY id').fetchall()
passed('Opening upgraded schema preserves old memory rows exactly')
insertSource=query('MemorySourceQueue.sq','insertMemorySource')
def source(ref,kind='PRIVATE_CHAT',owner='op',text='old text',at=1):
 c.execute(insertSource,(kind,'operator',owner,ref,text,at,0,0,'pending',0,0,0,'','',at))
for ref in ['a_%','a_%:epoch123:1-3','a_XX:epoch123:1-3','a_%other:epoch123:1-3']:source(ref)
c.execute(query('MemorySourceQueue.sq','deleteMemorySourcesBySession'),{'sessionId':'a_%'})
left={r[0] for r in c.execute('SELECT sourceRefId FROM memory_source_queue')}
assert left=={'a_XX:epoch123:1-3','a_%other:epoch123:1-3'}
passed('Session purge removes old and epoch-window identities without wildcard/adjacent-session over-deletion')
# Rollback fixtures prove source and durable intent share one commit.
c.commit()
try:
 with c:
  c.execute(query('Diaries.sq','insertDiary'),('op','角色','日记内容','2026-10-15',1,200))
  diaryId=c.execute('SELECT last_insert_rowid()').fetchone()[0]
  source('diary_'+str(diaryId),'DIARY',text='日记内容',at=200)
  raise RuntimeError('process failure before commit')
except RuntimeError:pass
assert c.execute('SELECT COUNT(*) FROM diaries').fetchone()[0]==0
assert not c.execute("SELECT 1 FROM memory_source_queue WHERE sourceKind='DIARY'").fetchone()
with c:
 c.execute(query('Diaries.sq','insertDiary'),('op','角色','日记内容','2026-10-15',1,200))
 diaryId=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 source('diary_'+str(diaryId),'DIARY',text='日记内容',at=200)
assert c.execute(query('Diaries.sq','getDiaryById'),(diaryId,)).fetchone()[3]=='日记内容'
passed('Diary source and memory intent commit/roll back together, preserving real diary ID')
assert c.execute(query('Diaries.sq','getNextVersion'),('op','2026-10-15')).fetchone()[0]==2
assert c.execute(query('Diaries.sq','getNextVersion'),('op','2026-10-16')).fetchone()[0]==1
passed('New diary versions increment within one day while original versions remain unchanged')
turnInsert=query('ReplyTurns.sq','insertReplyTurnIfAbsent')
try:
 with c:
  c.execute("INSERT INTO moments(operatorId,operatorName,content,isUserPost,mentionedOperatorIds,likeCount,commentCount,createdAt) VALUES('user','用户','正文',1,'op',0,0,200)")
  momentId=c.execute('SELECT last_insert_rowid()').fetchone()[0]
  c.execute(turnInsert,('reply','moment:'+str(momentId),'moment_comment','moment_comment',None,'payload','online',200,200,200))
  raise RuntimeError('interruption')
except RuntimeError:pass
assert c.execute('SELECT COUNT(*) FROM moments').fetchone()[0]==0
assert c.execute('SELECT COUNT(*) FROM reply_turns').fetchone()[0]==0
passed('New moment and reply intents roll back together on interruption')
with c:
 c.execute("INSERT INTO moments(operatorId,operatorName,content,isUserPost,mentionedOperatorIds,likeCount,commentCount,createdAt) VALUES('user','用户','正文',1,'op',0,0,200)")
 momentId=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 c.execute(query('MomentComments.sq','insertComment'),(momentId,'user','用户','评论',0,'',200,1))
 commentId=c.execute('SELECT last_insert_rowid()').fetchone()[0]
 args=('reply','moment:'+str(momentId),'moment_comment','moment_comment',None,'payload','online',200,200,200)
 c.execute(turnInsert,args);c.execute(turnInsert,args)
assert c.execute('SELECT COUNT(*) FROM reply_turns').fetchone()[0]==1
assert c.execute(query('ReplyTurns.sq','selectPendingMomentReplyTurns')).fetchall()==[('reply',)]
passed('New comment task survives restart lookup and repeated insert cannot duplicate it')
# Stage vector recovery state independently of L1 success.
source('session:epoch1:1-3')
sid=c.execute("SELECT id FROM memory_source_queue WHERE sourceRefId='session:epoch1:1-3'").fetchone()[0]
c.execute(query('MemorySourceQueue.sq','claimMemorySourceById'),(1000,'token',sid,100))
c.execute(query('MemorySourceQueue.sq','completeMemorySource'),(sid,'token'))
assert c.execute('SELECT processedL1,processedVector FROM memory_source_queue WHERE id=?',(sid,)).fetchone()==(1,0)
c.execute(query('VectorMemories.sq','insertVectorMemory'),('vec','operator','op','memory_v2_l1','s','same fact',.5,'[1,0]','','private','signature',1,9999))
c.execute(query('MemoryItems.sq','updateMemoryItemVectorId'),('vec',1,mid))
c.execute(query('MemorySourceQueue.sq','refreshSourceVectorCompletion'),('PRIVATE_CHAT','session:epoch1:1-3'))
# The nullable-privacy fixture has the same source and is still missing; never claim full recovery yet.
assert c.execute('SELECT processedVector FROM memory_source_queue WHERE id=?',(sid,)).fetchone()[0]==0
c.execute("UPDATE memory_items SET vectorId='vec' WHERE sourceRefId='session:epoch1:1-3'")
c.execute(query('MemorySourceQueue.sq','refreshSourceVectorCompletion'),('PRIVATE_CHAT','session:epoch1:1-3'))
assert c.execute('SELECT processedVector FROM memory_source_queue WHERE id=?',(sid,)).fetchone()[0]==1
passed('L1 success does not falsely mark missing vectors complete; completion updates only after all source vectors exist')
states=dict(c.execute(query('MemoryItems.sq','getMemoryIndexStates'),{'now':100,'signature':'signature'}))
assert states[mid]=='ready'
assert dict(c.execute(query('MemoryItems.sq','getMemoryIndexStates'),{'now':100,'signature':'other'}))[mid]=='stale'
c.execute('DELETE FROM vector_memories WHERE id=?',('vec',))
assert dict(c.execute(query('MemoryItems.sq','getMemoryIndexStates'),{'now':100,'signature':'signature'}))[mid]=='missing'
assert not c.execute(query('MemoryItems.sq','hasUsableMemoryVector'),(mid,'signature',100)).fetchone()[0]
passed('Index metadata distinguishes usable, mismatched and missing vectors without network requests')
c.execute(query('Memories.sq','insertMemory'),('session','op','DAILY','旧日摘要','20261014','','',100,9999))
c.execute(query('Memories.sq','insertMemory'),('session','op','DAILY','昨日摘要','20261015','','',200,9999))
assert c.execute(query('Memories.sq','getDailyBySessionAndDate'),('session','20261015')).fetchone()[4]=='昨日摘要'
assert c.execute(query('Memories.sq','getDailyBySessionAndDate'),('session','20261016')).fetchone() is None
passed('Daily summary selection respects exact target date instead of latest unrelated day')
out={'checks':checks,'count':len(checks),'baseline':str(B),'real_device_upgrade_tested':False,'uses_user_database':False}
Path('memory-upgrade-verification-1.14.18.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(len(checks),'checks passed')
