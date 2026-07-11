package com.rhodes.privatechat.shared.modelgateway

class DisabledVisionGateway : VisionGateway {
    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        error("识图模型未配置")
    }
}
