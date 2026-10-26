package com.freellm.chat.data

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

    suspend fun deleteConversation(id: Long) = dao.deleteConversation(id)

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun deleteFrom(cid: Long, fromId: Long) = dao.deleteFrom(cid, fromId)

    suspend fun updateAssistant(
        id: Long,
        content: String,
        modelName: String?,
        providerName: String?,
        modelKey: String?,
        notes: String?
    ) = dao.updateAssistant(id, content, modelName, providerName, modelKey, notes)

    suspend fun deleteAll() = dao.deleteAllConversations()

    suspend fun exportMarkdown(id: Long): String {
        val c = dao.conversation(id)
        val ms = dao.messagesOnce(id)
        val sb = StringBuilder("# ").append(c?.title.orEmpty()).append("\n\n")
        ms.forEach { m ->
            val who = if (m.role == "user") "**You**" else "**" + (m.modelName ?: "Assistant") + "**"
            sb.append(who).append("\n\n").append(m.content).append("\n\n---\n\n")
        }
        return sb.toString()
    }
}
