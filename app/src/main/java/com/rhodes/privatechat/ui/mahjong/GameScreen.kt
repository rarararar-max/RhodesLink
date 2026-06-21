package com.rhodes.privatechat.ui.mahjong

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.game.mahjong.*
import com.rhodes.privatechat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val C1 = Color(0xFF1B3A2D); private val C2 = Color(0xFF244A38); private val C3 = Color(0xFFFF8F00)
private val C4 = Color(0xFFFDD835); private val C5 = Color(0xFF2E4A3A); private val C6 = Color(0xFFFFD700)
private val CARD_BACK = Color(0xFF2E5A3A); private val CARD_EDGE = Color(0xFF4A7A5A)

data class MeldUI(val r: Boolean, val k: Boolean, val p: Boolean, val c: Boolean, val co: List<List<Tile>>?, val dt: Tile, val ds: Seat, val actingSeat: Seat = Seat.EAST)
data class ChatMsg(val sender: String, val text: String, val isAssistant: Boolean = false, val isSystem: Boolean = false)

@Composable
fun GameScreen(game: GameState, onBack: () -> Unit, onSettlement: (SettlementResult) -> Unit, assistantName: String = "", assistantAvatarUri: String = "", avatarMap: Map<String, String> = emptyMap(), onSave: ((GameState) -> Unit)? = null, onGenerateTalk: ((PlayerState, String, Tile?, String, Int, Int, String, List<String>, List<String>, (String) -> Unit) -> Unit)? = null) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var g by remember { mutableStateOf(game, neverEqualPolicy()) }; var sel by remember { mutableStateOf<Int?>(null) }
    var isU by remember { mutableStateOf(false) }; var start by remember { mutableStateOf(false) }; var rnd by remember { mutableIntStateOf(0) }
    var mld by remember { mutableStateOf<MeldUI?>(null) }; var showResult by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var effectText by remember { mutableStateOf("") }; var effectColor by remember { mutableStateOf(Color(0xFFFF5722)) }; var effectPlayer by remember { mutableStateOf("") }
    var thinkingPlayer by remember { mutableStateOf("") }; var tLoopJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var helpBubble by remember { mutableStateOf("") }
    var talkBubble by remember { mutableStateOf<ChatMsg?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showChatPanel by remember { mutableStateOf(false) }
    var chatSearch by remember { mutableStateOf("") }
    var lastPhaseTalkTurn by remember { mutableIntStateOf(-1) }
    val u = g.humanPlayer() ?: return; val dl = g.dealer(); val oi = g.players.filter { !it.isHuman }
    val shouldDealNewGame = remember(game) { game.players.all { it.hand.isEmpty() && it.discards.isEmpty() && it.melds.isEmpty() } }
    val asstName = assistantName.ifBlank { oi.firstOrNull()?.name ?: "" }
    val humanName = u.name.ifBlank { "你" }
    val humanAvatar = avatarMap[u.opId].orEmpty()
    val userShanten = Engine.shanten(u.hand); val isUserTenpai = Engine.isTenpaiState(u.hand)
    val hasYaku = if(isUserTenpai) Engine.checkYakuLocal(u.hand, u.melds, u.seat, g.roundWind, u.melds.isEmpty(), false, u.isRiichi).isValid else false
    val effectAlpha by animateFloatAsState(if(effectText.isNotEmpty())1f else 0f); val curSeat = g.currentPlayer().seat
    val phaseTip = when {
        showResult -> "本局结束，确认后进入结算"
        mld != null -> "有人打出了可响应的牌，请选择 和/碰/吃/杠 或跳过"
        isU && isUserTenpai -> "你的回合：已听牌，可以等自摸，也可以胡别人打出的牌"
        isU -> "你的回合：点选一张手牌，再点出牌。"
        thinkingPlayer.isNotEmpty() -> "${thinkingPlayer}正在思考"
        else -> "${g.roundLabel()}进行中"
    }

    fun showEffect(pn:String,at:String,c:Color=Color(0xFFFF5722)){effectText=at;effectColor=c;effectPlayer=pn}
    fun cnv(event:String,p:PlayerState,tile:String=""){
        // Action events are shown by effects/tiles; chatLog is reserved for spoken lines.
    }
    fun tableTalk(event:String,p:PlayerState,tile:Tile?=null){
        if(p.isHuman)return
        val fallback = AiChat.tableTalk(AiChat.TableTalkContext(event=event,player=p,tile=tile,roundLabel=g.roundLabel(),shanten=Engine.shanten(p.hand),wallLeft=g.wall.size))
        g.chatLog.add("${p.name}：$fallback")
        talkBubble = ChatMsg(p.name,fallback,isAssistant=p.opId==g.assistantOpId)
        scope.launch { delay(4200); if(talkBubble?.text == fallback) talkBubble = null }
        val idx = g.chatLog.lastIndex
        if(event in setOf("chi","pon","kan","ron","tsumo","opening","middle","late")){
            val participants = g.players.map { if (it.isHuman) humanName else it.name }
            val recentChat = g.chatLog.takeLast(8)
            onGenerateTalk?.invoke(p,event,tile,g.roundLabel(),g.wall.size,Engine.shanten(p.hand),fallback,participants,recentChat){ generated ->
                if(generated.isNotBlank() && idx in g.chatLog.indices && !p.isHuman){
                    g.chatLog[idx] = "${p.name}：$generated"
                    talkBubble = ChatMsg(p.name,generated,isAssistant=p.opId==g.assistantOpId)
                    scope.launch { delay(5200); if(talkBubble?.text == generated) talkBubble = null }
                    g = g.copy(chatLog = g.chatLog)
                }
            }
        }
    }
    fun maybePhaseTalk(){
        val phase = when {
            g.wall.size <= 20 -> "late"
            rnd >= 6 -> "middle"
            rnd == 0 -> "opening"
            else -> ""
        }
        if(phase.isNotBlank() && lastPhaseTalkTurn != rnd && Random.nextFloat() < if(phase=="opening")1f else 0.28f){
            lastPhaseTalkTurn = rnd
            val speaker = g.assistantPlayer() ?: g.players.filter{!it.isHuman}.randomOrNull() ?: return
            tableTalk(phase,speaker)
        }
    }
    fun addChat(sender:String, text:String, isAssistant:Boolean=false, isSystem:Boolean=false){
        g.chatLog.add("$sender：$text")
    }
    fun requestHelp(){
        if(u.hand.isEmpty())return
        val asst = g.assistantPlayer() ?: g.players.first{!it.isHuman}
        helpBubble = AiChat.help(asst, u.hand, userShanten)
        scope.launch { delay(4000); helpBubble = "" }
    }
    fun ce(){if(g.wall.isEmpty()){
        cnv("exhaustive", g.players.firstOrNull { it.isHuman } ?: g.players.first())
        showResult=true;g=g.copy();showEffect("流局","牌山已空",Color(0xFF90A4AE))
    }}
    fun win(p:PlayerState){
        g.winnerSeat=p.seat;showResult=true;g=g.copy()
        val isRon=g.lastDiscard!=null&&g.lastDiscardSeat!=null
        showEffect(p.name,if(isRon)"点炮！"else"自摸！",Color(0xFFFFD700))
        tableTalk(if(isRon)"ron"else"tsumo",p,g.lastDiscard)
    }
    fun confirmResult(){showResult=false;if(!settled){settled=true;onSettlement(Engine.settle(g))}}
    fun checkTsumo(p:PlayerState)=Engine.canWin(p.hand,p.melds,p.seat,g.roundWind,p.melds.isEmpty(),true,p.isRiichi,g.ippatsuPlayerIdx>=0&&g.players.indexOf(p)==g.ippatsuPlayerIdx)
    fun checkAllMelds(discard:Tile,fromSeat:Seat){
        val hu=g.humanPlayer()!!
        if(hu.seat!=fromSeat&&!hu.isRiichi){val th=hu.hand.toMutableList().apply{add(discard);sortBy{it.ordinalForSort()}}
        val R=Engine.canWin(th,hu.melds,hu.seat,g.roundWind,hu.melds.isEmpty(),false,hu.isRiichi,g.ippatsuPlayerIdx>=0&&g.players.indexOf(hu)==g.ippatsuPlayerIdx)
        val P=Engine.canPon(hu.hand,discard);val K=Engine.canKan(hu.hand,discard)
        val co=if((hu.seat.ordinal-fromSeat.ordinal+4)%4==1)Engine.canChi(hu.hand,discard)else null;val C=co!=null&&co.isNotEmpty()
        if(R||P||K||C)mld=MeldUI(R,K,P,C,if(C)co else null,discard,fromSeat,hu.seat)}
    }
    fun aiDecideMeld(p:PlayerState,tile:Tile,type:MeldType)=AiDiscard.decideMeld(p,type,g)
    fun gameCopy(g:GameState)=GameSerializer.deserialize(GameSerializer.serialize(g))
    fun removeCalledDiscard(tile:Tile,fromSeat:Seat){
        val fromPlayer=g.players.find{it.seat==fromSeat}?:return
        val idx=fromPlayer.discards.indexOfLast{it==tile}
        if(idx>=0)fromPlayer.discards.removeAt(idx)
    }
    fun executeMeld(p:PlayerState,type:MeldType,tile:Tile,fromSeat:Seat,chiOpts:List<Tile>?=null){
        when(type){
            MeldType.PON->{p.hand.filter{it==tile}.take(2).forEach{p.hand.remove(it)};p.melds.add(Meld(MeldType.PON,listOf(tile,tile,tile),fromSeat))}
            MeldType.KAN->{p.hand.filter{it==tile}.take(3).forEach{p.hand.remove(it)};p.melds.add(Meld(MeldType.KAN,listOf(tile,tile,tile,tile),fromSeat));g.currentTurn=g.players.indexOf(p);if(g.wall.isNotEmpty()){Engine.draw(g)};g.ippatsuPlayerIdx=-1}
            MeldType.CHI->{chiOpts?.forEach{t->val idx=p.hand.indexOf(t);if(idx>=0)p.hand.removeAt(idx)};p.melds.add(Meld(MeldType.CHI,listOf(tile)+(chiOpts?:emptyList()),fromSeat))}
            else->return
        }
        removeCalledDiscard(tile,fromSeat)
        showEffect(p.name,when(type){MeldType.PON->"碰！";MeldType.KAN->"杠！";MeldType.CHI->"吃！";else->"鸣牌"},C3)
        tableTalk(when(type){MeldType.PON->"pon";MeldType.KAN->"kan";MeldType.CHI->"chi";else->"discard"},p,tile)
        g.currentTurn=g.players.indexOf(p);g.ippatsuPlayerIdx=-1
    }
    var aiDiscardOnly: (suspend (PlayerState) -> Unit)? = null
    fun processDiscard(tile:Tile,from:PlayerState):Boolean{
        g.lastDiscard=tile;g.lastDiscardSeat=from.seat
        val ao=g.players.filter{it.seat!=from.seat}.sortedBy{(it.seat.ordinal-from.seat.ordinal+4)%4}
        for(p in ao){if(Engine.isFuriten(p))continue
            if(Engine.canWin(p.hand.toMutableList().apply{add(tile);sortBy{it.ordinalForSort()}},p.melds,p.seat,g.roundWind,p.melds.isEmpty(),false,p.isRiichi,g.ippatsuPlayerIdx>=0&&g.players.indexOf(p)==g.ippatsuPlayerIdx)){
                if(p.isHuman){checkAllMelds(tile,from.seat);return false}
                p.hand.add(tile);p.hand.sortBy{it.ordinalForSort()};showEffect(p.name,"放铳！",Color(0xFFC62828));win(p);return true}
        }
        for(p in ao.filter{!it.isHuman&&!Engine.isTenpaiState(it.hand)}){
            if(Engine.canKan(p.hand,tile) && Random.nextFloat() < 0.18f){
                executeMeld(p,MeldType.KAN,tile,from.seat);g=gameCopy(g);aiDiscardOnly?.let{fn->scope.launch{thinkingPlayer=p.name;fn(p)}};return false
            }
        }
        for(p in ao.filter{!it.isHuman&&!Engine.isTenpaiState(it.hand)}){
            if(Engine.canPon(p.hand,tile) && Random.nextFloat() < 0.24f){
                executeMeld(p,MeldType.PON,tile,from.seat);g=gameCopy(g);aiDiscardOnly?.let{fn->scope.launch{thinkingPlayer=p.name;fn(p)}};return false
            }
        }
        // AI 不主动吃牌，避免顺序判断过复杂；用户仍可响应别人弃牌。
        checkAllMelds(tile,from.seat);if(mld!=null)return false;return true
    }
    aiDiscardOnly = discardOnly@ { p: PlayerState ->
        if(p.hand.isEmpty()){showResult=true;showEffect("流局","手牌异常，牌局结束",Color(0xFF90A4AE));g=gameCopy(g);return@discardOnly}
        delay(350L)
        val tile=AiDiscard.decideDiscard(p,g)
        if(!p.hand.remove(tile)){g=gameCopy(g);return@discardOnly}
        p.discards.add(tile);thinkingPlayer=""
        showEffect(p.name,"打出 ${Tile.tileName(tile)}",Color(0xFF90A4AE));g=gameCopy(g)
        if(Engine.isTenpaiState(p.hand) && Random.nextFloat() < 0.35f)tableTalk("tenpai",p)
        if(!processDiscard(tile,p)){if(mld==null&&!showResult)g.currentTurn=g.players.indexOf(p);g=gameCopy(g);return@discardOnly}
        if(showResult)return@discardOnly
        g.currentTurn=(g.players.indexOf(p)+1)%4;g=gameCopy(g)
    }
    suspend fun aiT(p:PlayerState){
        if(g.wall.isNotEmpty()&&p.hand.size<14)Engine.draw(g);g.ippatsuPlayerIdx=-1
        if(checkTsumo(p)){thinkingPlayer="";showEffect(p.name,"自摸！",Color(0xFFFFD700));win(p);g=gameCopy(g);return}
        delay(400L);val tile=AiDiscard.decideDiscard(p,g);p.hand.remove(tile);p.discards.add(tile);thinkingPlayer=""
        showEffect(p.name,"打出 ${Tile.tileName(tile)}",Color(0xFF90A4AE));g=gameCopy(g)
        if(Engine.isTenpaiState(p.hand) && Random.nextFloat() < 0.35f)tableTalk("tenpai",p)
        if(!processDiscard(tile,p)){if(mld==null&&!showResult)g.currentTurn=g.players.indexOf(p);g=gameCopy(g);return}
        if(showResult)return;g.currentTurn=(g.players.indexOf(p)+1)%4;g=gameCopy(g)
    }
    suspend fun tLoop(){
        while(!showResult&&mld==null){
            val c=g.currentPlayer()
            if(c.isHuman){isU=true;thinkingPlayer="";g.ippatsuPlayerIdx=-1;if(g.wall.isNotEmpty()){Engine.draw(g);rnd++;g=gameCopy(g);if(checkTsumo(c)){win(c);break}}else{ce();break};break}
            else{thinkingPlayer=c.name;delay(800L+Random.nextLong(1200));maybePhaseTalk();aiT(c);rnd++}
        }
    }
    fun launchTLoop(){tLoopJob?.cancel();tLoopJob=scope.launch{tLoop()}}
    fun disc(idx:Int){val human=g.humanPlayer()?:return;if(idx !in human.hand.indices)return;val t=human.hand[idx];human.hand.removeAt(idx);human.discards.add(t);sel=null;isU=false;g.drawnIdx=-1;if(!processDiscard(t,human))g.currentTurn=g.players.indexOf(human);g=gameCopy(g);if(!showResult&&mld==null){g.currentTurn=(g.currentTurn+1)%4;g=gameCopy(g);launchTLoop()}}
    fun dm(t:MeldType){val m=mld?:return;val human=g.humanPlayer()?:return;executeMeld(human,t,m.dt,m.ds,m.co?.firstOrNull());mld=null;isU=true;g=g.copy()}
    fun dp(){mld=null;if(!showResult){g.currentTurn=(g.currentTurn+1)%4;g=g.copy();launchTLoop()}}
    fun doAnkan(tile:Tile){val human=g.humanPlayer()?:return;repeat(4){val idx=human.hand.indexOfFirst{it==tile};if(idx>=0)human.hand.removeAt(idx)};human.melds.add(Meld(MeldType.ANKAN,listOf(tile,tile,tile,tile),Seat.EAST));showEffect(human.name,"暗杠！",C3);g.currentTurn=g.players.indexOf(human);if(g.wall.isNotEmpty()){Engine.draw(g)};g=g.copy();sel=null;isU=true;g.drawnIdx=human.hand.size-1}

    LaunchedEffect(Unit){if(shouldDealNewGame)Engine.deal(g);g=g.copy();start=true}
    LaunchedEffect(start){
        if(!start)return@LaunchedEffect
        if(shouldDealNewGame){
            if(!dl.isHuman){delay(500L+Random.nextLong(800));if(!showResult)aiT(dl);if(!showResult){g.currentTurn=(g.dealerIdx+1)%4;g=g.copy();tLoop()}}else isU=true
        } else {
            if(g.currentPlayer().isHuman) isU=true else tLoop()
        }
    }
    LaunchedEffect(effectText){if(effectText.isNotEmpty()){delay(2000);effectText=""}}
    LaunchedEffect(Unit){while(true){delay(10000);onSave?.invoke(g)}}

    val topOpp=oi.firstOrNull();val lOp=oi.getOrNull(1);val rOp=oi.getOrNull(2)
    Box(Modifier.fillMaxSize()){
    Column(Modifier.fillMaxSize().background(C1).systemBarsPadding().padding(horizontal=4.dp)){
        MahjongTopStatusBar(g, rnd, phaseTip, isU, thinkingPlayer, userShanten, isUserTenpai, hasYaku, u.isRiichi, onExit = { showExitDialog = true }, onRules = { showRuleDialog = true })
        MahjongTableArea(
            game = g,
            humanName = humanName,
            avatarMap = avatarMap,
            currentSeat = curSeat,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical=3.dp)
        )
        Row(Modifier.fillMaxWidth().padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha=0.20f)).clickable{showChatPanel=true}.padding(horizontal=10.dp),contentAlignment=Alignment.CenterStart){
                val lastMsg = g.chatLog.lastOrNull()
                val lastName = lastMsg?.substringBefore("：").orEmpty()
                val lastText = lastMsg?.substringAfter("：", lastMsg).orEmpty()
                Text(if(lastMsg==null)"牌桌聊天 · 点击展开" else "$lastName：$lastText",fontSize=11.sp,color=Color.White.copy(alpha=0.68f),maxLines=1)
            }
            Spacer(Modifier.width(6.dp))
            Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFFF8F00).copy(alpha=0.16f)).border(1.dp,C3.copy(alpha=0.45f),RoundedCornerShape(17.dp)).clickable{requestHelp()}.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically){
                OperatorAvatarImage(avatarUri = assistantAvatarUri, name = asstName, modifier = Modifier.size(25.dp).clip(CircleShape))
                Spacer(Modifier.width(5.dp))
                Column{Text(asstName.take(6),fontSize=9.sp,color=C6,fontWeight=FontWeight.SemiBold,maxLines=1);Text("问建议",fontSize=8.sp,color=Color.White.copy(alpha=0.62f))}
            }
        }
        // 手牌区
        val hc=u.hand.size;val r1=(hc+1)/2
        Column(Modifier.fillMaxWidth().shadow(8.dp,RoundedCornerShape(topStart=14.dp,topEnd=14.dp)).background(Color(0xFF101C18).copy(alpha=0.96f),RoundedCornerShape(topStart=14.dp,topEnd=14.dp)).border(1.dp,Color.White.copy(alpha=0.08f),RoundedCornerShape(topStart=14.dp,topEnd=14.dp)).padding(vertical=6.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                Text("你的手牌",fontSize=11.sp,fontWeight=FontWeight.Bold,color=C4)
                Spacer(Modifier.width(8.dp))
                Text(if(isU)"点击牌后出牌" else "等待对手行动",fontSize=9.sp,color=Color.White.copy(alpha=0.48f))
                Spacer(Modifier.weight(1f))
                Text("${u.hand.size}张",fontSize=9.sp,color=Color.White.copy(alpha=0.56f))
            }
            if(u.melds.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.melds.forEach{m->Row(Modifier.background(Color.White.copy(alpha=0.15f),RoundedCornerShape(3.dp)).padding(2.dp)){m.tiles.forEach{t->PTile(t,w=28.dp,h=38.dp,fs=13.sp)}}}}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.hand.take(r1).forEachIndexed{i,t->HTile52(t,sel==i,Modifier.padding(1.dp),g.drawnIdx>=0&&g.drawnIdx==i){if(isU&&mld==null)if(sel==i)disc(i)else sel=i}}}
            if(hc>r1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.hand.drop(r1).forEachIndexed{i,t->val idx=r1+i;HTile52(t,sel==idx,Modifier.padding(1.dp),g.drawnIdx>=0&&g.drawnIdx==idx){if(isU&&mld==null)if(sel==idx)disc(idx)else sel=idx}}}
        }
        // 按钮栏
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.48f)).padding(horizontal=4.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically){
            val ms=mld
            if(ms!=null){
                    if(ms.r)Bt("胡",Color(0xFFC62828),Modifier.weight(1f)){val d=g.lastDiscard;if(d!=null){val human=g.humanPlayer()?:return@Bt;human.hand.add(d);g=g.copy();win(human)}}
                if(ms.k)Bt("杠",C3,Modifier.weight(1f)){dm(MeldType.KAN)};if(ms.p)Bt("碰",C3,Modifier.weight(1f)){dm(MeldType.PON)};if(ms.c)Bt("吃",C3,Modifier.weight(1f)){dm(MeldType.CHI)}
                Bt("过",Color(0xFF37474F),Modifier.weight(1f)){dp()}
            }else{
                Bt("出牌",C3,Modifier.weight(1f),isU&&sel!=null){if(isU)sel?.let{disc(it)}?:Toast.makeText(ctx,"请选牌",Toast.LENGTH_SHORT).show()}
                val ak=if(isU&&mld==null)Engine.canAnkan(u.hand)else emptyList()
                if(ak.isNotEmpty())ak.forEach{t->Bt("暗杠${Tile.tileName(t)}",Color(0xFF7B1FA2),Modifier.weight(1f)){doAnkan(t)}}
                Bt("退",Color(0xFF37474F),Modifier.weight(1f)){showExitDialog=true}
            }
            val pointDiff = (g.humanPlayer()?.points ?: 25000) - 25000
            Column(horizontalAlignment = Alignment.End) { Text("筹码 ${g.humanPlayer()?.points?:25000}",fontSize=9.sp,color=Color.White.copy(alpha=0.66f)); Text("收益 ${if(pointDiff>=0)"+" else ""}${pointDiff/10}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=C6) }
        }
    }
    // 助手建议气泡
    if(helpBubble.isNotEmpty()){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
            Box(Modifier.padding(horizontal=24.dp).background(Color(0xFF5C6BC0).copy(alpha=0.92f),RoundedCornerShape(12.dp)).padding(horizontal=14.dp,vertical=10.dp)){
                Text(helpBubble,fontSize=13.sp,color=Color.White,fontWeight=FontWeight.Medium,maxLines=3)
            }
        }
    }
    talkBubble?.let { bubble ->
        val speaker = g.players.find { it.name == bubble.sender }
        val avatar = speaker?.let { avatarMap[it.opId] }.orEmpty()
        val alignment = when (speaker) {
            topOpp -> Alignment.Center
            lOp -> Alignment.CenterStart
            rOp -> Alignment.CenterEnd
            else -> Alignment.BottomCenter
        }
        val bubblePadding = when (speaker) {
            lOp -> PaddingValues(start=28.dp,end=18.dp,top=116.dp,bottom=178.dp)
            rOp -> PaddingValues(start=18.dp,end=28.dp,top=116.dp,bottom=178.dp)
            topOpp -> PaddingValues(horizontal=34.dp,vertical=150.dp)
            else -> PaddingValues(horizontal=18.dp, vertical=174.dp)
        }
        Box(Modifier.fillMaxSize().padding(bubblePadding),contentAlignment=alignment){
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.shadow(12.dp,RoundedCornerShape(18.dp)).background(Color(0xFF141A1F).copy(alpha=0.94f),RoundedCornerShape(18.dp)).border(1.dp,if(bubble.isAssistant)C6.copy(alpha=0.65f)else Color.White.copy(alpha=0.12f),RoundedCornerShape(18.dp)).padding(horizontal=12.dp,vertical=10.dp)){
                if(avatar.isNotBlank()) OperatorAvatarImage(avatarUri=avatar,name=bubble.sender,modifier=Modifier.size(34.dp).clip(CircleShape))
                else Box(Modifier.size(34.dp).clip(CircleShape).background(if(bubble.isAssistant)C3 else Primary),contentAlignment=Alignment.Center){Text(bubble.sender.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp)}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.widthIn(max=260.dp)){
                    Text(if(bubble.isAssistant)"${bubble.sender} · 助手" else bubble.sender,fontSize=10.sp,color=if(bubble.isAssistant)C6 else Color.White.copy(alpha=0.62f),fontWeight=FontWeight.SemiBold)
                    Text(bubble.text,fontSize=14.sp,color=Color.White,lineHeight=19.sp,maxLines=3)
                }
            }
        }
    }
    // 牌局动作条：区别于角色聊天气泡
    if(effectAlpha>0.01f && effectText.isNotEmpty()){
        val effectOp = g.players.find { it.name == effectPlayer }
        val effectAvatar = effectOp?.let { avatarMap[it.opId] }
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=96.dp).shadow(8.dp,RoundedCornerShape(22.dp)).background(effectColor.copy(alpha=0.90f*effectAlpha),RoundedCornerShape(22.dp)).border(1.dp,Color.White.copy(alpha=0.35f*effectAlpha),RoundedCornerShape(22.dp)).padding(horizontal=14.dp,vertical=8.dp)){
            if(!effectAvatar.isNullOrBlank()) OperatorAvatarImage(avatarUri=effectAvatar,name=effectPlayer,modifier=Modifier.size(28.dp).clip(CircleShape))
            else Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha=0.22f)),contentAlignment=Alignment.Center){Text(effectPlayer.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp)}
            Spacer(Modifier.width(8.dp))
            Text(effectPlayer,fontSize=11.sp,color=Color.White.copy(alpha=0.88f),fontWeight=FontWeight.SemiBold,maxLines=1)
            Spacer(Modifier.width(8.dp))
            Text(effectText,fontSize=15.sp,fontWeight=FontWeight.Bold,color=Color.White.copy(alpha=effectAlpha),maxLines=1)
        }
    }
    }
    if(showResult&&g.winnerSeat!=null){val w=g.players.find{it.seat==g.winnerSeat}!!;val isRon=g.lastDiscard!=null&&g.lastDiscardSeat!=null;val lName=if(isRon)g.players.find{it.seat==g.lastDiscardSeat}?.name else null
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)),contentAlignment=Alignment.Center){Box(Modifier.widthIn(max=340.dp).heightIn(max=520.dp).background(C1,RoundedCornerShape(12.dp)).padding(16.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.verticalScroll(rememberScrollState())){Text("${w.name} ${if(isRon)"点炮和牌！"else"自摸和牌！"}",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFFD700))
        if(lName!=null)Text("放铳者：$lName",fontSize=13.sp,color=Color.White.copy(alpha=0.7f));Spacer(Modifier.height(8.dp))
        g.players.forEach{p->val label=if(p.isHuman)humanName else p.name;val pAvatar=if(p.isHuman)humanAvatar else avatarMap[p.opId];Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp)){
            if(pAvatar!=null) OperatorAvatarImage(avatarUri=pAvatar,name=p.name,modifier=Modifier.size(28.dp).clip(CircleShape))
            else Box(Modifier.size(28.dp).clip(CircleShape).background(Primary),contentAlignment=Alignment.Center){Text(label.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=11.sp)}
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)){Row(Modifier.horizontalScroll(rememberScrollState())){p.hand.sortedBy{it.ordinalForSort()}.forEach{t->PTile(t,w=24.dp,h=34.dp,fs=11.sp)}};if(p.melds.isNotEmpty()){Row(Modifier.horizontalScroll(rememberScrollState())){p.melds.forEach{m->Box(Modifier.padding(0.5.dp).background(Color.White.copy(alpha=0.1f),RoundedCornerShape(2.dp)).padding(1.dp)){Row{m.tiles.forEach{t->PTile(t,w=20.dp,h=28.dp,fs=9.sp)}}}}}}}
        }}
        Spacer(Modifier.height(4.dp));Text("牌墙剩余：${g.wall.size}张",fontSize=11.sp,color=Color.White.copy(alpha=0.6f));Spacer(Modifier.height(10.dp))
        Bt("确认",C3,Modifier.fillMaxWidth()){confirmResult()}}}}
    }
    if(showResult&&g.winnerSeat==null){
        val tenpaiNames = g.players.filter{Engine.isTenpaiState(it.hand)}.map{it.name}
        val notenNames = g.players.filter{!Engine.isTenpaiState(it.hand)}.map{it.name}
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)),contentAlignment=Alignment.Center){Box(Modifier.widthIn(max=340.dp).heightIn(max=520.dp).background(C1,RoundedCornerShape(12.dp)).padding(16.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.verticalScroll(rememberScrollState())){
        Text("流局",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color(0xFF90A4AE))
        Spacer(Modifier.height(8.dp))
        if(tenpaiNames.isNotEmpty())Text("听牌：${tenpaiNames.joinToString("、")}",fontSize=13.sp,color=Color(0xFF4CAF50))
        if(notenNames.isNotEmpty())Text("未听：${notenNames.joinToString("、")}",fontSize=13.sp,color=Color(0xFFC62828))
        Spacer(Modifier.height(8.dp))
        g.players.forEach{p->val label=if(p.isHuman)humanName else p.name;val tenpai=Engine.isTenpaiState(p.hand);val pAvatar=if(p.isHuman)humanAvatar else avatarMap[p.opId];Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp)){
            if(pAvatar!=null) OperatorAvatarImage(avatarUri=pAvatar,name=p.name,modifier=Modifier.size(28.dp).clip(CircleShape))
            else Box(Modifier.size(28.dp).clip(CircleShape).background(Primary),contentAlignment=Alignment.Center){Text(label.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=11.sp)}
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)){Row(Modifier.horizontalScroll(rememberScrollState())){p.hand.sortedBy{it.ordinalForSort()}.forEach{t->PTile(t,w=24.dp,h=34.dp,fs=11.sp)}};if(p.melds.isNotEmpty()){Row(Modifier.horizontalScroll(rememberScrollState())){p.melds.forEach{m->Box(Modifier.padding(0.5.dp).background(Color.White.copy(alpha=0.1f),RoundedCornerShape(2.dp)).padding(1.dp)){Row{m.tiles.forEach{t->PTile(t,w=20.dp,h=28.dp,fs=9.sp)}}}}}}}
        }}
        Spacer(Modifier.height(10.dp))
        Bt("确认",C3,Modifier.fillMaxWidth()){confirmResult()}}}}
    }
    if(showExitDialog){
        AlertDialog(
            onDismissRequest={showExitDialog=false},
            title={Text("退出牌局",fontWeight=FontWeight.SemiBold,color=TextPrimary)},
            text={Text("当前进度会自动保存，下次进入可继续。确定退出？",fontSize=14.sp,color=TextSecondary)},
            confirmButton={TextButton(onClick={showExitDialog=false;onSave?.invoke(g);onBack()}){Text("退出",color=ErrorRed,fontWeight=FontWeight.SemiBold)}},
            dismissButton={TextButton(onClick={showExitDialog=false}){Text("继续打牌",color=Primary)}}
        )
    }
    if(showRuleDialog){
        BasicMahjongRuleDialog(onDismiss={showRuleDialog=false})
    }
    if(showChatPanel){
        val filtered = remember(g.chatLog.toList(), chatSearch) { if(chatSearch.isBlank()) g.chatLog else g.chatLog.filter { it.contains(chatSearch, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest={showChatPanel=false},
            containerColor=Color(0xFF101C18),
            title={Text("牌桌聊天",fontWeight=FontWeight.SemiBold,color=C4)},
            text={
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1512)).border(1.dp,Color.White.copy(alpha=0.08f),RoundedCornerShape(14.dp)).padding(10.dp)) {
                    OutlinedTextField(
                        value=chatSearch,
                        onValueChange={chatSearch=it},
                        singleLine=true,
                        placeholder={Text("搜索角色或内容")},
                        modifier=Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max=360.dp)){
                        itemsIndexed(filtered){_,msg->
                            val name=msg.substringBefore("：")
                            val text=msg.substringAfter("：",msg)
                            Column(Modifier.fillMaxWidth().padding(vertical=5.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha=0.06f)).padding(horizontal=10.dp,vertical=7.dp)){
                                Text(name,fontSize=11.sp,fontWeight=FontWeight.Bold,color=C6)
                                Text(text,fontSize=13.sp,color=Color.White.copy(alpha=0.88f),lineHeight=18.sp)
                            }
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={showChatPanel=false}){Text("关闭",color=C4)}}
        )
    }
}
}
@Composable fun Bt(t:String,c:Color,m:Modifier,en:Boolean=true,on:()->Unit){
    Button(onClick=on,modifier=m.height(34.dp),enabled=en,colors=ButtonDefaults.buttonColors(containerColor=c,disabledContainerColor=Color.Gray),shape=RoundedCornerShape(17.dp)){Text(t,fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(en)Color.White else Color.White.copy(alpha=0.5f))}
}

