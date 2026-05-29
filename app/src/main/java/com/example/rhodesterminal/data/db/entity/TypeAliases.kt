package com.example.rhodesterminal.data.db.entity

// Type aliases mapping old Room entity types to shared model types
// This allows existing code to continue working while using the shared types

typealias OperatorEntity = com.example.rhodesterminal.shared.model.Operator
typealias ChatSessionEntity = com.example.rhodesterminal.shared.model.ChatSession
typealias ChatMessageEntity = com.example.rhodesterminal.shared.model.ChatMessage
typealias MemoryEntity = com.example.rhodesterminal.shared.model.Memory
typealias MemoryAnchorEntity = com.example.rhodesterminal.shared.model.MemoryAnchor
typealias RelationshipEntity = com.example.rhodesterminal.shared.model.Relationship
typealias MomentEntity = com.example.rhodesterminal.shared.model.Moment
typealias MomentCommentEntity = com.example.rhodesterminal.shared.model.MomentComment
typealias MomentLikeEntity = com.example.rhodesterminal.shared.model.MomentLike
typealias DiaryEntity = com.example.rhodesterminal.shared.model.Diary
typealias DispatchRecordEntity = com.example.rhodesterminal.shared.model.DispatchRecord
typealias MahjongSaveEntity = com.example.rhodesterminal.shared.model.MahjongSave

typealias MemoryType = com.example.rhodesterminal.shared.model.MemoryType
typealias AnchorType = com.example.rhodesterminal.shared.model.AnchorType
typealias RelationshipType = com.example.rhodesterminal.shared.model.RelationshipType
