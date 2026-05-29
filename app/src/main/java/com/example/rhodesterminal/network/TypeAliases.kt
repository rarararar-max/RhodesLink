package com.example.rhodesterminal.network

// Type aliases mapping old network types to shared model types

typealias Message = com.example.rhodesterminal.shared.model.AiMessage
typealias ChatCompletionRequest = com.example.rhodesterminal.shared.model.ChatCompletionRequest
typealias StreamChunk = com.example.rhodesterminal.shared.model.StreamChunk
typealias StreamChoice = com.example.rhodesterminal.shared.model.StreamChoice
typealias Delta = com.example.rhodesterminal.shared.model.Delta
typealias NonStreamResponse = com.example.rhodesterminal.shared.model.NonStreamResponse
typealias NonStreamChoice = com.example.rhodesterminal.shared.model.NonStreamChoice
typealias StreamError = com.example.rhodesterminal.shared.model.StreamError
typealias StreamErrorDetail = com.example.rhodesterminal.shared.model.StreamErrorDetail
typealias OfflineModeResponse = com.example.rhodesterminal.shared.model.OfflineModeResponse
typealias Segment = com.example.rhodesterminal.shared.model.Segment
typealias AnalysisResult = com.example.rhodesterminal.shared.model.AnalysisResult
typealias OnlineModeResponse = com.example.rhodesterminal.shared.model.OnlineModeResponse
typealias SummaryResponse = com.example.rhodesterminal.shared.model.SummaryResponse
typealias AnchorItem = com.example.rhodesterminal.shared.model.AnchorItem
typealias ProviderConfig = com.example.rhodesterminal.shared.model.ProviderConfig