@Composable fun MahjongTopStatusBar(g:GameState,rnd:Int,phaseTip:String,isUserTurn:Boolean,thinkingPlayer:String,userShanten:Int,isUserTenpai:Boolean,hasYaku:Boolean,isRiichi:Boolean,onExit:()->Unit,onRules:()->Unit){
    Column(Modifier.fillMaxWidth().shadow(6.dp,RoundedCornerShape(12.dp)).background(Color(0xFF132B24),RoundedCornerShape(12.dp)).border(1.dp,Color.White.copy(alpha=0.08f),RoundedCornerShape(12.dp)).padding(horizontal=8.dp,vertical=6.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onExit,modifier=Modifier.size(28.dp)){Icon(Icons.AutoMirrored.Filled.ArrowBack,null,tint=Color.White)}
            Column(Modifier.weight(1f)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(g.roundLabel(),fontSize=13.sp,fontWeight=FontWeight.Bold,color=C4)
                    Spacer(Modifier.width(6.dp));Text("第${rnd}巡",fontSize=10.sp,color=Color.White.copy(alpha=0.62f))
                    if(isUserTurn){Spacer(Modifier.width(6.dp));Text("你的回合",fontSize=10.sp,fontWeight=FontWeight.Bold,color=C3)}
                    if(thinkingPlayer.isNotEmpty()){Spacer(Modifier.width(6.dp));Text("${thinkingPlayer}思考中",fontSize=10.sp,color=Color.White.copy(alpha=0.56f))}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(phaseTip,fontSize=10.sp,color=Color.White.copy(alpha=0.62f),maxLines=1,modifier=Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Text("牌山 ${g.wall.size}",fontSize=12.sp,fontWeight=FontWeight.Bold,color=C6,modifier=Modifier.background(Color.White.copy(alpha=0.12f),RoundedCornerShape(6.dp)).padding(horizontal=6.dp,vertical=2.dp))
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("规则",fontSize=10.sp,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha=0.14f)).clickable{onRules()}.padding(horizontal=6.dp,vertical=2.dp))
                Spacer(Modifier.width(6.dp))
                Text("基础麻将",fontSize=10.sp,color=C6,fontWeight=FontWeight.Bold,modifier=Modifier.background(Color.White.copy(alpha=0.12f),RoundedCornerShape(6.dp)).padding(horizontal=6.dp,vertical=2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
            val status = if(isUserTenpai)"听牌" else "整理牌型"
            Text(status,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=if(isUserTenpai)Color(0xFFFF7043)else Color.White.copy(alpha=0.78f))
        }
    }
}

@Composable fun BasicMahjongRuleDialog(onDismiss:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        containerColor=Color(0xFF101C18),
        title={Text("基础麻将规则",fontWeight=FontWeight.SemiBold,color=C4)},
        text={
            Column(Modifier.verticalScroll(rememberScrollState())){
                Text("怎么胡牌",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text("凑成 4 组牌 + 1 对将 就能胡。组牌可以是顺子（如 3万4万5万）或刻子（如 3张中）。",fontSize=13.sp,color=Color.White.copy(alpha=0.82f),lineHeight=18.sp)
                Spacer(Modifier.height(8.dp))
                Text("常见牌型",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text("平胡、自摸、七对、对对胡、清一色、混一色。不使用复杂额外规则，能看懂牌型就能玩。",fontSize=13.sp,color=Color.White.copy(alpha=0.82f),lineHeight=18.sp)
                Spacer(Modifier.height(8.dp))
                Text("别人出牌时",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text("你能胡就点“胡”；也可以点“过”等自己摸牌。能碰、吃、杠时也会出现按钮。",fontSize=13.sp,color=Color.White.copy(alpha=0.82f),lineHeight=18.sp)
                Spacer(Modifier.height(8.dp))
                Text("AI规则",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text("AI会摸牌、出牌、胡牌，也会做最简单的碰/杠判断；整体按轻松娱乐规则处理。",fontSize=13.sp,color=Color.White.copy(alpha=0.82f),lineHeight=18.sp)
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("知道了",color=C4)}}
    )
}

@Composable fun MahjongSeatHeader(p:PlayerState,avatarUri:String?,currentSeat:Seat,modifier:Modifier=Modifier){
    Row(modifier.shadow(4.dp,RoundedCornerShape(12.dp)).background(Color(0xFF10251F).copy(alpha=0.88f),RoundedCornerShape(12.dp)).border(1.dp,if(p.seat==currentSeat)C6.copy(alpha=0.55f)else Color.White.copy(alpha=0.07f),RoundedCornerShape(12.dp)).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        OppMini(p.name,34.dp,p.isTenpai||Engine.isTenpaiState(p.hand),p.seat==currentSeat,avatarUri)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(70.dp)){Text(p.name,fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1);Text("筹码${p.points}",fontSize=9.sp,color=Color.White.copy(alpha=0.58f))}
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())){repeat(p.hand.size.coerceIn(0,14)){BackTile(15.dp,21.dp)}}
        if(p.melds.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){p.melds.forEach{m->MeldMini(m)}}
    }
}

@Composable fun MahjongTableArea(game:GameState,humanName:String,avatarMap:Map<String,String>,currentSeat:Seat,modifier:Modifier=Modifier){
    val opponents = game.players.filter { !it.isHuman }
    val top = opponents.getOrNull(0)
    val left = opponents.getOrNull(1)
    val right = opponents.getOrNull(2)
    Box(modifier.shadow(10.dp,RoundedCornerShape(20.dp)).background(Color(0xFF0F3A2B),RoundedCornerShape(20.dp)).border(1.dp,Color.White.copy(alpha=0.10f),RoundedCornerShape(20.dp)).padding(8.dp)){
        Column(Modifier.fillMaxSize()){
            OpponentSeatPanel(top,avatarMap[top?.opId],currentSeat,verticalHand=false,Modifier.fillMaxWidth().height(48.dp))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(82.dp),verticalAlignment=Alignment.CenterVertically){
                OpponentSeatPanel(left,avatarMap[left?.opId],currentSeat,verticalHand=true,Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(6.dp))
                CenterCompass(game,Modifier.width(108.dp).fillMaxHeight())
                Spacer(Modifier.width(6.dp))
                OpponentSeatPanel(right,avatarMap[right?.opId],currentSeat,verticalHand=true,Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF09281F).copy(alpha=0.92f)).border(1.dp,Color.White.copy(alpha=0.07f),RoundedCornerShape(16.dp)).padding(6.dp)){
                Column(Modifier.fillMaxSize()){
                    Row(Modifier.weight(1f).fillMaxWidth()){
                        DiscardRiverLarge(top,top?.name.orEmpty(),Modifier.weight(1f).fillMaxHeight(),columns=6)
                        Spacer(Modifier.width(6.dp))
                        DiscardRiverLarge(left,left?.name.orEmpty(),Modifier.weight(1f).fillMaxHeight(),columns=6)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.weight(1f).fillMaxWidth()){
                        DiscardRiverLarge(right,right?.name.orEmpty(),Modifier.weight(1f).fillMaxHeight(),columns=6)
                        Spacer(Modifier.width(6.dp))
                        DiscardRiverLarge(game.humanPlayer(),humanName,Modifier.weight(1f).fillMaxHeight(),columns=6,highlight=true)
                    }
                }
            }
        }
    }
}

