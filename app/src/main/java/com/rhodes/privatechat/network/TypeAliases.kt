package com.rhodes.privatechat.network

// Type aliases mapping old network types to shared model types

typealias Message = com.rhodes.privatechat.shared.model.AiMessage
typealias ChatCompletionRequest = com.rhodes.privatechat.shared.model.ChatCompletionRequest
typealias StreamChunk = com.rhodes.privatechat.shared.model.StreamChunk
typealias StreamChoice = com.rhodes.privatechat.shared.model.StreamChoice
typealias Delta = com.rhodes.privatechat.shared.model.Delta
typealias NonStreamResponse = com.rhodes.privatechat.shared.model.NonStreamResponse
typealias NonStreamChoice = com.rhodes.privatechat.shared.model.NonStreamChoice
typealias StreamError = com.rhodes.privatechat.shared.model.StreamError
typealias StreamErrorDetail = com.rhodes.privatechat.shared.model.StreamErrorDetail
typealias OfflineModeResponse = com.rhodes.privatechat.shared.model.OfflineModeResponse
typealias Segment = com.rhodes.privatechat.shared.model.Segment
typealias AnalysisResult = com.rhodes.privatechat.shared.model.AnalysisResult
typealias OnlineModeResponse = com.rhodes.privatechat.shared.model.OnlineModeResponse
typealias SummaryResponse = com.rhodes.privatechat.shared.model.SummaryResponse
typealias AnchorItem = com.rhodes.privatechat.shared.model.AnchorItem
typealias ProviderConfig = com.rhodes.privatechat.shared.model.ProviderConfig
