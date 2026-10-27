package com.freellm.chat.data

import com.freellm.chat.data.db.AnswerCacheEntity
import com.freellm.chat.data.db.ChatDao
import com.freellm.chat.data.db.ConversationEntity
import com.freellm.chat.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {
    fun conversations(q: String): Flow<List<ConversationEntity>> =
        if (q.isBlank()) dao.all() else dao.search(q.trim())

    fun messages(id: Long): Flow<List<MessageEntity>> = dao.messages(id)

    suspend fun messagesOnce(id: Long): List<MessageEntity> = dao.messagesOnce(id)

    suspend fun conversation(id: Long): ConversationEntity? = dao.conversation(id)

    suspend fun newConversation(title: String, pinned: String?): Long {
        val now = System.currentTimeMillis()
        return dao.insertConversation(
            ConversationEntity(title = title, createdAt = now, updatedAt = now, pinnedModel = pinned)
        )
    }

    suspend fun addMessage(m: MessageEntity): Long {
        val id = dao.insertMessage(m)
        dao.touch(m.conversationId, System.currentTimeMillis())
        return id
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title)

    suspend fun setPinned(id: Long, pinned: String?) = dao.setPinned(id, pinned)

    suspend fun setStarred(id: Long, on: Boolean) = dao.setStarred(id, on)

    suspend fun setInstructions(id: Long, text: String?) = dao.setInstructions(id, text?.trim()?.ifEmpty { null })

    suspend fun deleteConversation(id: Long) = dao.deleteConversation(id)

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun deleteFrom(cid: Long, fromId: Long) {
        dao.deleteFrom(cid, fromId)
        val c = dao.conversation(cid)
        if (c != null && c.summarizedUpTo >= fromId) dao.setSummary(cid, null, 0)
    }

    suspend fun setSummary(id: Long, summary: String?, upTo: Long) = dao.setSummary(id, summary, upTo)

    suspend fun cacheGet(key: String, ttlMs: Long, now: Long): AnswerCacheEntity? {
        val e = dao.cacheGet(key) ?: return null
        return if (com.freellm.chat.domain.CachePolicy.fresh(e.createdAt, now, ttlMs)) e else null
    }

    suspend fun cachePut(e: AnswerCacheEntity, ttlMs: Long) {
        dao.cachePut(e)
        dao.cachePrune(e.createdAt - ttlMs)
    }

    suspend fun cacheForget(question: String) = dao.cacheForget(question)

    suspend fun cacheClear() = dao.cacheClear()

    suspend fun updateAssistant(
        id: Long,
        content: String,
        modelName: String?,
        providerName: String?,
        modelKey: String?,
        notes: String?
    ) = dao.updateAssistant(id, content, modelName, providerName, modelKey, notes)

    suspend fun deleteAll() {
        dao.deleteAllConversations()
        dao.cacheClear()
        dao.searchClear()
    }

    suspend fun searchClear() = dao.searchClear()

    suspend fun setNotes(id: Long, notes: String?) = dao.setNotes(id, notes)

    suspend fun exportMarkdown(id: Long): String {
        val c = dao.conversation(id)
        val ms = dao.messagesOnce(id)
        val sb = StringBuilder("# ").append(c?.title.orEmpty()).append("\n\n")
        ms.forEach { m ->
            val who = if (m.role == "user") "**You**" else "**" + (m.modelName ?: "Assistant") + "**"
            sb.append(who).append("\n\n").append(m.content).append("\n\n")
            val src = com.freellm.chat.domain.SourcesPrompt.decode(m.sources)
            if (src.isNotEmpty()) {
                src.forEachIndexed { i, s -> sb.append("[").append(i + 1).append("] ").append(s.title).append(" - ").append(s.url).append("\n") }
                sb.append("\n")
            }
            sb.append("---\n\n")
        }
        return sb.toString()
    }
}

class RoomSearchCache(private val dao: ChatDao) : com.freellm.chat.domain.SearchCacheStore {
    override suspend fun get(key: String, ttlMs: Long, now: Long): String? {
        val e = dao.searchGet(key) ?: return null
        return if (com.freellm.chat.domain.CachePolicy.fresh(e.createdAt, now, ttlMs)) e.payload else null
    }

    override suspend fun put(key: String, payload: String, now: Long, ttlMs: Long) {
        dao.searchPut(com.freellm.chat.data.db.SearchCacheEntity(key, payload, now))
        dao.searchPrune(now - ttlMs)
    }
}
