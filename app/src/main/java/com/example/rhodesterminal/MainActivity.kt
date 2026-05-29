package com.example.rhodesterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.example.rhodesterminal.viewmodel.MainViewModel
import com.example.rhodesterminal.ui.chat.ChatScreen
import com.example.rhodesterminal.ui.chatsettings.ChatSettingsScreen
import com.example.rhodesterminal.ui.contacts.ContactsScreen
import com.example.rhodesterminal.ui.detail.OperatorDetailScreen
import com.example.rhodesterminal.ui.diary.DiaryScreen
import com.example.rhodesterminal.ui.dispatch.DispatchHistoryScreen
import com.example.rhodesterminal.ui.dispatch.DispatchProgressScreen
import com.example.rhodesterminal.ui.dispatch.DispatchScreen
import com.example.rhodesterminal.ui.editor.OperatorEditScreen
import com.example.rhodesterminal.ui.features.FeaturesScreen
import com.example.rhodesterminal.ui.group.GroupDetailScreen
import com.example.rhodesterminal.ui.group.GroupEditScreen
import com.example.rhodesterminal.ui.impressions.ImpressionsScreen
import com.example.rhodesterminal.ui.mahjong.SelectScreen
import com.example.rhodesterminal.ui.mahjong.GameScreen
import com.example.rhodesterminal.ui.mahjong.SettlementScreen
import com.example.rhodesterminal.ui.model.ModelSettingsScreen
import com.example.rhodesterminal.ui.moments.MomentDetailScreen
import com.example.rhodesterminal.ui.moments.MomentsScreen
import com.example.rhodesterminal.ui.moments.UnreadMessagesScreen
import com.example.rhodesterminal.ui.profile.CreditsScreen
import com.example.rhodesterminal.ui.profile.ProfileSettingsScreen
import com.example.rhodesterminal.ui.prompt.PromptEditorScreen
import com.example.rhodesterminal.ui.ranking.RankingScreen
import com.example.rhodesterminal.ui.sessions.SessionListScreen
import com.example.rhodesterminal.ui.settings.DataManagementScreen
import com.example.rhodesterminal.ui.settings.PermissionsScreen
import com.example.rhodesterminal.ui.settings.SettingsScreen
import com.example.rhodesterminal.ui.stats.TokenStatsScreen
import com.example.rhodesterminal.game.mahjong.GameSerializer
import com.example.rhodesterminal.game.mahjong.GameState
import com.example.rhodesterminal.game.mahjong.SettlementResult
import com.example.rhodesterminal.game.mahjong.*
import com.example.rhodesterminal.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainApp() }
    }
}

sealed class SubScreen {
    data object None : SubScreen()
    data object Moments : SubScreen()
    data object Diary : SubScreen()
    data object NewOperator : SubScreen()
    data class EditOperator(val opId: String) : SubScreen()
    data object NewGroup : SubScreen()
    data class EditGroup(val groupId: String) : SubScreen()
    data class GroupChat(val name: String, val groupId: String) : SubScreen()
    data object ProfileSettings : SubScreen()
    data object ModelSettings : SubScreen()
    data object ChatSettings : SubScreen()
    data object PromptEditor : SubScreen()
    data object Credits : SubScreen()
    data object DataManagement : SubScreen()
    data object Permissions : SubScreen()
    data class OperatorDetail(val opId: String) : SubScreen()
    data object TokenStats : SubScreen()
    data object Ranking : SubScreen()
    data object Impressions : SubScreen()
    data object Dispatch : SubScreen()
    data class DispatchProgress(val id: String) : SubScreen()
    data object DispatchHistory : SubScreen()
    data class MomentDetail(val momentId: Long, val replyToCommentId: Long = 0, val replyToName: String = "") : SubScreen()
    data object UnreadMessages : SubScreen()
    data object MahjongSelect : SubScreen()
}

