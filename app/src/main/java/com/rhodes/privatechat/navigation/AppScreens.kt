package com.rhodes.privatechat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.rhodes.privatechat.game.mahjong.GameSerializer
import com.rhodes.privatechat.game.mahjong.GameState
import com.rhodes.privatechat.game.mahjong.GameStateCreateParams
import com.rhodes.privatechat.game.mahjong.Engine
import com.rhodes.privatechat.game.mahjong.MahjongHistoryEntry
import com.rhodes.privatechat.game.mahjong.MatchMode
import com.rhodes.privatechat.game.mahjong.SettlementResult
import com.rhodes.privatechat.ui.gameroom.PokerMode
import com.rhodes.privatechat.ui.gameroom.PokerOpponent
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val json = Json { ignoreUnknownKeys = true }

// ──────────────────────────────────────────────
// Game Room
// ──────────────────────────────────────────────

data object GameRoomRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.gameroom.GameRoomScreen(
            onBack = { navigator.pop() },
            onMahjong = { navigator.push(MahjongSelectRoute) },
            onLandlord = { navigator.push(PokerSelectRoute(PokerMode.LANDLORD)) },
            onRunFast = { navigator.push(PokerSelectRoute(PokerMode.RUN_FAST)) }
        )
    }
}

data class PokerSelectRoute(val mode: PokerMode) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        com.rhodes.privatechat.ui.gameroom.PokerSelectScreen(
            mode = mode,
            operators = operators,
            onBack = { navigator.pop() },
            onStart = { opponents -> navigator.push(PokerGameRoute(mode, opponents)) }
        )
    }
}

data class PokerGameRoute(val mode: PokerMode, val opponents: List<PokerOpponent>) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val settings: SettingsRepository = koinInject()
        val viewModel: MainViewModel = koinViewModel()
        val profile = viewModel.getUserProfile()
        com.rhodes.privatechat.ui.gameroom.SimplePokerGameScreen(
            mode = mode,
            opponents = opponents,
            userName = profile.nickname,
            userAvatarUri = profile.avatarUri,
            balance = settings.lmb,
            onBack = { navigator.pop() },
            onSettle = { gain -> settings.addLmb(gain) },
            onGenerateTalk = { speaker, gameName, event, tableInfo, recentTalk, fallback, callback ->
                viewModel.generatePokerTalk(speaker, gameName, event, tableInfo, recentTalk, fallback, callback)
            }
        )
    }
}

// ──────────────────────────────────────────────
// Chat
// ──────────────────────────────────────────────

data class ChatOperator(val operatorId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val operator = remember(operators, operatorId) { operators.find { it.id == operatorId } }
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(operatorId) {
            if (operator != null) {
                try {
                    viewModel.chatViewModel.selectOperatorSync(operator)
                } catch (_: Exception) {
                }
                ready = true
            }
        }
        DisposableEffect(Unit) {
            onDispose { viewModel.chatViewModel.clearSelection() }
        }
        if (ready && operator != null) {
            com.rhodes.privatechat.ui.chat.ChatScreen(
                viewModel = viewModel,
                operator = operator,
                onBack = { navigator.pop() },
                onEditOperator = { navigator.push(EditOperator(operator.id)) },
                onViewStatus = { navigator.push(OperatorDetailRoute(operator.id)) },
                onViewHistory = { navigator.push(ChatHistoryRoute(operator.id)) },
                onVoiceCall = { navigator.push(VoiceCallRoute(operator.id)) }
            )
        }
    }
}

data class VoiceCallRoute(val operatorId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val operator = remember(operators, operatorId) { operators.find { it.id == operatorId } }
        if (operator != null) {
            com.rhodes.privatechat.ui.call.VoiceCallScreen(viewModel = viewModel, operator = operator, onBack = { navigator.pop() })
        }
    }
}

data object SleepRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val kaltsit = remember(operators) { operators.find { it.id == "kaltsit" || it.name == "凯尔希" } }
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(kaltsit) {
            if (operators.isNotEmpty() && kaltsit == null) {
                android.widget.Toast.makeText(context, "未找到凯尔希角色", android.widget.Toast.LENGTH_SHORT).show()
                navigator.pop()
            } else if (kaltsit != null && kaltsit.voiceName.isBlank()) {
                android.widget.Toast.makeText(context, "请先在凯尔希角色编辑页面填写音色ID", android.widget.Toast.LENGTH_SHORT).show()
                navigator.pop()
            }
        }
        if (kaltsit != null && kaltsit.voiceName.isNotBlank()) {
            com.rhodes.privatechat.ui.sleep.SleepModeScreen(viewModel = viewModel, operator = kaltsit, onBack = { navigator.pop() })
        }
    }
}

