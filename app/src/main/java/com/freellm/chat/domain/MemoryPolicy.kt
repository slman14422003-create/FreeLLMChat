package com.freellm.chat.domain

data class MemoryInput(
    val cont: Boolean,
    val hasAtt: Boolean,
    val excluded: Boolean,
    val skipOnce: Boolean,
    val skipMemory: Boolean,
    val needsFresh: Boolean,
    val timeSensitive: Boolean,
    val pinned: Boolean,
    val lastIsUser: Boolean,
    val cacheable: Boolean,
    val greeting: Boolean,
    val hard: Boolean,
    val reasonOrCode: Boolean,
    val shortFollowUp: Boolean,
    val answerCache: Boolean,
    val localFirst: Boolean,
    val recall: Boolean
)

data class MemoryDecision(val storeCache: Boolean, val lookupCache: Boolean, val useLocal: Boolean, val useRecall: Boolean)

object MemoryPolicy {
    fun decide(i: MemoryInput): MemoryDecision {
        val store = i.answerCache && !i.cont && !i.hasAtt && !i.pinned && i.lastIsUser && i.cacheable &&
            !i.skipMemory && !i.needsFresh && !i.greeting && !i.hard && !i.reasonOrCode
        val lookup = store && !i.excluded && !i.skipOnce
        val local = !i.cont && !i.excluded && !i.skipOnce && !i.hasAtt && !i.skipMemory && !i.needsFresh &&
            !i.timeSensitive && i.lastIsUser && i.localFirst
        val recall = !i.cont && !i.hasAtt && !i.shortFollowUp && i.recall
        return MemoryDecision(store, lookup, local, recall)
    }
}
