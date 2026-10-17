from pathlib import Path
import re,sqlite3,json,hashlib,sys
R=Path(__file__).resolve().parents[1]
B=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(r'C:\Users\Administrator\Documents\ChatGPT\mingrifangzhoutest2\baseline-1.14.15')
Q=Path('shared/src/commonMain/sqldelight/com/rhodes/privatechat/shared/db')
def text(p):return p.read_text(encoding='utf-8-sig')
def ddl(p):return re.split(r'^\w+:\s*$',text(p),maxsplit=1,flags=re.M)[0]
def query(file,name):
 s=text(R/Q/file);m=re.search(r'^'+name+r':\s*\n(.*?);',s,re.M|re.S);assert m,name;return m.group(1)
results=[]
def passed(s):results.append(s);print('PASS:',s)
for old in (B/Q).glob('*.sq'):
 assert ddl(old)==ddl(R/Q/old.name),old.name
passed('All existing SQL table/index definitions unchanged from 1.14.15')
c=sqlite3.connect(':memory:')
for name in ['ChatMessages.sq','ReplyTurns.sq','Moments.sq','MomentComments.sq','VectorMemories.sq']:
 c.executescript(ddl(B/Q/name))
c.execute("INSERT INTO chat_messages(id,sessionId,senderName,content,isMe,timestamp) VALUES(1,'s','角色','原回复',0,100)")
c.execute("INSERT INTO chat_messages(id,sessionId,senderName,content,isMe,timestamp) VALUES(2,'s','用户','第二条',1,50)")
original=c.execute('SELECT * FROM chat_messages ORDER BY id').fetchall()
# New query additions require no migrations; an upgrade open must not alter any stored row.
for name in ['ChatMessages.sq','ReplyTurns.sq','Moments.sq','MomentComments.sq','VectorMemories.sq']:c.executescript(ddl(R/Q/name))
assert original==c.execute('SELECT * FROM chat_messages ORDER BY id').fetchall()
passed('Opening current schema preserves old chat rows byte-for-byte')
q=query('ChatMessages.sq','replaceMessageContentIfUnchanged')
c.execute(q,('错误覆盖',1,'不匹配'));assert c.execute('SELECT content FROM chat_messages WHERE id=1').fetchone()[0]=='原回复'
c.execute(q,('新回复',1,'原回复'));assert c.execute('SELECT content FROM chat_messages WHERE id=1').fetchone()[0]=='新回复'
c.execute('DELETE FROM chat_messages WHERE id=1');c.execute(q,('复活',1,'新回复'));assert not c.execute('SELECT 1 FROM chat_messages WHERE id=1').fetchone()
passed('Conditional regeneration rejects stale edits and never resurrects deleted messages')
page=c.execute(query('ChatMessages.sq','getMessagesAfterCursor'),('s',0,0,1)).fetchall();assert page[0][0]==2
assert c.execute(query('ChatMessages.sq','countMessagesBySession'),('s',)).fetchone()[0]==1
passed('Cursor paging and count operate on real surviving records')
insert=query('ReplyTurns.sq','insertReplyTurnIfAbsent')
args=('task','moment:1','moment_comment','moment_comment',None,'payload','online',0,1,1)
c.execute(insert,args);c.execute(insert,args);assert c.execute('SELECT COUNT(*) FROM reply_turns').fetchone()[0]==1
claim=query('ReplyTurns.sq','claimReplyTurn')
c.execute(claim,('owner1',1000,1,'task',1,1));c.execute(claim,('owner2',1000,2,'task',2,2))
assert c.execute('SELECT leaseToken,attemptCount FROM reply_turns').fetchone()==('owner1',1)
passed('Duplicate scheduling and concurrent claim produce only one task owner')
c.commit()
try:
 with c:
  c.execute("INSERT INTO moment_comments(momentId,operatorId,operatorName,content,parentCommentId,replyToName,createdAt,isRead) VALUES(1,'op','角色','回复',0,'用户',1,0)")
  c.execute(query('ReplyTurns.sq','completeReplyTurn'),(3,3,'task','owner1'))
  raise RuntimeError('Simulated process failure before commit')
except RuntimeError:pass
assert c.execute('SELECT COUNT(*) FROM moment_comments').fetchone()[0]==0
assert c.execute('SELECT status FROM reply_turns').fetchone()[0]=='running'
with c:
 c.execute("INSERT INTO moment_comments(momentId,operatorId,operatorName,content,parentCommentId,replyToName,createdAt,isRead) VALUES(1,'op','角色','回复',0,'用户',1,0)")
 c.execute(query('ReplyTurns.sq','completeReplyTurn'),(3,3,'task','owner1'))
c.execute(claim,('owner3',2000,1001,'task',1001,1001))
assert c.execute('SELECT status FROM reply_turns').fetchone()[0]=='succeeded'
assert c.execute(query('ReplyTurns.sq','countOrphanedSucceededReplyTurns')).fetchone()[0]==0
assert not c.execute(query('ReplyTurns.sq','selectPendingMomentReplyTurns')).fetchall()
passed('Comment+completion rollback together; completed moment task is not reclaimed or orphan-cleaned')
for i in range(350):
 c.execute("INSERT INTO vector_memories(id,ownerType,ownerId,sourceType,sourceId,content,embeddingSignature,expiresAt) VALUES(?,'knowledge_base','book','chunk',?,?,'sig',9999)",(f'{i:04}',str(i),'正文'+str(i)))
cursor='';seen=[]
while True:
 page=c.execute(query('VectorMemories.sq','getKnowledgeVectorPage'),('book','sig',0,cursor,128)).fetchall()
 if not page:break
 seen.extend(row[0] for row in page);cursor=page[-1][0]
assert len(seen)==350 and len(set(seen))==350
assert not c.execute(query('VectorMemories.sq','getKnowledgeVectorPage'),('book','other',0,'',128)).fetchall()
passed('Knowledge paging covers all 350 chunks with no omissions, duplicates or wrong-model vectors')
out={'baseline':str(B),'project':str(R),'checks':results,'device_upgrade_tested':False}
Path('upgrade-verification-1.14.16.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(len(results),'checks passed')
