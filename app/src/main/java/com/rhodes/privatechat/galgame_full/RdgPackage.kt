package com.rhodes.privatechat.galgame_full

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID

object RdgPackage {
    private const val MAX_PACKAGE_ENTRIES = 200
    private const val MAX_PACKAGE_BYTES = 80L * 1024 * 1024

    fun exportEntry(context: Context, entry: LibraryEntry, currentState: GameState, currentAssets: ProjectAssets, destination: Uri): Result<Unit> = runCatching {
        val state = currentState.takeIf { it.projectId == entry.id } ?: GameStore.stateFromJson(entry.stateJson)
        val assets = if (currentState.projectId == entry.id) currentAssets else GameStore.assetsFromJson(entry.assetsJson)
        export(context, state, assets, destination).getOrThrow()
    }

    fun exportEntryHtml(context: Context, entry: LibraryEntry, currentState: GameState, currentAssets: ProjectAssets, destination: Uri): Result<Unit> = runCatching {
        val state = currentState.takeIf { it.projectId == entry.id } ?: GameStore.stateFromJson(entry.stateJson)
        val assets = if (currentState.projectId == entry.id) currentAssets else GameStore.assetsFromJson(entry.assetsJson)
        exportHtml(context, state, assets, destination).getOrThrow()
    }

    fun exportHtml(context: Context, state: GameState, assets: ProjectAssets, destination: Uri): Result<Unit> = runCatching {
        val initial = createInitialGameState(state.projectId.ifBlank { "export" }, state.project)
        val assetsJson = JSONObject(GameStore.snapshot(context, state.projectId.ifBlank { "export" }, state, assets).assetsJson)
        assetsJson.optJSONArray("sprites")?.inlineData(context, "image/png")
        assetsJson.optJSONArray("backgrounds")?.inlineData(context, "image/jpeg")
        val payload = JSONObject()
            .put("version", 1)
            .put("exportId", System.currentTimeMillis())
            .put("initialState", JSONObject(GameStore.snapshot(context, initial.projectId, initial, assets).stateJson))
            .put("assets", assetsJson)
            .toString().replace("</", "<\\/")
        val html = H5_TEMPLATE.replace("__GAME_DATA__", payload)
            .replace("text:String(x.text||'').slice(0,600)", "text:String(x.text||'').slice(0,120)")
            .replace("text:String(j.narration).slice(0,600)", "text:String(j.narration).slice(0,120)")
            .replace("state.scene.visibleSpriteId=lines[0].spriteId||null;", "state.scene.visibleSpriteId=lines[0].spriteId||null;applyH5State(j,c);")
            .replace("if(j.chapter_progress?.completion){", "if(j.chapter_progress?.completion&&(!(c.allowedBackgroundIds||[]).length||true)&&!(c.requiredFlag&&!(state.events||[]).includes(c.requiredFlag))&&((c.minAffection||0)<=Number(state.affection||0))){")
        write(context, destination, html.toByteArray(Charsets.UTF_8))
    }