@Composable fun OpponentSeatPanel(p:PlayerState?,avatarUri:String?,currentSeat:Seat,verticalHand:Boolean,modifier:Modifier=Modifier){
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha=0.20f)).border(1.dp,if(p?.seat==currentSeat)C6.copy(alpha=0.5f)else Color.White.copy(alpha=0.06f),RoundedCornerShape(14.dp)).padding(horizontal=6.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        if(p==null){Text("空位",fontSize=10.sp,color=Color.White.copy(alpha=0.35f));return@Row}
        OppMini(p.name,32.dp,p.isTenpai||Engine.isTenpaiState(p.hand),p.seat==currentSeat,avatarUri)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)){
            Text(p.name,fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1)
            Text("筹码${p.points} · ${p.hand.size}张",fontSize=8.sp,color=Color.White.copy(alpha=0.55f),maxLines=1)
            if(p.melds.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()).padding(top=2.dp)){p.melds.take(3).forEach{m->MeldMini(m)}}
        }
        if(p.isRiichi)Text("立",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF81C784),modifier=Modifier.padding(horizontal=2.dp))
        if(verticalHand){
            Column(Modifier.width(30.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){repeat(p.hand.size.coerceIn(0,14)){BackTile(20.dp,12.dp)}}
        }else{
            Row(Modifier.horizontalScroll(rememberScrollState())){repeat(p.hand.size.coerceIn(0,14)){BackTile(14.dp,20.dp)}}
        }
    }
}