@Composable
fun MainApp(viewModel: MainViewModel = koinViewModel()) {
    val selectedOperator by viewModel.selectedOperator.collectAsState()
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var momentBadge by remember { mutableIntStateOf(viewModel.getMomentBadge()) }
    var mahjongGame by remember { mutableStateOf<GameState?>(null) }
    var mahjongShowSettlement by remember { mutableStateOf(false) }
    var mahjongSettlement by remember { mutableStateOf<SettlementResult?>(null) }
    var mahjongNames by remember { mutableStateOf("") }
    var mahjongReplayData by remember { mutableStateOf<GameStateCreateParams?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            momentBadge = viewModel.getMomentBadge()
            delay(10_000)
        }
    }

    LaunchedEffect(subScreen) {
        if (subScreen == SubScreen.None || subScreen is SubScreen.Moments) {
            momentBadge = viewModel.getMomentBadge()
        }
    }

    // Back handling: subScreen → close, chat → back to sessions, else → system back
    BackHandler(enabled = subScreen != SubScreen.None) {
        when (subScreen) {
            is SubScreen.GroupChat -> { viewModel.clearCurrentGroup(); subScreen = SubScreen.None }
            is SubScreen.MomentDetail -> { subScreen = SubScreen.UnreadMessages }
            is SubScreen.UnreadMessages -> { subScreen = SubScreen.Moments }
            is SubScreen.DispatchProgress -> { subScreen = SubScreen.Dispatch }
            is SubScreen.DispatchHistory -> { subScreen = SubScreen.Dispatch }
            is SubScreen.PromptEditor -> { subScreen = SubScreen.ChatSettings }
            is SubScreen.OperatorDetail -> subScreen = SubScreen.None
            is SubScreen.EditOperator -> subScreen = SubScreen.None
            is SubScreen.EditGroup -> subScreen = SubScreen.None
            else -> subScreen = SubScreen.None
        }
    }
    BackHandler(enabled = selectedOperator != null && subScreen == SubScreen.None) {
        viewModel.clearSelection()
    }

    // 麻将牌局优先展示
    mahjongGame?.let { game ->
        val asstOp = viewModel.operators.value.find { it.id == game.assistantOpId }
        GameScreen(game = game, onBack = { mahjongGame = null }, onSettlement = { result ->
            mahjongSettlement = result; mahjongShowSettlement = true
            mahjongNames = game.players.filter { !it.isHuman }.joinToString("、") { it.name }
            // 保存对局历史
            try {
                val prefs = viewModel.getApplication<android.app.Application>().getSharedPreferences("mahjong_history", 0)
                val json = prefs.getString("games", "[]") ?: "[]"
                val gson = com.google.gson.Gson()
                val list = gson.fromJson(json, Array<MahjongHistoryEntry>::class.java)?.toMutableList() ?: mutableListOf()
                val hu = game.humanPlayer()
                list.add(0, MahjongHistoryEntry(
                    time = System.currentTimeMillis(),
                    opponents = game.players.filter { !it.isHuman }.map { it.name },
                    userRank = result.rankings.find { it.name == hu?.name }?.rank ?: 4,
                    userNetGain = result.userNetGain,
                    userPoints = result.rankings.find { it.name == hu?.name }?.finalPoints ?: 25000,
                    winType = if (result.rankings.firstOrNull()?.name == hu?.name) { if (game.lastDiscard != null) "荣和" else "自摸" } else "",
                    winnerName = result.rankings.firstOrNull()?.name ?: ""
                ))
                prefs.edit().putString("games", gson.toJson(list.take(100))).apply()
            } catch (_: Exception) {}
            mahjongGame = null
        }, assistantName = asstOp?.name ?: "", assistantAvatarUri = asstOp?.avatarUri ?: "")
        return@MainApp
    }

    // 麻将结算
    if (mahjongShowSettlement && mahjongSettlement != null) {
        val result = mahjongSettlement!!
        LaunchedEffect(Unit) {
            // 结算龙门币
            val prefs = viewModel.getApplication<android.app.Application>().getSharedPreferences("dispatch", 0)
            val bal = prefs.getInt("lmb", 1000)
            prefs.edit().putInt("lmb", bal + result.userNetGain).apply()
            // 更新对手龙门币
            val lmbPrefs = viewModel.getApplication<android.app.Application>().getSharedPreferences("op_lmb", 0)
            result.rankings.forEach { r ->
                val op = viewModel.operators.value.find { it.name == r.name }
                if (op != null && op.id != "user") {
                    val cur = lmbPrefs.getInt(op.id, op.lmb)
                    lmbPrefs.edit().putInt(op.id, cur + r.netGain).apply()
                }
            }
            // AI角色记忆锚点
            val gainText = if (result.userNetGain >= 0) "净赢${result.userNetGain}" else "净输${-result.userNetGain}"
            val highName = result.rankings.firstOrNull()?.name ?: ""
            val winType = if (highName != "" && result.rankings.first().netGain > 0) {
                if (kotlin.random.Random.nextBoolean()) "自摸" else "荣和"
            } else ""
            viewModel.createMahjongAnchor("在活动室打了一局麻将，${highName}${if(winType.isNotEmpty())"$winType"else"流局"}，${gainText}龙门币")
        }
        SettlementScreen(result = result, onBack = {
            mahjongSettlement = null; mahjongShowSettlement = false; mahjongGame = null
        }, onPlayAgain = {
            mahjongReplayData?.let { d ->
                mahjongSettlement = null; mahjongShowSettlement = false
                mahjongGame = GameState.create(d.opIds, d.opNames, d.styles, d.userId, d.userName, d.assistantId)
            }
        })
        return@MainApp
    }

    // 子页面优先于聊天（从聊天内打开的编辑/状态页也走这里）
    if (subScreen != SubScreen.None) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
        val opForSub = selectedOperator
        when (val screen = subScreen) {
            is SubScreen.Moments -> MomentsScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None },
                onOperatorClick = { name ->
                    val op = viewModel.findOperatorByName(name)
                    if (op != null) { viewModel.selectOperator(op); subScreen = SubScreen.None }
                },
                onUnreadMessages = { subScreen = SubScreen.UnreadMessages })
            is SubScreen.Diary -> DiaryScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.NewOperator -> OperatorEditScreen(viewModel = viewModel, operator = null, onBack = { subScreen = SubScreen.None })
            is SubScreen.EditOperator -> {
                val op = viewModel.operators.value.find { it.id == screen.opId } ?: opForSub
                if (op != null) OperatorEditScreen(viewModel = viewModel, operator = op, onBack = { subScreen = SubScreen.None })
                else subScreen = SubScreen.None
            }
            is SubScreen.NewGroup -> GroupEditScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.EditGroup -> GroupEditScreen(viewModel = viewModel, groupId = screen.groupId, onBack = { subScreen = SubScreen.None })
            is SubScreen.GroupChat -> GroupDetailScreen(viewModel = viewModel, groupName = screen.name, onBack = { viewModel.clearCurrentGroup(); subScreen = SubScreen.None },
                onEditGroup = { id -> subScreen = SubScreen.EditGroup(id) }, groupId = screen.groupId,
                onOperatorClick = { name -> val op = viewModel.findOperatorByName(name); if (op != null) { viewModel.selectOperator(op); subScreen = SubScreen.None } })
            is SubScreen.ProfileSettings -> ProfileSettingsScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.ModelSettings -> ModelSettingsScreen(onBack = { subScreen = SubScreen.None })
            is SubScreen.ChatSettings -> ChatSettingsScreen(onBack = { subScreen = SubScreen.None }, onPromptEditor = { subScreen = SubScreen.PromptEditor })
            is SubScreen.PromptEditor -> PromptEditorScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.ChatSettings })
            is SubScreen.Credits -> CreditsScreen(onBack = { subScreen = SubScreen.None })
            is SubScreen.DataManagement -> DataManagementScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.Permissions -> PermissionsScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.OperatorDetail -> {
                val op = viewModel.operators.value.find { it.id == screen.opId } ?: opForSub
                if (op != null) OperatorDetailScreen(viewModel = viewModel, operator = op, onBack = { subScreen = SubScreen.None }, onOperatorClick = { viewModel.selectOperator(it) })
                else subScreen = SubScreen.None
            }
            is SubScreen.TokenStats -> TokenStatsScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.Ranking -> RankingScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.Impressions -> ImpressionsScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None })
            is SubScreen.Dispatch -> DispatchScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.None },
                onStart = { id -> subScreen = SubScreen.DispatchProgress(id) },
                onHistory = { subScreen = SubScreen.DispatchHistory })
            is SubScreen.DispatchProgress -> DispatchProgressScreen(viewModel = viewModel, dispatchId = screen.id, onBack = { subScreen = SubScreen.Dispatch })
            is SubScreen.DispatchHistory -> DispatchHistoryScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.Dispatch })
            is SubScreen.MomentDetail -> MomentDetailScreen(viewModel = viewModel, momentId = screen.momentId, replyToCommentId = screen.replyToCommentId, replyToName = screen.replyToName, onBack = { subScreen = SubScreen.UnreadMessages }, onOperatorClick = { name -> val op = viewModel.findOperatorByName(name); if (op != null) { viewModel.selectOperator(op); subScreen = SubScreen.None } })
            is SubScreen.UnreadMessages -> UnreadMessagesScreen(viewModel = viewModel, onBack = { subScreen = SubScreen.Moments }, onMomentClick = { momentId, commentId, name -> subScreen = SubScreen.MomentDetail(momentId, commentId, name) })
            is SubScreen.MahjongSelect -> {
                val userLmb = viewModel.getApplication<android.app.Application>().getSharedPreferences("dispatch", 0).getInt("lmb", 1000)
                SelectScreen(
                operators = viewModel.operators.value,
                userLmb = userLmb,
                userAvatarUri = viewModel.getUserProfile().avatarUri,
                userName = viewModel.getUserProfile().nickname,
                onBack = { subScreen = SubScreen.None },
                onStart = { game -> 
                    // 扣入场费
                    val prefs = viewModel.getApplication<android.app.Application>().getSharedPreferences("dispatch", 0)
                    val bal = prefs.getInt("lmb", 1000)
                    prefs.edit().putInt("lmb", bal - 100).apply()
                    // 保存再来一局数据
                    mahjongReplayData = GameStateCreateParams(
                        opIds = game.players.filter { !it.isHuman }.map { it.opId },
                        opNames = game.players.filter { !it.isHuman }.map { it.name },
                        styles = game.players.filter { !it.isHuman }.map { Triple(it.attack, it.defense, it.meldPref) },
                        userId = game.players.find { it.isHuman }!!.opId,
                        userName = game.players.find { it.isHuman }!!.name,
                        assistantId = game.assistantOpId
                    )
                    mahjongGame = game
                    subScreen = SubScreen.None
                }
            )
            }
            is SubScreen.None -> {}
        }
    }
    } else if (selectedOperator != null) {
        ChatScreen(viewModel = viewModel, onBack = { viewModel.clearSelection() },
            onEditOperator = { subScreen = SubScreen.EditOperator(selectedOperator!!.id) },
            onViewStatus = { subScreen = SubScreen.OperatorDetail(selectedOperator!!.id) })
    } else {
        MainTabs(viewModel = viewModel, momentBadge = momentBadge, activeTab = activeTab, onTabChange = { activeTab = it },
            onNavigate = { screen -> subScreen = screen })
    }
}