    fun export(context: Context, state: GameState, assets: ProjectAssets, destination: Uri): Result<Unit> = runCatching {
        val entry = GameStore.snapshot(context, state.projectId.ifBlank { "export" }, state, assets)
        val assetsJson = JSONObject(entry.assetsJson)
        writeStream(context, destination).use { output -> ZipOutputStream(output).use { zip ->
            fun text(path: String, value: String) { zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            fun asset(uri: String, path: String) { GameStore.openAssetInput(context, uri)?.use { input -> zip.putNextEntry(ZipEntry(path)); input.copyTo(zip); zip.closeEntry() } ?: error("无法读取资源：$uri") }
            assetsJson.optJSONArray("sprites")?.let { array -> for (i in 0 until array.length()) { val item = array.getJSONObject(i); val path = "sprites/${item.optString("id")}.png"; asset(item.getString("uri"), path); item.put("uri", path) } }
            assetsJson.optJSONArray("backgrounds")?.let { array -> for (i in 0 until array.length()) { val item = array.getJSONObject(i); val path = "backgrounds/${item.optString("id")}.img"; asset(item.getString("uri"), path); item.put("uri", path) } }
            text("manifest.json", JSONObject().put("format", "rdg").put("version", 1).put("title", state.project.title).toString())
            text("state.json", entry.stateJson); text("assets.json", assetsJson.toString())
        } }
    }

    fun import(context: Context, source: Uri): Result<LibraryEntry> = runCatching {
        val temp = File(context.filesDir, "rdg_import_${UUID.randomUUID()}").apply { require(mkdirs()) { "无法创建导入临时目录" } }
        try {
            var stateJson = ""; var assetsJson = ""; var manifestJson = ""
            val names = mutableSetOf<String>()
            context.contentResolver.openInputStream(source)?.use { input -> ZipInputStream(input).use { zip ->
                var count = 0; var bytes = 0L; var item = zip.nextEntry
                while (item != null) {
                    require(++count <= MAX_PACKAGE_ENTRIES) { "游戏包文件过多，无法导入" }
                    val normalizedName = item.name.replace('\\', '/').replace(Regex("/+"), "/")
                    require(names.add(normalizedName)) { "游戏包包含重复文件：${item.name}" }
                    val target = File(temp, item.name)
                    require(target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) { "游戏包包含非法文件路径" }
                    if (item.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; bytes += n; require(bytes <= MAX_PACKAGE_BYTES) { "游戏包解压后超过 80 MB，无法导入" }; out.write(buffer, 0, n) }
                    } }
                    when (item.name) { "state.json" -> stateJson = target.readText(); "assets.json" -> assetsJson = target.readText(); "manifest.json" -> manifestJson = target.readText() }
                    item = zip.nextEntry
                }
            } } ?: error("无法读取导入文件")
            require(stateJson.isNotBlank() && assetsJson.isNotBlank() && manifestJson.isNotBlank()) { "RDG 包缺少 manifest.json、state.json 或 assets.json" }
            val manifest = JSONObject(manifestJson)
            require(manifest.optString("format") == "rdg" && manifest.optInt("version") == 1) { "不支持的 RDG 包格式或版本" }
            val assets = JSONObject(assetsJson); validateAssets(temp, assets)
            val state = JSONObject(stateJson); val project = state.optJSONObject("project") ?: error("项目数据无效")
            validateProject(project, assets)
            val id = "import_${UUID.randomUUID()}"; val stable = File(context.filesDir, "rdg/$id").apply { parentFile?.mkdirs(); require(mkdirs()) { "无法创建导入资源目录" } }
            try {
                temp.copyRecursively(stable, overwrite = true)
                rewriteAssetUris(assets, stable)
                state.put("projectId", id)
                val imported = LibraryEntry(id, project.optString("title", "导入的游戏"), project.optString("description"), state.toString(), assets.toString())
                GameStore.restore(context, imported); GameStore.publish(context, imported); imported
            } catch (error: Throwable) {
                stable.deleteRecursively()
                throw error
            }
        } finally { temp.deleteRecursively() }
    }

    private fun JSONArray.inlineData(context: Context, mime: String) { for (i in 0 until length()) { val item = getJSONObject(i); val uri = Uri.parse(item.getString("uri")); val bytes = if (uri.scheme == "file") File(uri.path.orEmpty()).takeIf { it.exists() }?.readBytes() else context.contentResolver.openInputStream(uri)?.use { it.readBytes() }; val value = bytes ?: error("无法读取资源：$uri"); val type = context.contentResolver.getType(uri) ?: mime; item.put("uri", "data:$type;base64," + Base64.encodeToString(value, Base64.NO_WRAP)) } }
    private fun validateAssets(root: File, assets: JSONObject) {
        val usedIds = mutableSetOf<String>()
        fun check(name: String, directory: String, alpha: Boolean) {
            assets.optJSONArray(name)?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("id")
                    require(id.isNotBlank() && usedIds.add("$name:$id")) { "资源 ID 为空或重复：$id" }
                    val relative = item.optString("uri").replace('\\', '/')
                    require(relative.startsWith("$directory/") && !relative.contains("../") && !relative.contains("/..")) { "资源路径无效：$relative" }
                    val file = File(root, relative)
                    require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "资源路径越界：$relative" }
                    require(file.exists() && file.length() > 0) { "缺少或为空的资源文件：$relative" }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    require(bitmap != null) { "无法读取图片资源：$relative" }
                    if (alpha) require(bitmap.hasAlpha()) { "立绘缺少透明通道：$relative" }
                }
            }
        }
        check("sprites", "sprites", true)
        check("backgrounds", "backgrounds", false)
    }
    private fun validateProject(project: JSONObject, assets: JSONObject) {
        val chars = project.optJSONArray("characters") ?: JSONArray(); val characterIdList = (0 until chars.length()).map { chars.getJSONObject(it).optString("id") }; val ids = characterIdList.toSet(); require(characterIdList.all { it.isNotBlank() } && ids.size == characterIdList.size) { "角色 ID 为空或重复" }; require(project.optString("playerCharacterId") in ids) { "项目缺少有效的玩家角色" }
        val spriteOwners = assets.optJSONArray("sprites")?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).optString("id") to a.getJSONObject(it).optString("characterId") } }.orEmpty(); val bgIds = assets.optJSONArray("backgrounds")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") }.toSet() }.orEmpty()
        for (i in 0 until chars.length()) { val c = chars.getJSONObject(i); require(c.optString("name").isNotBlank() && c.optString("personality").isNotBlank()) { "存在名称或设定不完整的角色" }; val refs = c.optJSONArray("sprites") ?: JSONArray(); require(refs.length() > 0) { "角色 ${c.optString("name")} 缺少立绘" }; for (j in 0 until refs.length()) { val id = refs.getJSONObject(j).optString("assetId"); require(spriteOwners[id] == c.optString("id")) { "角色引用了缺失或不匹配的立绘" } } }
        project.optJSONArray("chapters")?.let { chapters -> for (i in 0 until chapters.length()) { val c = chapters.getJSONObject(i); require(c.optString("title").isNotBlank() && c.optString("contentDescription").isNotBlank()) { "第${c.optInt("id", i + 1)}章缺少内容" }; val allowed = c.optJSONArray("allowedCharacterIds") ?: JSONArray(); require((0 until allowed.length()).any { allowed.optString(it) == project.optString("playerCharacterId") }) { "章节没有包含玩家角色" }; for (j in 0 until allowed.length()) require(allowed.optString(j) in ids) { "章节引用了不存在的角色" }; val bgs = c.optJSONArray("allowedBackgroundIds") ?: JSONArray(); for (j in 0 until bgs.length()) require(bgs.optString(j) in bgIds) { "章节引用了不存在的场景" } } }
    }
    private fun rewriteAssetUris(assets: JSONObject, stable: File) { for (name in listOf("sprites", "backgrounds")) assets.optJSONArray(name)?.let { a -> for (i in 0 until a.length()) { val item = a.getJSONObject(i); val file = File(stable, item.getString("uri")); require(file.canonicalPath.startsWith(stable.canonicalPath + File.separator)) { "资源路径越界" }; item.put("uri", Uri.fromFile(file).toString()) } } }
    private fun write(context: Context, destination: Uri, bytes: ByteArray) { writeStream(context, destination).use { it.write(bytes) } }
    private fun writeStream(context: Context, uri: Uri) = if (uri.scheme == "file") FileOutputStream(File(uri.path!!)) else context.contentResolver.openOutputStream(uri) ?: error("无法创建导出文件")
}