@Composable fun CompactSeat(p:PlayerState?,avatarUri:String?,currentSeat:Seat,modifier:Modifier=Modifier){
    Row(modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha=0.20f)).border(1.dp,if(p?.seat==currentSeat)C6.copy(alpha=0.5f)else Color.White.copy(alpha=0.06f),RoundedCornerShape(14.dp)).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically){
        if(p==null){Text("空位",fontSize=10.sp,color=Color.White.copy(alpha=0.35f));return@Row}
        OppMini(p.name,32.dp,p.isTenpai||Engine.isTenpaiState(p.hand),p.seat==currentSeat,avatarUri)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)){Text(p.name,fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1);Text("筹码${p.points} · 手牌${p.hand.size}",fontSize=8.sp,color=Color.White.copy(alpha=0.55f),maxLines=1)}
        if(p.isRiichi)Text("立",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF81C784))
    }
}

@Composable fun SideSeatPanel(p:PlayerState?,avatarUri:String?,currentSeat:Seat,alignLeft:Boolean,modifier:Modifier=Modifier){
    Column(modifier.background(Color.Black.copy(alpha=0.12f),RoundedCornerShape(12.dp)).padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
        if(p==null)return@Column
        OppMini(p.name,34.dp,p.isTenpai||Engine.isTenpaiState(p.hand),p.seat==currentSeat,avatarUri)
        Text(p.name.take(3),fontSize=9.sp,color=Color.White,maxLines=1,modifier=Modifier.padding(top=3.dp))
        Text("${p.points}",fontSize=8.sp,color=Color.White.copy(alpha=0.55f))
        Spacer(Modifier.height(4.dp))
        Column(horizontalAlignment=Alignment.CenterHorizontally){repeat(p.hand.size.coerceAtMost(12)){BackTile(17.dp,22.dp)}}
        if(p.melds.isNotEmpty())Column(Modifier.verticalScroll(rememberScrollState())){p.melds.take(2).forEach{m->MeldMini(m,vertical=true)}}
    }
}

