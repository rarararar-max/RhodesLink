package com.rhodes.privatechat.shared.modelgateway

data class VisionAnalyzeRequest(
    val imageUrlOrBase64: String,
    val prompt: String,
)

data class VisionAnalyzeResponse(
    val text: String,
)

interface VisionGateway {
    suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse
}
