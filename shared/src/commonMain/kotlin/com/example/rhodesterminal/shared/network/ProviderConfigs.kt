package com.example.rhodesterminal.shared.network

import com.example.rhodesterminal.shared.model.ProviderConfig

val providers = mapOf(
    "deepseek" to ProviderConfig(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/chat/completions",
        models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash", "deepseek-v4-pro")
    ),
    "minimax" to ProviderConfig(
        id = "minimax",
        name = "MiniMax",
        baseUrl = "https://api.minimax.chat/v1/chat/completions",
        models = listOf("abab6.5-chat", "abab5.5-chat")
    ),
    "byte" to ProviderConfig(
        id = "byte",
        name = "豆包(字节)",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
        models = listOf("doubao-lite-32k", "doubao-seed-1-6-flash", "doubao-seed-1-6", "doubao-seed-2-0-pro")
    ),
    "google" to ProviderConfig(
        id = "google",
        name = "Google",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
        models = listOf("gemini-1.5-pro", "gemini-1.5-flash"),
        isOpenAICompat = false
    ),
    "ali" to ProviderConfig(
        id = "ali",
        name = "阿里千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        models = listOf("qwen-flash", "qwen-plus", "qwen3-max")
    ),
    "zhipu" to ProviderConfig(
        id = "zhipu",
        name = "智谱AI",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        models = listOf("glm-4", "glm-3-turbo", "glm-4v")
    ),
    "siliconflow" to ProviderConfig(
        id = "siliconflow",
        name = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1/chat/completions",
        models = listOf("Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2.5-32B-Instruct", "deepseek-ai/DeepSeek-R1")
    ),
    "openai_compat" to ProviderConfig(
        id = "openai_compat",
        name = "OpenAI兼容",
        baseUrl = "https://api.openai.com/v1/chat/completions",
        models = listOf("gpt-4o", "gpt-4o-mini")
    ),
    "custom" to ProviderConfig(
        id = "custom",
        name = "自填",
        baseUrl = "",
        models = listOf("自填")
    )
)
