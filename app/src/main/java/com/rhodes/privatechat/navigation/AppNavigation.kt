package com.rhodes.privatechat.navigation

import android.content.Intent
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Alignment
import com.rhodes.privatechat.ui.theme.NavBarBg
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.PrimaryContainer
import com.rhodes.privatechat.ui.theme.Stroke
import com.rhodes.privatechat.ui.theme.ElevatedSurface
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.isRhodesTerminal
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.SlideTransition
import org.koin.compose.viewmodel.koinViewModel
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.MainActivity

// ──────────────────────────────────────────────
// Main screen with bottom tabs
// ──────────────────────────────────────────────

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var activeTab by rememberSaveable { mutableIntStateOf(0) }

        val tabs = listOf(
            @Suppress("DEPRECATION")
            TabItem("聊天", Icons.Outlined.Chat, Icons.Filled.Chat),
            TabItem("联系人", Icons.Outlined.People, Icons.Filled.People),
            TabItem("功能", Icons.Outlined.Build, Icons.Filled.Build),
            TabItem("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
        )

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isRhodesTerminal) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(NavBarBg)
                            .border(1.dp, Stroke)
                            .padding(horizontal = 6.dp, vertical = 7.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { i, tab ->
                            val selected = activeTab == i
                            Column(
                                modifier = Modifier.weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (selected) PrimaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { activeTab = i }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, tab.label, tint = if (selected) Primary else TextSecondary, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(tab.label, fontSize = 10.sp, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium, color = if (selected) Primary else TextSecondary)
                                if (selected) Box(Modifier.padding(top = 3.dp).width(14.dp).height(2.dp).background(Primary))
                            }
                        }
                    }
                } else NavigationBar(
                    modifier = Modifier.background(Brush.verticalGradient(listOf(ElevatedSurface.copy(alpha = 0.94f), NavBarBg.copy(alpha = 0.96f)))).border(1.dp, Stroke),
                    containerColor = NavBarBg.copy(alpha = 0.96f), tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { i, tab ->
                        NavigationBarItem(
                            selected = activeTab == i,
                            onClick = { activeTab = i },
                            icon = { Icon(if (activeTab == i) tab.selectedIcon else tab.unselectedIcon, tab.label) },
                            label = { Text(tab.label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                indicatorColor = PrimaryContainer.copy(alpha = 0.78f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (activeTab) {
                    0 -> ChatTabContent(navigator)
                    1 -> ContactsTabContent(navigator)
                    2 -> FeaturesTabContent(navigator)
                    3 -> SettingsTabContent(navigator)
                }
            }
        }
    }
}

private data class TabItem(val label: String, val unselectedIcon: ImageVector, val selectedIcon: ImageVector)

// ──────────────────────────────────────────────
// Tab contents
// ──────────────────────────────────────────────

@Composable
private fun ChatTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    com.rhodes.privatechat.ui.sessions.SessionListScreen(
        viewModel = viewModel,
        onSessionClick = { s ->
            if (s.operatorId.startsWith("group_")) {
                navigator.push(GroupChatRoute(s.operatorName.ifBlank { "群聊" }, s.id))
            } else {
                val op = viewModel.operators.value.find { it.id == s.operatorId }
                if (op != null) navigator.push(ChatOperator(op.id))
                else com.rhodes.privatechat.util.DebugLogger.log("Navigation", "会话 ${s.id} 的角色 ${s.operatorId} 已不存在")
            }
        },
        onPin = { viewModel.pinSession(it) },
        onMarkRead = { viewModel.markSessionRead(it) },
        onDelete = { viewModel.hideSession(it) }
    )
}

@Composable
private fun ContactsTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    com.rhodes.privatechat.ui.contacts.ContactsScreen(
        viewModel = viewModel,
        onOperatorClick = { op ->
            DebugLogger.diagnostic("Navigation/PrivateChatRequested", "operatorId=${op.id}, operatorName=${op.name}")
            navigator.push(ChatOperator(op.id))
        },
        onNewOperator = {
            DebugLogger.diagnostic("Navigation/NewOperatorRequested", "source=contacts")
            navigator.push(NewOperatorScreen)
        },
        onNewGroup = {
            DebugLogger.diagnostic("Navigation/NewGroupRequested", "source=contacts")
            navigator.push(NewGroupScreen)
        },
        onGroupClick = { name, id -> navigator.push(GroupChatRoute(name, id)) }
    )
}