@Composable fun CenterCompass(game:GameState,modifier:Modifier=Modifier){
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF09231B).copy(alpha=0.92f)).border(1.dp,C6.copy(alpha=0.28f),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(game.roundLabel(),fontSize=10.sp,fontWeight=FontWeight.Bold,color=C4,maxLines=1)
            Text("牌山 ${game.wall.size}",fontSize=11.sp,color=Color.White.copy(alpha=0.78f))
            if(game.honba>0)Text("连庄 ${game.honba}",fontSize=9.sp,color=C3)
        }
    }
}

@Composable fun DiscardRiver(p:PlayerState?,label:String,modifier:Modifier=Modifier,compact:Boolean=false){
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha=0.14f)).padding(4.dp)){
        Text(label.ifBlank{"牌河"},fontSize=8.sp,color=C4.copy(alpha=0.82f),maxLines=1)
        if(p==null||p.discards.isEmpty()){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("-",fontSize=11.sp,color=Color.White.copy(alpha=0.22f))}
        }else{
            val chunkSize=if(compact)5 else 7
            Column(Modifier.verticalScroll(rememberScrollState())){p.discards.chunked(chunkSize).forEachIndexed{ri,row->Row{row.forEachIndexed{ci,t->val isRiichi=p.isRiichi&&ri==p.discards.chunked(chunkSize).size-1&&ci==row.size-1;if(isRiichi)Box(Modifier.graphicsLayer(rotationZ=90f)){PTile(t,w=22.dp,h=30.dp,fs=10.sp)}else PTile(t,w=if(compact)21.dp else 24.dp,h=if(compact)29.dp else 33.dp,fs=if(compact)9.sp else 10.sp)}}}}
        }
    }
}

