package com.freellm.chat.data

import com.freellm.chat.domain.Catalog
import com.freellm.chat.domain.ModelDef
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class Round11DataTest {
    @Test fun expiredModelsArePruned() {
        val cat = Catalog(
            models = listOf(
                ModelDef("a", "p", "a", 1, expiresAt = "2026-01-01"),
                ModelDef("b", "p", "b", 2, expiresAt = "2027-01-01"),
                ModelDef("c", "p", "c", 3)
            )
        )
        val out = CatalogGuard.prune(cat, LocalDate.parse("2026-11-15"))
        assertEquals(listOf("b", "c"), out.models.map { it.id })
    }
}
