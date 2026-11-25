package com.freellm.chat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPolicyTest {
    private fun base() = MemoryInput(
        cont = false, hasAtt = false, excluded = false, skipOnce = false, skipMemory = false,
        needsFresh = false, timeSensitive = false, pinned = false, lastIsUser = true, cacheable = true,
        greeting = false, hard = false, reasonOrCode = false, shortFollowUp = false,
        answerCache = true, localFirst = true, recall = true
    )

    @Test fun plainQuestionUsesEverything() {
        val d = MemoryPolicy.decide(base())
        assertTrue(d.storeCache)
        assertTrue(d.lookupCache)
        assertTrue(d.useLocal)
        assertTrue(d.useRecall)
    }

    @Test fun attachmentDisablesAll() {
        val d = MemoryPolicy.decide(base().copy(hasAtt = true))
        assertFalse(d.storeCache)
        assertFalse(d.lookupCache)
        assertFalse(d.useLocal)
        assertFalse(d.useRecall)
    }

    @Test fun freshQuestionSkipsStoreAndLocal() {
        val d = MemoryPolicy.decide(base().copy(needsFresh = true))
        assertFalse(d.storeCache)
        assertFalse(d.useLocal)
    }

    @Test fun hardQuestionIsNotStored() {
        assertFalse(MemoryPolicy.decide(base().copy(hard = true)).storeCache)
    }

    @Test fun excludedSkipsLookupAndLocal() {
        val d = MemoryPolicy.decide(base().copy(excluded = true))
        assertFalse(d.lookupCache)
        assertFalse(d.useLocal)
    }

    @Test fun localFirstOffDisablesLocal() {
        assertFalse(MemoryPolicy.decide(base().copy(localFirst = false)).useLocal)
    }
}
