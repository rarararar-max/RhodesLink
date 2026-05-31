package com.example.rhodesterminal.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.viewmodel.koinViewModel
import com.example.rhodesterminal.viewmodel.MainViewModel

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
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF252529)) {
                    tabs.forEachIndexed { i, tab ->
                        NavigationBarItem(
                            selected = activeTab == i,
                            onClick = { activeTab = i },
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
    com.example.rhodesterminal.ui.sessions.SessionListScreen(
        viewModel = viewModel,
        onSessionClick = { s ->
            if (s.operatorId.startsWith("group_")) {
                navigator.push(GroupChatRoute(s.operatorName.ifBlank { "群聊" }, s.id))
            } else {
                val op = viewModel.operators.value.find { it.id == s.operatorId }
                if (op != null) navigator.push(ChatOperator(op.id))
            }
        },
        onPin = { viewModel.pinSession(it) },
        onMarkRead = { viewModel.markSessionRead(it) },
        onDelete = { viewModel.deleteSession(it) }
    )
}

@Composable
private fun ContactsTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    com.example.rhodesterminal.ui.contacts.ContactsScreen(
        viewModel = viewModel,
        onOperatorClick = { op -> navigator.push(ChatOperator(op.id)) },
        onNewOperator = { navigator.push(NewOperatorScreen) },
        onNewGroup = { navigator.push(NewGroupScreen) },
        onGroupClick = { name, id -> navigator.push(GroupChatRoute(name, id)) }
    )
}

@Composable
private fun FeaturesTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    com.example.rhodesterminal.ui.features.FeaturesScreen(
        momentBadge = viewModel.getMomentBadge(),
        commentBadge = viewModel.getUnreadCommentCount(),
        onMoments = { viewModel.markMomentsSeen(); navigator.push(MomentsRoute) },
        onDiary = { navigator.push(DiaryRoute) },
        onRanking = { navigator.push(RankingRoute) },
        onImpressions = { navigator.push(ImpressionsRoute) },
        onDispatch = { navigator.push(DispatchRoute) },
        onTokenStats = { navigator.push(TokenStatsRoute) },
        onMahjong = { navigator.push(MahjongSelectRoute) }
    )
}

@Composable
private fun SettingsTabContent(navigator: Navigator) {
    val viewModel: MainViewModel = koinViewModel()
    val profile by viewModel.userProfile.collectAsState()
    com.example.rhodesterminal.ui.settings.SettingsScreen(
        userNickname = profile.nickname,
        userGender = profile.gender,
        userAvatarUri = profile.avatarUri,
        onProfile = { navigator.push(ProfileSettingsRoute) },
        onModel = { navigator.push(ModelSettingsRoute) },
        onChatParams = { navigator.push(ChatSettingsRoute) },
        onDataManage = { navigator.push(DataManagementRoute) },
        onPermissions = { navigator.push(PermissionsRoute) },
        onCredits = { navigator.push(CreditsRoute) }
    )
}

// ──────────────────────────────────────────────
// Entry point
// ──────────────────────────────────────────────

@Composable
fun AppNavigation() {
    Navigator(MainScreen()) { navigator ->
        BackHandler(enabled = navigator.lastItem !is MainScreen) { navigator.pop() }
        CurrentScreen()
    }
}