@Composable fun DiscardRiverLarge(p:PlayerState?,label:String,modifier:Modifier=Modifier,columns:Int=7,highlight:Boolean=false){
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if(highlight)Color(0xFF164331).copy(alpha=0.72f)else Color.Black.copy(alpha=0.16f)).border(1.dp,if(highlight)C6.copy(alpha=0.20f)else Color.White.copy(alpha=0.05f),RoundedCornerShape(12.dp)).padding(5.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(label.ifBlank{"牌河"},fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(highlight)C6 else C4.copy(alpha=0.86f),maxLines=1)
            Spacer(Modifier.weight(1f))
            Text("${p?.discards?.size ?: 0}",fontSize=8.sp,color=Color.White.copy(alpha=0.42f))
        }
        Spacer(Modifier.height(2.dp))
        if(p==null||p.discards.isEmpty()){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("未出牌",fontSize=10.sp,color=Color.White.copy(alpha=0.20f))}
        }else{
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
                val rows = p.discards.chunked(columns)
                rows.forEachIndexed{ri,row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
                        row.forEachIndexed{ci,t->
                            val isRiichi=p.isRiichi&&ri==rows.size-1&&ci==row.size-1
                            val isLatest=ri==rows.size-1&&ci==row.size-1
                            if(isRiichi)Box(Modifier.graphicsLayer(rotationZ=90f).padding(horizontal=1.dp)){PTile(t,w=22.dp,h=31.dp,fs=9.sp)}
                            else Box(Modifier.border(if(isLatest)1.5.dp else 0.dp,if(isLatest)C6 else Color.Transparent,RoundedCornerShape(5.dp)).padding(if(isLatest)1.dp else 0.dp)){PTile(t,w=23.dp,h=32.dp,fs=10.sp)}
                        }
                    }
                }
            }
        }
    }
}

