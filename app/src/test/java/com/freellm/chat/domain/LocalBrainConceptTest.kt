package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBrainConceptTest {
    @Test fun synonymQuestionMatchesByMeaning() {
        val b = LocalBrain()
        b.learn("ما هي طريقة حذف الملف؟", "اضغط مطولا ثم اختر حذف.")
        assertNotNull(b.best("ما هي طريقة مسح الملف", 0.5))
    }

    @Test fun crossLanguageSynonymsShareConcept() {
        assertEquals(Concepts.canonical(LocalBrain.stem("cost")), Concepts.canonical(LocalBrain.stem("price")))
        assertEquals(Concepts.canonical(LocalBrain.stem("delete")), Concepts.canonical(LocalBrain.stem("remove")))
    }

    @Test fun unrelatedWordsStayApart() {
        val b = LocalBrain()
        b.learn("ما هو سعر الاشتراك؟", "5 دولارات.")
        assertNull(b.best("كيف أطبخ الأرز", 0.4))
    }

    @Test fun unknownTokenIsUnchanged() {
        assertEquals("zebra", Concepts.canonical("zebra"))
    }
}