@Composable
private fun FeaturesTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    var momentBadge by remember { mutableIntStateOf(0) }
    var commentBadge by remember { mutableIntStateOf(0) }
    var diaryBadge by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            momentBadge = viewModel.getMomentBadgeSuspend()
            commentBadge = viewModel.getUnreadCommentCountSuspend()
            diaryBadge = viewModel.getUnreadDiaryCount()
            delay(10_000)
        }
    }
    com.rhodes.privatechat.ui.features.FeaturesScreen(
        momentBadge = momentBadge,
        commentBadge = commentBadge,
        diaryBadge = diaryBadge,
        onMoments = { viewModel.markMomentsSeen(); momentBadge = 0; navigator.push(MomentsRoute) },
        onDiary = { navigator.push(DiaryRoute) },
        onRanking = { navigator.push(RankingRoute) },
        onImpressions = { navigator.push(ImpressionsRoute) },
        onDispatch = { navigator.push(DispatchRoute) },
        onTokenStats = { navigator.push(TokenStatsRoute) },
        onGameRoom = { navigator.push(GameRoomRoute) },
        onSleep = { navigator.push(SleepRoute) }
    )
}

@Composable
private fun SettingsTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    val profile by viewModel.userProfile.collectAsState()
    com.rhodes.privatechat.ui.settings.SettingsScreen(
        userNickname = profile.nickname,
        userGender = profile.gender,
        userAvatarUri = profile.avatarUri,
        onProfile = { navigator.push(ProfileSettingsRoute) },
        onAppearance = { navigator.push(AppearanceSettingsRoute) },
        onModel = { navigator.push(ModelSettingsRoute) },
        onChatParams = { navigator.push(ChatSettingsRoute) },
        onMemory = { navigator.push(MemorySettingsRoute) },
        onStory = { navigator.push(StorySettingsRoute) },
        onDailyContent = { navigator.push(DailyContentSettingsRoute) },
        onDataManage = { navigator.push(DataManagementRoute) },
        onPermissions = { navigator.push(PermissionsRoute) },
        onCredits = { navigator.push(CreditsRoute) },
        onDebugLog = { navigator.push(DebugLogRoute) }
    )
}

// ──────────────────────────────────────────────
// Entry point
// ──────────────────────────────────────────────

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val activity = context as? Activity

    Navigator(MainScreen()) { navigator ->
        BackHandler(enabled = navigator.lastItem !is MainScreen) { navigator.pop() }
        val navigationRequest by MainActivity.navigationRequest.collectAsState()

        LaunchedEffect(navigationRequest?.nonce) {
            val request = navigationRequest ?: return@LaunchedEffect
            val sessionId = request.sessionId
            val isGroup = request.isGroup
            try {
                val viewModel = org.koin.core.context.GlobalContext.get().get<MainViewModel>()
                val deadline = System.currentTimeMillis() + 10_000L
                while (System.currentTimeMillis() < deadline) {
                    val session = viewModel.allSessions.value.find { it.id == sessionId }
                    if (session != null) {
                        if (isGroup) {
                            navigator.push(GroupChatRoute(session.operatorName.ifBlank { "群聊" }, session.id))
                            break
                        }
                        val op = viewModel.operators.value.find { it.id == session.operatorId }
                        if (op != null) {
                            navigator.push(ChatOperator(op.id))
                            break
                        }
                    }
                    delay(200)
                }
            } catch (_: Exception) {
            } finally {
                MainActivity.consumeNavigationRequest(request.nonce)
                activity?.intent?.removeExtra("nav_session_id")
            }
        }

        SlideTransition(navigator)
    }
}
