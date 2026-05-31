package com.example.rhodesterminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.rhodesterminal.game.mahjong.GameState
import com.example.rhodesterminal.game.mahjong.GameStateCreateParams
import com.example.rhodesterminal.game.mahjong.MahjongHistoryEntry
import com.example.rhodesterminal.game.mahjong.SettlementResult
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.viewmodel.MainViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val json = Json { ignoreUnknownKeys = true }

// ──────────────────────────────────────────────
// Chat
// ──────────────────────────────────────────────

data class ChatOperator(val opId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val operators by viewModel.operators.collectAsState()
        val op = remember(operators) { operators.find { it.id == opId } }
        // 同步设置 selectedOperator，确保 ChatScreen 渲染时已有值
        if (op != null) {
            DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }
            viewModel.selectOperator(op)
            com.example.rhodesterminal.ui.chat.ChatScreen(
                viewModel = viewModel,
                onBack = { navigator.pop() },
                onEditOperator = { navigator.push(EditOperator(opId)) },
                onViewStatus = { navigator.push(OperatorDetailRoute(opId)) }
            )
        } else if (operators.isNotEmpty()) {
            // operators 已加载但找不到该干员，返回上一级
            LaunchedEffect(Unit) { navigator.pop() }
        }
    }
}

data class GroupChatRoute(val name: String, val groupId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.example.rhodesterminal.ui.group.GroupDetailScreen(
            viewModel = viewModel,
            groupName = name,
            groupId = groupId,
            onBack = { viewModel.clearCurrentGroup(); navigator.pop() },
            onEditGroup = { id -> navigator.push(EditGroup(id)) },
            onOperatorClick = { operatorName ->
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
        com.example.rhodesterminal.ui.editor.OperatorEditScreen(viewModel = koinViewModel(), operator = null, onBack = { navigator.pop() })
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
            com.example.rhodesterminal.ui.editor.OperatorEditScreen(viewModel = viewModel, operator = op, onBack = { navigator.pop() })
        }
    }
}

data object NewGroupScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.group.GroupEditScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data class EditGroup(val groupId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.group.GroupEditScreen(viewModel = koinViewModel(), groupId = groupId, onBack = { navigator.pop() })
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
            com.example.rhodesterminal.ui.detail.OperatorDetailScreen(
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
        com.example.rhodesterminal.ui.moments.MomentsScreen(
            viewModel = viewModel,
            onBack = { navigator.pop() },
            onOperatorClick = { name ->
                val op = viewModel.findOperatorByName(name)
                if (op != null) navigator.push(ChatOperator(op.id))
            },
            onUnreadMessages = { navigator.push(UnreadMessagesRoute) }
        )
    }
}

data object UnreadMessagesRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.example.rhodesterminal.ui.moments.UnreadMessagesScreen(
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
        com.example.rhodesterminal.ui.moments.MomentDetailScreen(
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
        com.example.rhodesterminal.ui.diary.DiaryScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object RankingRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        com.example.rhodesterminal.ui.ranking.RankingScreen(
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
        com.example.rhodesterminal.ui.impressions.ImpressionsScreen(
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
        com.example.rhodesterminal.ui.dispatch.DispatchScreen(
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
        com.example.rhodesterminal.ui.dispatch.DispatchProgressScreen(viewModel = koinViewModel(), dispatchId = id, onBack = { navigator.pop() })
    }
}

data object DispatchHistoryRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.dispatch.DispatchHistoryScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object TokenStatsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.stats.TokenStatsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
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
        val userLmb = settings.lmb
        com.example.rhodesterminal.ui.mahjong.SelectScreen(
            operators = viewModel.operators.value,
            userLmb = userLmb,
            userAvatarUri = viewModel.getUserProfile().avatarUri,
            userName = viewModel.getUserProfile().nickname,
            onBack = { navigator.pop() },
            onStart = { game ->
                settings.lmb = settings.lmb - 100
                val replayData = GameStateCreateParams(
                    opIds = game.players.filter { !it.isHuman }.map { it.opId },
                    opNames = game.players.filter { !it.isHuman }.map { it.name },
                    styles = game.players.filter { !it.isHuman }.map { Triple(it.attack, it.defense, it.meldPref) },
                    userId = game.players.find { it.isHuman }!!.opId,
                    userName = game.players.find { it.isHuman }!!.name,
                    assistantId = game.assistantOpId
                )
                navigator.replaceAll(MahjongGameRoute(game, replayData))
            }
        )
    }
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
        com.example.rhodesterminal.ui.mahjong.GameScreen(
            game = gameState,
            onBack = { navigator.popUntilRoot() },
            onSettlement = { result ->
                val names = gameState.players.filter { !it.isHuman }.joinToString("、") { it.name }
                navigator.replace(MahjongSettlementRoute(result, names, replayData, gameState))
            },
            assistantName = asstOp?.name ?: "",
            assistantAvatarUri = asstOp?.avatarUri ?: ""
        )
    }
}

data class MahjongSettlementRoute(
    val result: SettlementResult,
    val names: String,
    val replayData: GameStateCreateParams,
    val gameState: GameState
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: MainViewModel = koinViewModel()
        val settings: SettingsRepository = koinInject()

        LaunchedEffect(Unit) {
            settings.lmb = settings.lmb + result.userNetGain
            result.rankings.forEach { r ->
                val op = viewModel.operators.value.find { it.name == r.name }
                if (op != null && op.id != "user") {
                    val cur = settings.getInt(op.id, op.lmb)
                    settings.putInt(op.id, cur + r.netGain)
                }
            }
            val gainText = if (result.userNetGain >= 0) "净赢${result.userNetGain}" else "净输${-result.userNetGain}"
            val highName = result.rankings.firstOrNull()?.name ?: ""
            val winType = if (highName != "" && result.rankings.first().netGain > 0) {
                if (kotlin.random.Random.nextBoolean()) "自摸" else "荣和"
            } else ""
            viewModel.createMahjongAnchor("在活动室打了一局麻将，${highName}${if(winType.isNotEmpty())"$winType"else"流局"}，${gainText}龙门币")

            // 保存对局历史
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
                    winType = if (result.rankings.firstOrNull()?.name == hu?.name) { if (gameState.lastDiscard != null) "荣和" else "自摸" } else "",
                    winnerName = result.rankings.firstOrNull()?.name ?: ""
                ))
                settings.mahjongHistoryJson = json.encodeToString(list.take(100))
            } catch (_: Exception) {}
        }

        com.example.rhodesterminal.ui.mahjong.SettlementScreen(
            result = result,
            onBack = { navigator.popUntilRoot() },
            onPlayAgain = {
                val newGame = GameState.create(
                    replayData.opIds, replayData.opNames, replayData.styles,
                    replayData.userId, replayData.userName, replayData.assistantId
                )
                navigator.replace(MahjongGameRoute(newGame, replayData))
            }
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
        com.example.rhodesterminal.ui.profile.ProfileSettingsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object ModelSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.model.ModelSettingsScreen(onBack = { navigator.pop() })
    }
}

data object ChatSettingsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.chatsettings.ChatSettingsScreen(onBack = { navigator.pop() }, onPromptEditor = { navigator.push(PromptEditorRoute) })
    }
}

data object PromptEditorRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.prompt.PromptEditorScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object DataManagementRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.settings.DataManagementScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object PermissionsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.settings.PermissionsScreen(viewModel = koinViewModel(), onBack = { navigator.pop() })
    }
}

data object CreditsRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        com.example.rhodesterminal.ui.profile.CreditsScreen(onBack = { navigator.pop() })
    }
}