data class ChatHistoryRoute(val operatorId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val operator = remember(operators, operatorId) { operators.find { it.id == operatorId } }
        if (operator != null) {
            com.rhodes.privatechat.ui.chat.ChatHistoryScreen(
                viewModel = viewModel,
                operatorName = operator.name,
                onBack = { navigator.pop() }
            )
        }
    }
}

data class GroupChatRoute(val name: String, val groupId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.group.GroupDetailScreen(
            viewModel = viewModel,
            groupName = name,
            groupId = groupId,
            onBack = { navigator.pop() },
            onEditGroup = { id -> navigator.push(EditGroup(id)) },
            onOperatorClick = { operatorName ->
                viewModel.clearCurrentGroup()
                val op = viewModel.findOperatorByName(operatorName)
                if (op != null) navigator.push(ChatOperator(op.id))
            }
        )
    }
}

// ──────────────────────────────────────────────
// Contacts
// ──────────────────────────────────────────────

data object NewOperatorScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.editor.OperatorEditScreen(viewModel = koinViewModel(), operator = null, onBack = { navigator.pop() })
    }
}

data class EditOperator(val opId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val op = remember(operators) { operators.find { it.id == opId } }
        LaunchedEffect(op) { if (op == null && operators.isNotEmpty()) navigator.pop() }
        if (op != null) {
            com.rhodes.privatechat.ui.editor.OperatorEditScreen(viewModel = viewModel, operator = op, onBack = { navigator.pop() })
        }
    }
}

data object NewGroupScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.group.GroupEditScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data class EditGroup(val groupId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.group.GroupEditScreen(viewModel = koinViewModel(), groupId = groupId, onBack = { navigator.pop() })
    }
}

data class OperatorDetailRoute(val opId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val op = remember(operators) { operators.find { it.id == opId } }
        LaunchedEffect(op) { if (op == null && operators.isNotEmpty()) navigator.pop() }
        if (op != null) {
            com.rhodes.privatechat.ui.detail.OperatorDetailScreen(
                viewModel = viewModel,
                operator = op,
                onBack = { navigator.pop() },
                onOperatorClick = { clickedOp -> navigator.push(ChatOperator(clickedOp.id)) }
            )
        }
    }
}

// ──────────────────────────────────────────────
// Features
// ──────────────────────────────────────────────

data object MomentsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.moments.MomentsScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            onOperatorClick = { name ->
                val op = viewModel.findOperatorByName(name)
                if (op != null) navigator.push(ChatOperator(op.id))
            },
            onUnreadMessages = { navigator.push(UnreadMessagesRoute) },
            onMomentClick = { momentId, commentId, name -> navigator.push(MomentDetailRoute(momentId, commentId, name)) }
        )
    }
}

data object UnreadMessagesRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.moments.UnreadMessagesScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            onMomentClick = { momentId, commentId, name -> navigator.push(MomentDetailRoute(momentId, commentId, name)) }
        )
    }
}

data class MomentDetailRoute(val momentId: Long, val replyToCommentId: Long = 0, val replyToName: String = "") : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.moments.MomentDetailScreen(
            viewModel = viewModel,
            momentId = momentId,
            replyToCommentId = replyToCommentId,
            replyToName = replyToName,
            onBack = { navigator.pop() },
            onOperatorClick = { name ->
                val op = viewModel.findOperatorByName(name)
                if (op != null) navigator.push(ChatOperator(op.id))
            }
        )
    }
}

data object DiaryRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.diary.DiaryScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object RankingRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.ranking.RankingScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            onOperatorClick = { name ->
                val op = viewModel.findOperatorByName(name)
                if (op != null) navigator.push(ChatOperator(op.id))
            }
        )
    }
}

data object ImpressionsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.rhodes.privatechat.ui.impressions.ImpressionsScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            onOperatorClick = { name ->
                val op = viewModel.findOperatorByName(name)
                if (op != null) navigator.push(ChatOperator(op.id))
            }
        )
    }
}

data object DispatchRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.dispatch.DispatchScreen(
            viewModel = koinViewModel(),
            onBack = { navigator.pop() },
            onStart = { id -> navigator.push(DispatchProgressRoute(id)) },
            onHistory = { navigator.push(DispatchHistoryRoute) }
        )
    }
}

data class DispatchProgressRoute(val id: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.dispatch.DispatchProgressScreen(viewModel = koinViewModel(), dispatchId = id, onBack = { navigator.pop() })
    }
}

data object DispatchHistoryRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.dispatch.DispatchHistoryScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object TokenStatsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.stats.TokenStatsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

// ──────────────────────────────────────────────
// Mahjong
// ──────────────────────────────────────────────