@Composable
fun MainTabs(viewModel: MainViewModel, momentBadge: Int, activeTab: Int, onTabChange: (Int) -> Unit, onNavigate: (SubScreen) -> Unit) {
    val tabs = listOf(
        BottomTab("聊天", Icons.Outlined.Chat, Icons.Filled.Chat),
        BottomTab("联系人", Icons.Outlined.People, Icons.Filled.People),
        BottomTab("功能", Icons.Outlined.Build, Icons.Filled.Build),
        BottomTab("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
    )

    Scaffold(bottomBar = {
        NavigationBar(containerColor = Color(0xFF252529)) {
            tabs.forEachIndexed { i, tab ->
                NavigationBarItem(selected = activeTab == i, onClick = { onTabChange(i) },
                    icon = { Icon(if (activeTab == i) tab.selectedIcon else tab.unselectedIcon, tab.label) },
                    label = { Text(tab.label, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }) { innerPadding ->
        val profile by viewModel.userProfile.collectAsState()
        when (activeTab) {
            0 -> SessionListScreen(viewModel = viewModel, onSessionClick = { s ->
                if (s.operatorId.startsWith("group_")) {
                    onNavigate(SubScreen.GroupChat(s.operatorName.ifBlank { "群聊" }, s.id))
                } else {
                    val op = viewModel.operators.value.find { it.id == s.operatorId }
                    if (op != null) viewModel.selectOperator(op)
                }
            }, onPin = { viewModel.pinSession(it) }, onMarkRead = { viewModel.markSessionRead(it) },
                onDelete = { viewModel.deleteSession(it) }, modifier = Modifier.padding(innerPadding))
            1 -> ContactsScreen(viewModel = viewModel, onOperatorClick = { op -> viewModel.selectOperator(op) },
                onNewOperator = { onNavigate(SubScreen.NewOperator) }, onNewGroup = { onNavigate(SubScreen.NewGroup) },
                onGroupClick = { name, id -> onNavigate(SubScreen.GroupChat(name, id)) }, modifier = Modifier.padding(innerPadding))
             2 -> FeaturesScreen(momentBadge = momentBadge, commentBadge = viewModel.getUnreadCommentCount(), onMoments = { viewModel.markMomentsSeen(); onNavigate(SubScreen.Moments) }, onDiary = { onNavigate(SubScreen.Diary) },
                onRanking = { onNavigate(SubScreen.Ranking) }, onImpressions = { onNavigate(SubScreen.Impressions) },
                onDispatch = { onNavigate(SubScreen.Dispatch) }, onTokenStats = { onNavigate(SubScreen.TokenStats) },
                onMahjong = { onNavigate(SubScreen.MahjongSelect) },
                modifier = Modifier.padding(innerPadding))
             3 -> SettingsScreen(userNickname = profile.nickname, userGender = profile.gender, userAvatarUri = profile.avatarUri, onProfile = { onNavigate(SubScreen.ProfileSettings) }, onModel = { onNavigate(SubScreen.ModelSettings) },
                onChatParams = { onNavigate(SubScreen.ChatSettings) }, onDataManage = { onNavigate(SubScreen.DataManagement) },
                onPermissions = { onNavigate(SubScreen.Permissions) }, onCredits = { onNavigate(SubScreen.Credits) }, modifier = Modifier.padding(innerPadding))
        }
    }
}

data class BottomTab(val label: String, val unselectedIcon: ImageVector, val selectedIcon: ImageVector)
