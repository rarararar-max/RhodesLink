package com.rhodes.privatechat.automation

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import org.koin.java.KoinJavaComponent.get

class GroupAutoChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val groupId = inputData.getString("groupId") ?: return Result.success()
        val token = inputData.getString("token") ?: return Result.success()
        val repository = get<ChatRepository>(ChatRepository::class.java)
        val settings = get<SettingsRepository>(SettingsRepository::class.java)
        // Validate and claim before constructing a request, so stale or foreground-consumed work is inert.
        val viewModel = MainViewModel(
            applicationContext as Application, repository, settings,
            get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
            get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
        )
        viewModel.groupChatViewModel.runScheduledAutoTurn(groupId, token)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