data object MahjongSelectRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val settings: SettingsRepository = koinInject()
        val context = androidx.compose.ui.platform.LocalContext.current
        val userLmb = settings.lmb
        val operators by viewModel.operators.collectAsState()
        var savedGame by remember { mutableStateOf<GameState?>(null) }
        LaunchedEffect(Unit) {
            viewModel.loadMahjongSave { save ->
                if (save != null) {
                    try {
                        val game = GameSerializer.deserialize(save.saveJson)
                        if (game.isValidMahjongGame()) savedGame = game else viewModel.deleteMahjongSave()
                    } catch (_: Exception) {
                        viewModel.deleteMahjongSave()
                    }
                }
            }
        }
        com.rhodes.privatechat.ui.mahjong.SelectScreen(
            operators = operators,
            userLmb = userLmb,
            userAvatarUri = viewModel.getUserProfile().avatarUri,
            userName = viewModel.getUserProfile().nickname,
            onBack = { navigator.pop() },
            savedGame = savedGame,
            onResume = { game ->
                val replayData = game.toReplayDataOrNull()
                if (replayData == null) {
                    viewModel.deleteMahjongSave()
                    savedGame = null
                    Toast.makeText(context, "旧麻将存档不兼容，已清理，请重新开局", Toast.LENGTH_SHORT).show()
                } else {
                    navigator.replace(MahjongGameRoute(game, replayData))
                }
            },
            onStart = { game ->
                val replayData = game.toReplayDataOrNull()
                if (replayData == null) {
                    Toast.makeText(context, "麻将牌局数据异常，请重新选择对手", Toast.LENGTH_SHORT).show()
                } else {
                    navigator.replace(MahjongGameRoute(game, replayData))
                }
            }
        )
    }
}

private fun GameState.isValidMahjongGame(): Boolean = toReplayDataOrNull() != null

private fun GameState.toReplayDataOrNull(): GameStateCreateParams? {
    val opponents = players.filter { !it.isHuman }
    val human = players.firstOrNull { it.isHuman } ?: return null
    if (players.size != 4 || opponents.size != 3) return null
    if (opponents.any { it.opId.isBlank() || it.name.isBlank() }) return null
    if (human.opId.isBlank() || human.name.isBlank()) return null
    if (assistantOpId.isBlank()) return null
    normalizeTurn()
    return GameStateCreateParams(
        opIds = opponents.map { it.opId },
        opNames = opponents.map { it.name },
        styles = opponents.map { Triple(it.attack, it.defense, it.meldPref) },
        userId = human.opId,
        userName = human.name,
        assistantId = assistantOpId,
        matchMode = matchMode
    )
}

data class MahjongGameRoute(
    val gameState: GameState,
    val replayData: GameStateCreateParams
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val asstOp = viewModel.operators.value.find { it.id == gameState.assistantOpId }
        val profile = viewModel.getUserProfile()
        val avatarMap = viewModel.operators.value.associate { it.id to it.avatarUri } + ("user" to profile.avatarUri)
        com.rhodes.privatechat.ui.mahjong.GameScreen(
            game = gameState,
            onBack = { navigator.pop() },
            onSettlement = { result ->
                navigator.replace(MahjongSettlementRoute(result, replayData, gameState))
            },
            assistantName = asstOp?.name ?: "",
            assistantAvatarUri = asstOp?.avatarUri ?: "",
            avatarMap = avatarMap,
            onSave = { g -> viewModel.saveMahjongGame(GameSerializer.serialize(g), g.matchMode.name) },
            onGenerateTalk = { player, event, tile, roundLabel, wallLeft, shanten, fallback, participants, recentChat, callback ->
                viewModel.generateMahjongTableTalk(player, event, tile, roundLabel, wallLeft, shanten, fallback, participants, recentChat, callback)
            }
        )
    }
}

