package com.rhodes.privatechat.shared.call

enum class CallState {
    Calling,
    Connected,
    Listening,
    UserSpeaking,
    Thinking,
    AiSpeaking,
    Ended,
    Failed,
}
