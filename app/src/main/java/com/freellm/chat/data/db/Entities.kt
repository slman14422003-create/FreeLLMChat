package com.freellm.chat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedModel: String? = null,
    @ColumnInfo(defaultValue = "0") val starred: Boolean = false,
    val instructions: String? = null,
    val summary: String? = null,
    @ColumnInfo(defaultValue = "0") val summarizedUpTo: Long = 0
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val modelName: String? = null,
    val providerName: String? = null,
    val modelKey: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val attachments: String? = null,
    val sources: String? = null
)

@Entity(tableName = "answer_cache")
data class AnswerCacheEntity(
    @PrimaryKey val cacheKey: String,
    val question: String,
    val answer: String,
    val modelName: String?,
    val providerName: String?,
    val createdAt: Long
)

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val createdAt: Long
)
