package com.freellm.chat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun all(): Flow<List<ConversationEntity>>

    @Query(
        "SELECT * FROM conversations WHERE title LIKE '%' || :q || '%' " +
            "OR id IN (SELECT conversationId FROM messages WHERE content LIKE '%' || :q || '%') " +
            "ORDER BY updatedAt DESC"
    )
    fun search(q: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Insert
    suspend fun insertConversation(c: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET pinnedModel = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: String?)

    @Query("UPDATE conversations SET updatedAt = :t WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id")
    fun messages(id: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id")
    suspend fun messagesOnce(id: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(m: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :cid AND id >= :fromId")
    suspend fun deleteFrom(cid: Long, fromId: Long)

    @Query(
        "UPDATE messages SET content = :content, modelName = :modelName, providerName = :providerName, " +
            "modelKey = :modelKey, notes = :notes WHERE id = :id"
    )
    suspend fun updateAssistant(
        id: Long,
        content: String,
        modelName: String?,
        providerName: String?,
        modelKey: String?,
        notes: String?
    )

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
