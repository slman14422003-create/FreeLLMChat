package com.freellm.chat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY starred DESC, updatedAt DESC")
    fun all(): Flow<List<ConversationEntity>>

    @Query(
        "SELECT * FROM conversations WHERE title LIKE '%' || :q || '%' " +
            "OR id IN (SELECT conversationId FROM messages WHERE content LIKE '%' || :q || '%') " +
            "ORDER BY starred DESC, updatedAt DESC"
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

    @Query("UPDATE conversations SET starred = :on WHERE id = :id")
    suspend fun setStarred(id: Long, on: Boolean)

    @Query("UPDATE conversations SET instructions = :text WHERE id = :id")
    suspend fun setInstructions(id: Long, text: String?)

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

    @Query("UPDATE messages SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String?)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("UPDATE conversations SET summary = :summary, summarizedUpTo = :upTo WHERE id = :id")
    suspend fun setSummary(id: Long, summary: String?, upTo: Long)

    @Query("SELECT * FROM answer_cache WHERE cacheKey = :key")
    suspend fun cacheGet(key: String): AnswerCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cachePut(e: AnswerCacheEntity)

    @Query("DELETE FROM answer_cache WHERE createdAt < :before")
    suspend fun cachePrune(before: Long)

    @Query("DELETE FROM answer_cache WHERE question = :question")
    suspend fun cacheForget(question: String)

    @Query("DELETE FROM answer_cache")
    suspend fun cacheClear()

    @Query("SELECT * FROM search_cache WHERE cacheKey = :key")
    suspend fun searchGet(key: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun searchPut(e: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE createdAt < :before")
    suspend fun searchPrune(before: Long)

    @Query("DELETE FROM search_cache")
    suspend fun searchClear()
}