private val H5_TEMPLATE = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>AI Galgame</title><style>
*{box-sizing:border-box}body{margin:0;background:#10111b;color:#fff;font-family:system-ui,"Microsoft YaHei",sans-serif}.hidden{display:none!important}.setup{min-height:100dvh;max-width:600px;margin:auto;padding:24px;background:linear-gradient(#363254,#141522)}.game{min-height:100dvh;position:relative;overflow:hidden;background:#303044}.bg{position:absolute;z-index:1;inset:0;width:100%;height:100%;object-fit:cover}.sprite{position:absolute;z-index:2;bottom:0;left:50%;height:92%;max-width:none;transform-origin:bottom center}.hud{position:absolute;z-index:4;top:0;left:0;right:0;padding:calc(env(safe-area-inset-top) + 12px) 14px 35px;background:linear-gradient(#141522ef,transparent)}.hudrow{display:flex;gap:8px;align-items:start}.hudtitle{font-size:20px;font-weight:700;flex:1}.muted{color:#c9c7dd;font-size:13px;line-height:1.5}.panel{background:#141522e8;border-radius:18px;padding:14px}.dialogue{position:absolute;z-index:5;left:12px;right:12px;bottom:calc(env(safe-area-inset-bottom) + 10px);max-height:56dvh;overflow:auto}.speaker{color:#e9a8bd;font-weight:700;font-size:13px}.line{line-height:1.55;margin:5px 0 10px;white-space:pre-wrap}.row{display:flex;gap:8px;flex-wrap:wrap}.row>*{flex:1}input,textarea,select{width:100%;margin-top:6px;border:1px solid #938baf;border-radius:10px;background:#171827;color:#fff;padding:11px;font:inherit}button{border:0;border-radius:10px;background:#e9a8bd;color:#141522;padding:11px 14px;font-weight:700;font:inherit;margin-top:8px}button.secondary{background:#383651;color:#fff}.choice{display:block;width:100%;text-align:left;background:#383651;color:#fff}.status{color:#e9a8bd;font-size:13px;white-space:pre-wrap;margin-top:8px}.loading{opacity:.65;pointer-events:none}.modal{position:fixed;z-index:20;inset:0;background:#141522f5;padding:24px;overflow:auto}.modal .panel{max-width:600px;margin:auto}.slot{display:flex;gap:8px;align-items:center}.slot span{flex:1}
</style></head><body><section id="setup" class="setup"><h1>AI Galgame</h1><p class="muted">H5 不包含作者 API Key，请填写你自己的接口配置。浏览器直连需要接口允许 CORS。</p><label>接口地址<input id="url" value="https://api.deepseek.com/chat/completions"></label><label>模型<input id="model" value="deepseek-v4-flash"></label><label>API Key<input id="key" type="password" autocomplete="off"></label><div class="row"><button class="secondary" id="test">测试连接</button><button id="start">开始游玩</button></div><div id="setupStatus" class="status"></div></section><section id="game" class="game hidden"><img id="bg" class="bg"><img id="sprite" class="sprite"><header class="hud"><div class="hudrow"><div><div id="title" class="hudtitle"></div><div id="chapter" class="muted"></div></div><button class="secondary" id="more">更多</button></div></header><main id="dialogue" class="dialogue panel"></main></section><div id="modal" class="modal hidden"><div class="panel"><div id="modalContent"></div><button class="secondary" id="closeModal">关闭</button></div></div><script>
 const DATA=__GAME_DATA__,DEFAULT_CHOICES=['继续观察','询问当前情况','表达自己的想法'];let state=JSON.parse(JSON.stringify(DATA.initialState)),assets=DATA.assets,config={},loading=false;const q=s=>document.querySelector(s),chapter=()=>state.project.chapters.find(x=>x.id===state.chapter)||state.project.chapters[0],key=slot=>'rdg-play-'+state.projectId+'-'+DATA.exportId+'-'+slot,esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function initial(){return JSON.parse(JSON.stringify(DATA.initialState))}function sceneData(){const c=chapter(),b=assets.backgrounds.find(x=>x.id===state.scene.backgroundId)||assets.backgrounds.find(x=>(c.allowedBackgroundIds||[]).includes(x.id)),s=assets.sprites.find(x=>x.id===state.scene.visibleSpriteId);return{b,s}}function render(){const d=sceneData(),s=d.s,b=d.b;q('#title').textContent=state.project.title||'AI Galgame';q('#chapter').textContent='第 '+state.chapter+' 章 · '+(state.goal||chapter().goal||chapter().title||'');q('#bg').src=b?.uri||'';if(b)q('#bg').style.objectPosition=((b.focusX||.5)*100)+'% '+((b.focusY||.5)*100)+'%';q('#sprite').src=s?.uri||'';q('#sprite').style.display=s?'block':'none';if(s){const scale=(state.scene.spriteScale||1)*(s.scale||1)*(assets.globalSpriteScale||1),x=(state.scene.spriteOffsetX||0)+(s.offsetX||0)+(assets.globalSpriteOffsetX||0),y=(state.scene.spriteOffsetY||0)+(s.offsetY||0)+(assets.globalSpriteOffsetY||0);q('#sprite').style.transform='translate(calc(-50% + '+x*100+'%), '+y*100+'%) scale('+scale+')'}const dbox=q('#dialogue'),line=state.lines?.[state.lineIndex||0];if(loading){dbox.innerHTML='<div class="status">正在生成剧情，请稍候……</div>';return}let html='';if(line){const pages=String(line.text||'').match(/.{1,180}/gs)||[''];html+='<div class="speaker">'+esc(line.speaker)+'</div><div class="line">'+esc(pages[state.linePage||0]||pages[pages.length-1])+'</div><button id="next">'+((state.linePage||0)+1<pages.length?'继续阅读':((state.lineIndex||0)+1<(state.lines||[]).length?'下一句':'继续'))+'</button>'}else{(state.messages||[]).slice(-3).forEach(m=>html+='<div class="speaker">'+esc(m.speaker)+'</div><div class="line">'+esc(m.text)+'</div>');const choices=state.choices?.length?state.choices:DEFAULT_CHOICES;(state.pendingTransition||state.endingShown)?null:choices.forEach((x,i)=>html+='<button class="choice" data-choice="'+i+'">'+esc(x)+'</button>');html+='<div class="row"><textarea id="input" rows="2" placeholder="你想做什么？"></textarea><button id="send">发送</button></div>';if(state.pendingTransition)html+='<div class="panel"><h2>'+esc(state.pendingTransition.title)+'</h2><p class="muted">'+esc(state.pendingTransition.subtitle)+'</p><button id="transition">继续</button></div>';if(state.endingShown)html='<div class="panel"><h2>故事已完结</h2><p class="muted">你可以重新体验，也可以继续游玩。</p><div class="row"><button id="restart">重新开始</button><button id="continue">继续游玩</button></div></div>'}dbox.innerHTML=html;q('#next')?.addEventListener('click',advance);q('#send')?.addEventListener('click',()=>{const v=q('#input').value;q('#input').value='';play(v)});document.querySelectorAll('[data-choice]').forEach(b=>b.addEventListener('click',()=>play((state.choices?.length?state.choices:DEFAULT_CHOICES)[+b.dataset.choice])));q('#transition')?.addEventListener('click',nextChapter);q('#restart')?.addEventListener('click',restart);q('#continue')?.addEventListener('click',()=>{state.endingShown=false;saveAuto();render()})}
function advance(){const pages=String(state.lines[state.lineIndex].text||'').match(/.{1,180}/gs)||[''];if((state.linePage||0)+1<pages.length)state.linePage++;else if((state.lineIndex||0)+1<state.lines.length){state.lineIndex++;state.linePage=0;const l=state.lines[state.lineIndex];state.messages.push({speaker:l.speaker,text:l.text})}else{state.lines=[];state.lineIndex=0;state.linePage=0}saveAuto();render()}
async function call(input){const r=await fetch(config.url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+config.key},body:JSON.stringify({model:config.model,temperature:.7,max_tokens:2400,messages:[{role:'system',content:'你是互动视觉小说剧情 Agent，只返回 JSON。不得让玩家角色发言，只能让当前章节允许的 NPC 发言。返回 {"background_id":"","narration":"","lines":[{"speaker_id":"","text":"","sprite_id":""}],"choices":[],"goal":"","chapter_progress":{"completion":false,"summary":""}}，最多3句台词、2到3个选项。'}, {role:'user',content:runtime(input)}]})});const text=await r.text();if(!r.ok)throw Error('HTTP '+r.status);return JSON.parse(text)}function runtime(input){const c=chapter();return('项目：'+state.project.title+'\n简介：'+state.project.description+'\n章节：'+c.title+'\n目标：'+(state.goal||c.goal)+'\n内容：'+c.contentDescription+'\n允许角色：'+(c.allowedCharacterIds||[]).join(',')+'\n角色：'+state.project.characters.map(x=>x.id+'|'+x.name+'|'+x.personality).join('\n')+'\n立绘：'+assets.sprites.map(x=>x.id+'|'+x.characterId+'|'+x.name+'|'+x.usageCondition).join('\n')+'\n最近对话：'+state.messages.slice(-12).map(x=>x.speaker+'：'+x.text).join('\n')+'\n记忆：'+(state.storyMemory||'')+'\n玩家输入：'+input).slice(-18000)}async function play(input){if(loading||!String(input||'').trim()||state.pendingTransition||state.endingShown)return;loading=true;state.choices=[];state.messages.push({speaker:'你',text:String(input).trim()});render();try{const raw=(await call(input.trim())).choices[0].message.content,j=JSON.parse(raw.slice(raw.indexOf('{'),raw.lastIndexOf('}')+1)),c=chapter(),allowed=c.allowedCharacterIds||[];const lines=(j.lines||[]).filter(x=>allowed.includes(x.speaker_id)&&x.speaker_id!==state.project.playerCharacterId).slice(0,3).map(x=>{const a=assets.sprites.find(s=>s.id===x.sprite_id&&s.characterId===x.speaker_id)||assets.sprites.find(s=>s.characterId===x.speaker_id);return{speakerId:x.speaker_id,speaker:state.project.characters.find(c=>c.id===x.speaker_id)?.name||x.speaker_id,text:String(x.text||'').slice(0,600),spriteId:a?.id||null}}).filter(x=>x.text);if(j.narration)lines.unshift({speakerId:'',speaker:'旁白',text:String(j.narration).slice(0,600),spriteId:null});if(!lines.length)throw Error('AI 未返回有效台词');state.lines=lines;state.lineIndex=0;state.linePage=0;state.messages.push({speaker:lines[0].speaker,text:lines[0].text});state.scene.visibleCharacterId=lines[0].speakerId||null;state.scene.visibleSpriteId=lines[0].spriteId||null;state.choices=(j.choices||[]).map(x=>typeof x==='string'?x:(x?.text||x?.label||'')).filter(Boolean).slice(0,3);state.goal=String(j.goal||state.goal).slice(0,100);state.chapterTurns=(state.chapterTurns||0)+1;state.storyMemory=(state.storyMemory+'\n'+lines.map(x=>x.speaker+'：'+x.text).join('；')+'\n'+(j.chapter_progress?.summary||'')).slice(-6000);if(j.chapter_progress?.completion){const next=state.project.chapters.some(x=>x.id>state.chapter);state.pendingTransition={type:next?'chapter':'ending',title:next?'本章完成':'故事已完结',subtitle:next?'可以进入下一章。':'最后一章已经完成。'}}}catch(e){state.messages.push({speaker:'系统',text:'AI 请求失败：'+e.message})}finally{loading=false;saveAuto();render()}}
function applyH5State(j,c){if((c.allowedBackgroundIds||[]).includes(j.background_id))state.scene.backgroundId=j.background_id;for(const v of state.project.variables||[]){const delta=Number(j.variable_deltas?.[v.id]||0);const low=Math.max(v.minimum??0,(state.variables?.[v.id]??v.initial??0)+(Math.max(v.perTurnMinimum??-5,Math.min(v.perTurnMaximum??5,delta)));const high=v.maximum??100;state.variables[v.id]=Math.min(high,low)}const itemIds=new Set((state.project.items||[]).map(x=>x.id)),eventIds=new Set((state.project.events||[]).map(x=>x.id));state.items=(state.items||[]).filter(x=>itemIds.has(x)&&!(j.remove_item_ids||[]).includes(x));for(const id of j.grant_item_ids||[])if(itemIds.has(id)&&!state.items.includes(id))state.items.push(id);state.events=(state.events||[]).filter(x=>eventIds.has(x)&&!(j.clear_event_ids||[]).includes(x));for(const id of j.set_event_ids||[])if(eventIds.has(id)&&!state.events.includes(id))state.events.push(id)}
function nextChapter(){const next=state.project.chapters.filter(x=>x.id>state.chapter).sort((a,b)=>a.id-b.id)[0];if(!next){state.pendingTransition=null;state.endingShown=true}else{state.chapter=next.id;state.chapterTurns=0;state.goal=next.goal;state.lines=[];state.choices=[];state.pendingTransition=null;state.messages.push({speaker:'旁白',text:next.openingDescription||next.contentDescription||'新的故事开始了。'});state.scene={backgroundId:next.allowedBackgroundIds?.[0]||'',visibleCharacterId:null,visibleSpriteId:null,backgroundFocusX:.5,backgroundFocusY:.5,spriteScale:1,spriteOffsetX:0,spriteOffsetY:0}}saveAuto();render()}function restart(){state=initial();for(let i=1;i<=3;i++)localStorage.removeItem(key(i));saveAuto();render()}function saveAuto(){localStorage.setItem(key('auto'),JSON.stringify(state))}function saveSlot(slot){localStorage.setItem(key(slot),JSON.stringify(state))}function load(slot){try{const raw=localStorage.getItem(key(slot));if(raw){state=JSON.parse(raw);saveAuto();render();return true}}catch(e){}return false}function menu(){q('#modalContent').innerHTML='<h2>更多设置</h2><p class="muted">保存或读取三个本地存档槽。</p><div class="slot"><span>存档 1</span><button id="save1">保存</button><button id="load1">读取</button></div><div class="slot"><span>存档 2</span><button id="save2">保存</button><button id="load2">读取</button></div><div class="slot"><span>存档 3</span><button id="save3">保存</button><button id="load3">读取</button></div><button id="restart1">重新开始</button><button id="share1">分享游戏</button><p class="muted">存档和 API 配置仅保存在当前浏览器。</p>';q('#modal').classList.remove('hidden');for(let i=1;i<=3;i++){q('#save'+i).onclick=()=>{saveSlot(i);q('#modalContent').insertAdjacentHTML('beforeend','<p class="status">已保存到存档'+i+'</p>')};q('#load'+i).onclick=()=>{load(i);q('#modal').classList.add('hidden')}}q('#restart1').onclick=()=>{if(confirm('重新开始将清除当前游玩进度，确定吗？')){restart();q('#modal').classList.add('hidden')}};q('#share1').onclick=async()=>{if(navigator.share)await navigator.share({title:state.project.title,text:'分享互动视觉小说'});else alert('请下载 HTML 文件后分享。')}}q('#closeModal').onclick=()=>q('#modal').classList.add('hidden');q('#more').onclick=menu;q('#test').onclick=async()=>{try{config={url:q('#url').value.trim(),model:q('#model').value.trim(),key:q('#key').value.trim()};await call('只返回测试结果');q('#setupStatus').textContent='连接成功。'}catch(e){q('#setupStatus').textContent='连接失败：'+e.message}};q('#start').onclick=()=>{config={url:q('#url').value.trim(),model:q('#model').value.trim(),key:q('#key').value.trim()};if(!config.key){q('#setupStatus').textContent='请填写 API Key';return}try{const raw=localStorage.getItem(key('auto'));if(raw)state=JSON.parse(raw)}catch(e){}q('#setup').classList.add('hidden');q('#game').classList.remove('hidden');render()};
</script></body></html>"""
