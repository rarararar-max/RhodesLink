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
import com.rhodes.privatechat.game.mahjong.*
import com.rhodes.privatechat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val C1 = Color(0xFF1B3A2D); private val C2 = Color(0xFF5D4037); private val C3 = Color(0xFFFF8F00)
private val C4 = Color(0xFFFDD835); private val C5 = Color(0xFF2E4A3A); private val C6 = Color(0xFFFFD700)
private val CARD_BACK = Color(0xFF2E5A3A); private val CARD_EDGE = Color(0xFF4A7A5A)

data class MeldUI(val r: Boolean, val k: Boolean, val p: Boolean, val c: Boolean, val co: List<List<Tile>>?, val dt: Tile, val ds: Seat, val actingSeat: Seat = Seat.EAST)
data class ChatMsg(val sender: String, val text: String, val isAssistant: Boolean = false, val isSystem: Boolean = false)

@Composable
fun GameScreen(game: GameState, onBack: () -> Unit, onSettlement: (SettlementResult) -> Unit, assistantName: String = "", assistantAvatarUri: String = "", onSave: ((GameState) -> Unit)? = null) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var g by remember { mutableStateOf(game) }; var sel by remember { mutableStateOf<Int?>(null) }
    var isU by remember { mutableStateOf(false) }; var start by remember { mutableStateOf(false) }; var rnd by remember { mutableIntStateOf(0) }
    var mld by remember { mutableStateOf<MeldUI?>(null) }; var showResult by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var effectText by remember { mutableStateOf("") }; var effectColor by remember { mutableStateOf(Color(0xFFFF5722)) }; var effectPlayer by remember { mutableStateOf("") }
    var thinkingPlayer by remember { mutableStateOf("") }; var tLoopJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var helpBubble by remember { mutableStateOf("") }
    val u = g.humanPlayer() ?: return; val dl = g.dealer(); val oi = g.players.filter { !it.isHuman }
    val asstName = assistantName.ifBlank { oi.firstOrNull()?.name ?: "" }
    val userShanten = Engine.shanten(u.hand); val isUserTenpai = Engine.isTenpaiState(u.hand)
    val effectAlpha by animateFloatAsState(if(effectText.isNotEmpty())1f else 0f); val curSeat = g.currentPlayer().seat

    fun showEffect(pn:String,at:String,c:Color=Color(0xFFFF5722)){effectText=at;effectColor=c;effectPlayer=pn}
    fun cnv(event:String,p:PlayerState,tile:String=""){}
    fun sp(n:String,a:String){}
    fun asTip(){}
    fun requestHelp(){
        if(u.hand.isEmpty())return
        val asst = g.players.first{!it.isHuman}
        helpBubble = AiChat.help(asst, u.hand, userShanten)
        scope.launch { delay(4000); helpBubble = "" }
    }
    fun ce(){if(g.wall.isEmpty()){
        val tenpais=g.players.filter{Engine.isTenpaiState(it.hand)};val notens=g.players.filter{!Engine.isTenpaiState(it.hand)}
        if(tenpais.isNotEmpty()&&notens.isNotEmpty()){val pp=1000;val pt=pp*notens.size/tenpais.size;notens.forEach{it.points-=pp};tenpais.forEach{it.points+=pt}}
        showResult=false;if(!settled){settled=true;onSettlement(Engine.settle(g))}
    }}
    fun win(p:PlayerState){g.winnerSeat=p.seat;showResult=true;g=g.copy();showEffect(p.name,if(g.lastDiscard!=null)"点炮！"else"自摸！",Color(0xFFFFD700))}
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
    fun executeMeld(p:PlayerState,type:MeldType,tile:Tile,fromSeat:Seat,chiOpts:List<Tile>?=null){
        when(type){
            MeldType.PON->{p.hand.filter{it==tile}.take(2).forEach{p.hand.remove(it)};p.melds.add(Meld(MeldType.PON,listOf(tile,tile,tile),fromSeat))}
            MeldType.KAN->{p.hand.filter{it==tile}.take(3).forEach{p.hand.remove(it)};p.melds.add(Meld(MeldType.KAN,listOf(tile,tile,tile,tile),fromSeat));g.currentTurn=g.players.indexOf(p);if(g.wall.isNotEmpty()){g.doraIndicators.add(g.wall.removeLast());g.uraDoraIndicators.add(g.wall.removeLast());Engine.draw(g)};g.ippatsuPlayerIdx=-1}
            MeldType.CHI->{chiOpts?.forEach{t->val idx=p.hand.indexOf(t);if(idx>=0)p.hand.removeAt(idx)};p.melds.add(Meld(MeldType.CHI,listOf(tile)+(chiOpts?:emptyList()),fromSeat))}
            else->return
        }
        showEffect(p.name,when(type){MeldType.PON->"碰！";MeldType.KAN->"杠！";MeldType.CHI->"吃！";else->"鸣牌"},C3);g.currentTurn=g.players.indexOf(p);g.ippatsuPlayerIdx=-1
    }
    fun processDiscard(tile:Tile,from:PlayerState):Boolean{
        g.lastDiscard=tile;g.lastDiscardSeat=from.seat
        val ao=g.players.filter{it.seat!=from.seat}.sortedBy{(it.seat.ordinal-from.seat.ordinal+4)%4}
        for(p in ao){if(Engine.isFuriten(p))continue
            if(Engine.canWin(p.hand.toMutableList().apply{add(tile);sortBy{it.ordinalForSort()}},p.melds,p.seat,g.roundWind,p.melds.isEmpty(),false,p.isRiichi,g.ippatsuPlayerIdx>=0&&g.players.indexOf(p)==g.ippatsuPlayerIdx)){
                if(p.isHuman){checkAllMelds(tile,from.seat);return false}
                p.hand.add(tile);p.hand.sortBy{it.ordinalForSort()};showEffect(p.name,"放铳！",Color(0xFFC62828));win(p);return true}
        }
        for(p in ao.filter{!it.isHuman&&!it.isRiichi}){if(Engine.canKan(p.hand,tile)&&aiDecideMeld(p,tile,MeldType.KAN)){executeMeld(p,MeldType.KAN,tile,from.seat);g=g.copy();return false}}
        for(p in ao.filter{!it.isHuman&&!it.isRiichi}){if(Engine.canPon(p.hand,tile)&&aiDecideMeld(p,tile,MeldType.PON)){executeMeld(p,MeldType.PON,tile,from.seat);g=g.copy();return false}}
        for(p in ao.filter{!it.isHuman&&!it.isRiichi}){if((p.seat.ordinal-from.seat.ordinal+4)%4==1){val co=Engine.canChi(p.hand,tile);if(co!=null&&aiDecideMeld(p,tile,MeldType.CHI)){executeMeld(p,MeldType.CHI,tile,from.seat,co.first());g=g.copy();return false}}}
        checkAllMelds(tile,from.seat);if(mld!=null)return false;return true
    }
    fun gameCopy(g:GameState)=GameSerializer.deserialize(GameSerializer.serialize(g))
    suspend fun aiT(p:PlayerState){
        if(g.wall.isNotEmpty()&&p.hand.size<14)Engine.draw(g);g.ippatsuPlayerIdx=-1
        if(checkTsumo(p)){thinkingPlayer="";showEffect(p.name,"自摸！",Color(0xFFFFD700));win(p);g=gameCopy(g);return}
        delay(400L);val tile=AiDiscard.decideDiscard(p,g);p.hand.remove(tile);p.discards.add(tile);thinkingPlayer=""
        showEffect(p.name,"打出 ${Tile.tileName(tile)}",Color(0xFF90A4AE));g=gameCopy(g)
        if(!processDiscard(tile,p)){if(mld==null&&!showResult)g.currentTurn=g.players.indexOf(p);g=gameCopy(g);return}
        if(showResult)return;g.currentTurn=(g.players.indexOf(p)+1)%4;g=gameCopy(g)
    }
    suspend fun tLoop(){
        while(!showResult&&mld==null){
            val c=g.currentPlayer()
            if(c.isHuman){isU=true;thinkingPlayer="";g.ippatsuPlayerIdx=-1;if(g.wall.isNotEmpty()){Engine.draw(g);rnd++;g=gameCopy(g);if(checkTsumo(c)){win(c);break};asTip()}else{ce();break};break}
            else{thinkingPlayer=c.name;delay(800L+Random.nextLong(1200));aiT(c);rnd++}
        }
    }
    fun launchTLoop(){tLoopJob?.cancel();tLoopJob=scope.launch{tLoop()}}
    fun disc(idx:Int){val t=u.hand[idx];u.hand.removeAt(idx);u.discards.add(t);sel=null;isU=false;g.drawnIdx=-1;if(!processDiscard(t,u))g.currentTurn=g.players.indexOf(u);g=g.copy();if(!showResult&&mld==null){g.currentTurn=(g.currentTurn+1)%4;g=g.copy();launchTLoop()}}
    fun dm(t:MeldType){val m=mld?:return;executeMeld(u,t,m.dt,m.ds,m.co?.firstOrNull());mld=null;isU=true;g=g.copy()}
    fun dp(){mld=null;if(!showResult){g.currentTurn=(g.currentTurn+1)%4;g=g.copy();launchTLoop()}}
    fun doAnkan(tile:Tile){repeat(4){u.hand.removeAt(u.hand.indexOfFirst{it==tile})};u.melds.add(Meld(MeldType.ANKAN,listOf(tile,tile,tile,tile),Seat.EAST));showEffect(u.name,"暗杠！",C3);g.currentTurn=g.players.indexOf(u);if(g.wall.isNotEmpty()){g.doraIndicators.add(g.wall.removeLast());g.uraDoraIndicators.add(g.wall.removeLast());Engine.draw(g)};g=g.copy();sel=null;isU=true;g.drawnIdx=u.hand.size-1}

    LaunchedEffect(Unit){Engine.deal(g);g=g.copy();start=true}
    LaunchedEffect(start){
        if(!start)return@LaunchedEffect
        if(!dl.isHuman){delay(500L+Random.nextLong(800));if(!showResult)aiT(dl);if(!showResult){g.currentTurn=(g.dealerIdx+1)%4;g=g.copy();tLoop()}}else isU=true
    }
    LaunchedEffect(effectText){if(effectText.isNotEmpty()){delay(2000);effectText=""}}
    LaunchedEffect(Unit){while(true){delay(10000);onSave?.invoke(g)}}

    val topOpp=oi.firstOrNull();val lOp=oi.getOrNull(1);val rOp=oi.getOrNull(2)
    Box(Modifier.fillMaxSize()){
    Column(Modifier.fillMaxSize().background(C1).systemBarsPadding().padding(horizontal=4.dp)){
        // 顶栏
        Row(Modifier.fillMaxWidth().background(C2,RoundedCornerShape(4.dp)).padding(horizontal=10.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,modifier=Modifier.size(24.dp)){Icon(Icons.AutoMirrored.Filled.ArrowBack,null,tint=Color.White)}
            Text("${if(g.roundWind==Seat.EAST)"东"else"南"}·${rnd}巡",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C4)
            if(isU)Text("·你的回合",fontSize=10.sp,color=C3,fontWeight=FontWeight.SemiBold)
            if(thinkingPlayer.isNotEmpty())Text("·${thinkingPlayer}思考中…",fontSize=10.sp,color=Color.White.copy(alpha=0.6f),fontWeight=FontWeight.Normal)
            Spacer(Modifier.weight(1f))
            Text("宝",fontSize=8.sp,color=C6,fontWeight=FontWeight.Bold,modifier=Modifier.background(Color.White.copy(alpha=0.2f),RoundedCornerShape(3.dp)).padding(horizontal=3.dp,vertical=1.dp))
            g.doraIndicators.forEach{BackTile(22.dp,32.dp,faceUp=true,tile=it)};Spacer(Modifier.width(4.dp))
            Text(if(isUserTenpai)"听牌！" else "向听${userShanten}",fontSize=11.sp,color=if(isUserTenpai)Color(0xFFFF5722)else Color.White)
            if(u.isRiichi)Text("立直",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF4CAF50),modifier=Modifier.background(Color.White.copy(alpha=0.15f),RoundedCornerShape(3.dp)).padding(horizontal=4.dp,vertical=1.dp))
            Text("🏔️${g.wall.size}",fontSize=12.sp,color=Color.White.copy(alpha=0.7f))
        }
        // 对家
        Row(Modifier.fillMaxWidth().padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
            topOpp?.let{op->OppMini(op.name,40.dp,op.isTenpai||Engine.isTenpai(op.hand),op.seat==curSeat);Box(Modifier.horizontalScroll(rememberScrollState())){Row{(0 until op.hand.size.coerceAtMost(14)).forEach{BackTile()}}}
                if(op.melds.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){op.melds.forEach{m->Row(Modifier.background(Color.White.copy(alpha=0.1f),RoundedCornerShape(2.dp)).padding(1.dp)){m.tiles.forEach{t->PTile(t,w=14.dp,h=20.dp,fs=6.sp)}}}}
                Text(op.name,fontSize=8.sp,color=Color.White,modifier=Modifier.padding(start=4.dp))
            };Spacer(Modifier.weight(1f));Text("🏔️${g.wall.size}",fontSize=14.sp,fontWeight=FontWeight.Bold,color=C6,modifier=Modifier.padding(end=4.dp))
        }
        // 中间三列
        Row(Modifier.weight(1f).fillMaxWidth()){
            Column(Modifier.width(40.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(4.dp));lOp?.let{op->OppMini(op.name,32.dp,op.isTenpai||Engine.isTenpai(op.hand),op.seat==curSeat);(0 until op.hand.size.coerceAtMost(14)).forEach{BackTile(18.dp,24.dp)}}}
            Column(Modifier.weight(1f).fillMaxWidth().fillMaxHeight().padding(horizontal=2.dp).background(Color.Black.copy(alpha=0.2f),RoundedCornerShape(4.dp)).padding(2.dp)){
                if(g.players.any{it.discards.isNotEmpty()}){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){g.players.forEach{p->if(p.discards.isNotEmpty()){Column{Text(if(p.isHuman)"你" else p.name,fontSize=8.sp,color=C4,modifier=Modifier.padding(horizontal=2.dp,vertical=1.dp));p.discards.chunked(7).forEachIndexed{ri,row->Row(Modifier.fillMaxWidth()){row.forEachIndexed{ci,t->if(p.isRiichi&&ri==p.discards.chunked(7).size-1&&ci==row.size-1)Box(Modifier.graphicsLayer(rotationZ=90f)){PTile(t)}else PTile(t)}}}}}}}}
            }
            Column(Modifier.width(40.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(4.dp));rOp?.let{op->OppMini(op.name,32.dp,op.isTenpai||Engine.isTenpai(op.hand),op.seat==curSeat);(0 until op.hand.size.coerceAtMost(14)).forEach{BackTile(18.dp,24.dp)}}}
        }
        // 助手求助按钮
        Row(Modifier.fillMaxWidth().padding(vertical=1.dp),verticalAlignment=Alignment.CenterVertically){
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF5C6BC0)).clickable{requestHelp()},contentAlignment=Alignment.Center){
                if(assistantAvatarUri.isNotBlank()){AsyncImage(model=assistantAvatarUri,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
                else{Text(asstName.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp)}
            }
            Text(asstName.take(6),fontSize=9.sp,color=Color.White.copy(alpha=0.6f),modifier=Modifier.padding(start=4.dp).clickable{requestHelp()})
        }
        // 手牌区
        val hc=u.hand.size;val r1=(hc+1)/2
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.35f)).padding(vertical=4.dp)){
            if(u.melds.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.melds.forEach{m->Row(Modifier.background(Color.White.copy(alpha=0.15f),RoundedCornerShape(3.dp)).padding(2.dp)){m.tiles.forEach{t->PTile(t,w=32.dp,h=44.dp,fs=15.sp)}}}}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.hand.take(r1).forEachIndexed{i,t->HTile52(t,sel==i,Modifier.padding(1.5.dp),g.drawnIdx>=0&&g.drawnIdx==i){if(isU&&mld==null)if(sel==i)disc(i)else sel=i}}}
            if(hc>r1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center){u.hand.drop(r1).forEachIndexed{i,t->val idx=r1+i;HTile52(t,sel==idx,Modifier.padding(1.5.dp),g.drawnIdx>=0&&g.drawnIdx==idx){if(isU&&mld==null)if(sel==idx)disc(idx)else sel=idx}}}
        }
        // 按钮栏
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.4f)).padding(horizontal=4.dp,vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically){
            val ms=mld
            if(ms!=null){
                if(ms.r)Bt("和",Color(0xFFC62828),Modifier.weight(1f)){val d=g.lastDiscard;if(d!=null){u.hand.add(d);g=g.copy();win(u)}}
                if(ms.k)Bt("杠",C3,Modifier.weight(1f)){dm(MeldType.KAN)};if(ms.p)Bt("碰",C3,Modifier.weight(1f)){dm(MeldType.PON)};if(ms.c)Bt("吃",C3,Modifier.weight(1f)){dm(MeldType.CHI)}
                Bt("过",Color(0xFF37474F),Modifier.weight(1f)){dp()}
            }else{
                Bt("出牌",C3,Modifier.weight(1f),isU&&sel!=null){if(isU)sel?.let{disc(it)}?:Toast.makeText(ctx,"请选牌",Toast.LENGTH_SHORT).show()}
                if(isU&&mld==null&&isUserTenpai&&!u.isRiichi&&u.melds.isEmpty())Bt("立直",Color(0xFF4CAF50),Modifier.weight(1f)){u.isRiichi=true;u.points-=1000;g.riichiSticks++;g.ippatsuPlayerIdx=g.players.indexOf(u);g=g.copy();cnv("riichi",u);val s=sel;if(s!=null)disc(s)}
                val ak=if(isU&&mld==null)Engine.canAnkan(u.hand)else emptyList()
                if(ak.isNotEmpty())ak.forEach{t->Bt("暗杠${Tile.tileName(t)}",Color(0xFF7B1FA2),Modifier.weight(1f)){doAnkan(t)}}
                Bt("退",Color(0xFF37474F),Modifier.weight(1f)){onSave?.invoke(g);onBack()}
            }
            Text("💰${((g.humanPlayer()?.points?:25000)*100)/1000}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=C6)
        }
    }
    // 助手建议气泡
    if(helpBubble.isNotEmpty()){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart){
            Box(Modifier.padding(start=8.dp,top=8.dp).background(Color(0xFF5C6BC0).copy(alpha=0.9f),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=7.dp)){
                Text(helpBubble,fontSize=12.sp,color=Color.White,fontWeight=FontWeight.Medium,maxLines=3)
            }
        }
    }
    // 特效展示（头像+动作）
    if(effectAlpha>0.01f && effectText.isNotEmpty())Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f*effectAlpha)),contentAlignment=Alignment.Center){
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.shadow(10.dp,RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha=0.7f),RoundedCornerShape(16.dp)).padding(horizontal=24.dp,vertical=16.dp)){
            Box(Modifier.size(44.dp).clip(CircleShape).background(Primary),contentAlignment=Alignment.Center){Text(effectPlayer.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp)}
            Spacer(Modifier.width(12.dp))
            Column{Text(effectPlayer,fontSize=14.sp,color=Color.White.copy(alpha=0.8f));Text(effectText,fontSize=28.sp,fontWeight=FontWeight.Bold,color=effectColor.copy(alpha=effectAlpha))}
        }
    }
    if(showResult&&g.winnerSeat!=null){val w=g.players.find{it.seat==g.winnerSeat}!!;val isRon=g.lastDiscard!=null&&g.lastDiscardSeat!=null;val lName=if(isRon)g.players.find{it.seat==g.lastDiscardSeat}?.name else null
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)),contentAlignment=Alignment.Center){Box(Modifier.widthIn(max=320.dp).background(C1,RoundedCornerShape(12.dp)).padding(16.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${w.name} ${if(isRon)"点炮和牌！"else"自摸和牌！"}",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFFD700))
        if(lName!=null)Text("放铳者：$lName",fontSize=13.sp,color=Color.White.copy(alpha=0.7f));Spacer(Modifier.height(8.dp))
        g.players.forEach{p->val label=if(p.isHuman)"你" else p.name;Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(vertical=1.dp)){Text("$label：",fontSize=10.sp,color=C4,modifier=Modifier.width(28.dp));Row(Modifier.horizontalScroll(rememberScrollState())){p.hand.sortedBy{it.ordinalForSort()}.forEach{t->PTile(t)}}};if(p.melds.isNotEmpty()){Row(modifier=Modifier.padding(start=28.dp)){p.melds.forEach{m->Box(Modifier.padding(1.dp).background(Color.White.copy(alpha=0.1f),RoundedCornerShape(2.dp)).padding(2.dp)){Row{m.tiles.forEach{t->PTile(t)}}}}}}}
        Spacer(Modifier.height(4.dp));Text("牌墙剩余：${g.wall.size}张",fontSize=11.sp,color=Color.White.copy(alpha=0.6f));Spacer(Modifier.height(10.dp))
        Bt("确认",C3,Modifier.fillMaxWidth()){confirmResult()}}}}
    }
}
}
@Composable fun Bt(t:String,c:Color,m:Modifier,en:Boolean=true,on:()->Unit){
    Button(onClick=on,modifier=m.height(34.dp),enabled=en,colors=ButtonDefaults.buttonColors(containerColor=c,disabledContainerColor=Color.Gray),shape=RoundedCornerShape(17.dp)){Text(t,fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(en)Color.White else Color.White.copy(alpha=0.5f))}
}
@Composable fun HTile52(t:Tile,s:Boolean,m:Modifier,isNew:Boolean=false,on:()->Unit){
    val e by animateFloatAsState(if(s)1f else 0f);val bgColor=Color(0xFFFFF8E1);val ec=Color(0xFFD4C5A9);val bc=when{s->Color(0xFFFFC107);isNew->Color(0xFF4CAF50);else->ec};val bw=if(s||isNew)2.dp else 1.dp;val tc=TileColors.tileColor(t);val txt=TileColors.tileText(t)
    Box(modifier=m.offset(y=(-8*e).dp).width(52.dp).height(70.dp).clickable(onClick=on)){
        Box(Modifier.fillMaxSize().padding(top=2.dp,start=2.dp).shadow(if(s)8.dp else 3.dp,RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color(0x33000000)))
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(bgColor).border(bw,bc,RoundedCornerShape(6.dp)),contentAlignment=Alignment.TopStart){
            if(t.suit!=Suit.WIND&&t.suit!=Suit.DRAGON)Text("${t.number}",fontSize=10.sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.5f),modifier=Modifier.padding(start=4.dp,top=3.dp))
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(txt,fontSize=16.sp,fontWeight=FontWeight.Bold,color=tc,modifier=Modifier.offset(y=(-2).dp))}
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).border(1.5.dp,Color.White.copy(alpha=0.25f),RoundedCornerShape(6.dp)))
    }
}
@Composable fun PTile(t:Tile,dora:Boolean=false,w:Dp=28.dp,h:Dp=38.dp,fs:androidx.compose.ui.unit.TextUnit=13.sp){val tc=TileColors.tileColor(t);val txt=TileColors.tileText(t)
    Box(Modifier.padding(0.5.dp).width(w).height(h).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFF8E1)).border(0.5.dp,if(dora)Color(0xFFFFC107) else Color(0xFFD4C5A9),RoundedCornerShape(4.dp)),contentAlignment=Alignment.Center){if(t.suit!=Suit.WIND&&t.suit!=Suit.DRAGON)Text("${t.number}",fontSize=(fs.value*0.6f).sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart).padding(start=1.dp,top=1.dp));Text(txt,fontSize=fs,fontWeight=FontWeight.Bold,color=tc)}
}
@Composable fun BackTile(w:Dp=16.dp,h:Dp=22.dp,faceUp:Boolean=false,tile:Tile?=null){
    Box(Modifier.padding(0.5.dp).width(w).height(h).clip(RoundedCornerShape(3.dp)).background(if(faceUp)Color(0xFFFFF8E1)else CARD_BACK).border(0.5.dp,if(faceUp)Color(0xFFD4C5A9)else CARD_EDGE,RoundedCornerShape(3.dp)),contentAlignment=Alignment.Center){
        if(faceUp&&tile!=null){val tc=TileColors.tileColor(tile);val txt=TileColors.tileText(tile);if(tile.suit!=Suit.WIND&&tile.suit!=Suit.DRAGON)Text("${tile.number}",fontSize=(w.value*0.3f).sp,fontWeight=FontWeight.Bold,color=tc.copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart).padding(start=1.dp,top=1.dp));Text(txt,fontSize=(w.value*0.45f).sp,fontWeight=FontWeight.Bold,color=tc)}
        else if(!faceUp){Box(Modifier.fillMaxSize().padding(2.dp)){Text("◆",fontSize=(w.value*0.5f).sp,color=Color(0xFF8BAA7B).copy(alpha=0.6f),modifier=Modifier.align(Alignment.Center));Text("◆",fontSize=(w.value*0.25f).sp,color=Color(0xFF6B8A5B).copy(alpha=0.4f),modifier=Modifier.align(Alignment.TopStart));Text("◆",fontSize=(w.value*0.25f).sp,color=Color(0xFF6B8A5B).copy(alpha=0.4f),modifier=Modifier.align(Alignment.BottomEnd))}}
    }
}
@Composable fun OppMini(name:String,sz:Dp,tenpai:Boolean=false,isActive:Boolean=false){
    val border=when{tenpai->Color(0xFFF44336);isActive->Color(0xFFFFD700);else->C3.copy(alpha=0.6f)};val bw=when{tenpai||isActive->2.5.dp;else->1.dp}
    Box(Modifier.size(sz).clip(CircleShape).background(Primary).border(bw,border,CircleShape),contentAlignment=Alignment.Center){Text(name.take(1),color=Color.White,fontWeight=FontWeight.Bold,fontSize=if(sz>36.dp)14.sp else 11.sp);if(tenpai)Text("●",fontSize=7.sp,color=Color(0xFFF44336),modifier=Modifier.align(Alignment.TopEnd).offset(x=3.dp,y=(-3).dp))}
}