data class MahjongSettlementRoute(
    val result: SettlementResult,
    val replayData: GameStateCreateParams,
    val gameState: GameState
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val settings: SettingsRepository = koinInject()
        val isFinalSettlement = result.matchMode == MatchMode.QUICK || result.currentRound >= result.maxRounds
        val profile = viewModel.getUserProfile()

        LaunchedEffect(Unit) {
            if (!isFinalSettlement) return@LaunchedEffect
            // 1. 统一更新龙门币：用户
            settings.addLmb(result.userNetGain)
            // 2. 统一更新龙门币：对手（用 Operator.lmb 字段）
            result.rankings.forEach { r ->
                val op = viewModel.operators.value.find { it.name == r.name }
                if (op != null && op.id != "user") {
                    viewModel.repository.operators.updateOperator(op.copy(lmb = (op.lmb + r.netGain).coerceAtLeast(0)))
                }
            }

            // 3. 调用 ViewModel 统一处理锚点/世界事件/关系/动态
            viewModel.settleMahjongGame(
                participantNames = result.participants,
                winnerName = result.winnerName,
                loserName = result.loserName,
                winType = result.winType,
                summary = result.summary,
                userNetGain = result.userNetGain,
                assistantName = result.assistantName
            )

            // 4. 保存对局历史
            try {
                val jsonStr = settings.mahjongHistoryJson.ifBlank { "[]" }
                val list = json.decodeFromString<List<MahjongHistoryEntry>>(jsonStr).toMutableList()
                val hu = gameState.humanPlayer()
                list.add(0, MahjongHistoryEntry(
                    time = System.currentTimeMillis(),
                    opponents = gameState.players.filter { !it.isHuman }.map { it.name },
                    userRank = result.rankings.find { it.name == hu?.name }?.rank ?: 4,
                    userNetGain = result.userNetGain,
                    userPoints = result.rankings.find { it.name == hu?.name }?.finalPoints ?: 25000,
                    winType = result.winType,
                    winnerName = result.winnerName
                ))
                settings.mahjongHistoryJson = json.encodeToString(list.take(100))
            } catch (_: Exception) {}

            // 5. 清理存档
            viewModel.deleteMahjongSave()
        }

        val avatarMap = viewModel.operators.value.associate { it.id to it.avatarUri } + ("user" to profile.avatarUri)

        com.rhodes.privatechat.ui.mahjong.SettlementScreen(
            result = result,
            onBack = { navigator.pop() },
            avatarMap = avatarMap,
            players = gameState.players,
            isFinalSettlement = isFinalSettlement,
            onGenerateLine = { player, name, isWinner, isDraw, rank, netGain, summary, fallback, callback ->
                viewModel.generateMahjongSettlementLine(player, name, isWinner, isDraw, rank, netGain, summary, fallback, callback)
            },
            onPlayAgain = {
                val newGame = GameState.create(
                    replayData.opIds, replayData.opNames, replayData.styles,
                    replayData.userId, replayData.userName, replayData.assistantId,
                    replayData.matchMode
                )
                navigator.replace(MahjongGameRoute(newGame, replayData))
            },
            onNextRound = if (result.matchMode != MatchMode.QUICK && result.currentRound < result.maxRounds) {
                {
                    val nextRound = result.currentRound + 1
                    val dealer = gameState.players.getOrNull(gameState.dealerIdx)
                    val dealerWon = result.winnerName.isNotBlank() && dealer?.name == result.winnerName
                    val dealerTenpaiDraw = result.winnerName.isBlank() && dealer?.let { Engine.isTenpaiState(it.hand) } == true
                    val nextDealerIdx = if (dealerWon || dealerTenpaiDraw) gameState.dealerIdx else (gameState.dealerIdx + 1) % gameState.players.size
                    val cumulative = result.cumulativePoints
                    val nextGame = GameState.create(
                        replayData.opIds, replayData.opNames, replayData.styles,
                        replayData.userId, replayData.userName, replayData.assistantId,
                        replayData.matchMode
                    )
                    nextGame.round = nextRound
                    nextGame.dealerIdx = nextDealerIdx
                    nextGame.currentTurn = nextDealerIdx
                    if (dealerWon || dealerTenpaiDraw || result.winnerName.isBlank()) {
                        nextGame.honba = gameState.honba + 1
                    } else {
                        nextGame.honba = 0
                    }
                    nextGame.riichiSticks = 0
                    nextGame.players.forEach { p ->
                        val prev = cumulative[p.name]
                        if (prev != null) p.points = prev
                    }
                    navigator.replace(MahjongGameRoute(nextGame, replayData))
                }
            } else null
        )
    }
}

// ──────────────────────────────────────────────
// Settings
// ──────────────────────────────────────────────

data object ProfileSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.profile.ProfileSettingsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object ModelSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.model.ModelSettingsScreen(onBack = { navigator.pop() })
    }
}

data object AppearanceSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.settings.AppearanceSettingsScreen(onBack = { navigator.pop() })
    }
}

data object ChatSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.chatsettings.ChatSettingsScreen(onBack = { navigator.pop() }, onPromptEditor = { navigator.push(PromptEditorRoute) })
    }
}

data object StorySettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.settings.StorySettingsScreen(onBack = { navigator.pop() })
    }
}

data object PromptEditorRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.prompt.PromptEditorScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object DataManagementRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.settings.DataManagementScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object PermissionsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.settings.PermissionsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object CreditsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.profile.CreditsScreen(onBack = { navigator.pop() })
    }
}

data object DebugLogRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.rhodes.privatechat.ui.settings.DebugLogScreen(onBack = { navigator.pop() })
    }
}