@Composable fun MeldMini(m:Meld,vertical:Boolean=false){
    if(vertical){Column(Modifier.padding(1.dp).background(Color.White.copy(alpha=0.10f),RoundedCornerShape(4.dp)).padding(1.dp)){m.tiles.forEach{PTile(it,w=16.dp,h=22.dp,fs=7.sp)}}}
    else Row(Modifier.padding(1.dp).background(Color.White.copy(alpha=0.10f),RoundedCornerShape(4.dp)).padding(1.dp)){m.tiles.forEach{PTile(it,w=16.dp,h=22.dp,fs=7.sp)}}
}

@Composable fun HTile52(t:Tile,s:Boolean,m:Modifier,isNew:Boolean=false,on:()->Unit){
    val e by animateFloatAsState(if(s)1f else 0f);val bgColor=Color(0xFFFFFBEC);val ec=Color(0xFFC9B68F);val bc=when{s->Color(0xFFFFD54F);isNew->Color(0xFF66BB6A);else->ec};val bw=if(s||isNew)2.dp else 1.dp;val tc=TileColors.tileColor(t);val txt=TileColors.tileText(t)
    Box(modifier=m.offset(y=(-8*e).dp).width(46.dp).height(62.dp).clickable(onClick=on)){
        Box(Modifier.fillMaxSize().padding(top=3.dp,start=2.dp).shadow(if(s)10.dp else 4.dp,RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(Color(0x55000000)))
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(bgColor).border(bw,bc,RoundedCornerShape(7.dp)),contentAlignment=Alignment.TopStart){
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(alpha=0.36f)))
            if(t.suit!=Suit.WIND&&t.suit!=Suit.DRAGON)Text("${t.number}",fontSize=10.sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.5f),modifier=Modifier.padding(start=4.dp,top=3.dp))
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(txt,fontSize=15.sp,fontWeight=FontWeight.Bold,color=tc,modifier=Modifier.offset(y=(-2).dp))}
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).border(1.5.dp,Color.White.copy(alpha=0.22f),RoundedCornerShape(7.dp)))
    }
}
@Composable fun PTile(t:Tile,dora:Boolean=false,w:Dp=28.dp,h:Dp=38.dp,fs:androidx.compose.ui.unit.TextUnit=13.sp){val tc=TileColors.tileColor(t);val txt=TileColors.tileText(t)
    Box(Modifier.padding(0.6.dp).width(w).height(h).shadow(1.dp,RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFFBEC)).border(0.7.dp,if(dora)Color(0xFFFFC107) else Color(0xFFC9B68F),RoundedCornerShape(4.dp)),contentAlignment=Alignment.Center){Box(Modifier.fillMaxWidth().height((h.value*0.12f).dp).align(Alignment.TopCenter).background(Color.White.copy(alpha=0.28f)));if(t.suit!=Suit.WIND&&t.suit!=Suit.DRAGON)Text("${t.number}",fontSize=(fs.value*0.6f).sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart).padding(start=1.dp,top=1.dp));Text(txt,fontSize=fs,fontWeight=FontWeight.Bold,color=tc)}
}
@Composable fun BackTile(w:Dp=16.dp,h:Dp=22.dp,faceUp:Boolean=false,tile:Tile?=null){
    Box(Modifier.padding(0.5.dp).width(w).height(h).clip(RoundedCornerShape(3.dp)).background(if(faceUp)Color(0xFFFFF8E1)else CARD_BACK).border(0.5.dp,if(faceUp)Color(0xFFD4C5A9)else CARD_EDGE,RoundedCornerShape(3.dp)),contentAlignment=Alignment.Center){
        if(faceUp&&tile!=null){val tc=TileColors.tileColor(tile);val txt=TileColors.tileText(tile);if(tile.suit!=Suit.WIND&&tile.suit!=Suit.DRAGON)Text("${tile.number}",fontSize=(w.value*0.3f).sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart).padding(start=1.dp,top=1.dp));Text(txt,fontSize=(w.value*0.45f).sp,fontWeight=FontWeight.Bold,color=tc)}
        else if(!faceUp){Box(Modifier.fillMaxSize().padding(2.dp)){Text("◆",fontSize=(w.value*0.5f).sp,color=Color(0xFF8BAA7B).copy(alpha=0.6f),modifier=Modifier.align(Alignment.Center));Text("◆",fontSize=(w.value*0.25f).sp,color=Color(0xFF6B8A5B).copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart));Text("◆",fontSize=(w.value*0.25f).sp,color=Color(0xFF6B8A5B).copy(alpha=0.4f),modifier=Modifier.align(Alignment.BottomEnd))}}
    }
}
@Composable fun OppMini(name:String,sz:Dp,tenpai:Boolean=false,isActive:Boolean=false,avatarUri:String?=null){
    val border=when{tenpai->Color(0xFFF44336);isActive->Color(0xFFFFD700);else->C3.copy(alpha=0.6f)};val bw=when{tenpai||isActive->2.5.dp;else->1.dp}
    Box(Modifier.size(sz).clip(CircleShape).background(Primary).border(bw,border,CircleShape),contentAlignment=Alignment.Center){
        if(!avatarUri.isNullOrBlank()) OperatorAvatarImage(avatarUri=avatarUri,name=name,modifier=Modifier.size(sz))
        else Text(name.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=if(sz>36.dp)14.sp else 11.sp)
        if(tenpai)Text("●",fontSize=7.sp,color=Color(0xFFF44336),modifier=Modifier.align(Alignment.TopEnd).offset(x=3.dp,y=(-3.dp)))
    }
}
