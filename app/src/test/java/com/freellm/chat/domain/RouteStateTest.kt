package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStateTest {
    private var now = 0L
    private val s = RouteState { now }

    private fun t(provider: String = "g", worker: Boolean = true) = Target(
        providerId = provider,
        providerName = provider,
        baseUrl = "https://direct.example/v1",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m", provider = provider.substringBefore('#'), displayName = "m", priority = 1),
        altBaseUrl = if (worker) "https://worker.example/g/v1" else ""
    )

    @Test fun transientDirectFailureCoolsForNinetySeconds() {
        s.onDirectFailure("g", ErrorKind.NETWORK)
        now = 89_999
        assertFalse(s.directAllowed(t()))
        now = 90_000
        assertTrue(s.directAllowed(t()))
    }

    @Test fun regionFailureCoolsForFifteenMinutes() {
        s.onDirectFailure("g", ErrorKind.REGION)
        now = 899_999
        assertFalse(s.directAllowed(t()))
        now = 900_000
        assertTrue(s.directAllowed(t()))
    }

    @Test fun routeIsWorkerOnlyWhenDirectBadAndWorkerGood() {
        assertEquals(NetRoute.DIRECT, s.route(t()))
        s.onDirectFailure("g", ErrorKind.NETWORK)
        assertEquals(NetRoute.DIRECT, s.route(t()))
        s.onWorkerSuccess("g")
        assertEquals(NetRoute.WORKER, s.route(t()))
        now = 90_000
        assertEquals(NetRoute.DIRECT, s.route(t()))
    }

    @Test fun routeStaysDirectWithoutWorkerUrl() {
        s.onDirectFailure("g", ErrorKind.NETWORK)
        s.onWorkerSuccess("g")
        assertEquals(NetRoute.DIRECT, s.route(t(worker = false)))
    }

    @Test fun routeFollowsDirectBadExpiry() {
        s.onWorkerSuccess("g")
        s.onDirectFailure("g", ErrorKind.REGION)
        now = 1_799_999
        assertEquals(NetRoute.DIRECT, s.route(t()))
        now = 0
        assertEquals(NetRoute.WORKER, s.route(t()))
    }

    @Test fun workerFailureDropsWorkerRoute() {
        s.onDirectFailure("g", ErrorKind.NETWORK)
        s.onWorkerSuccess("g")
        s.onWorkerFailure("g")
        assertEquals(NetRoute.DIRECT, s.route(t()))
    }

    @Test fun directSuccessClearsBothEntries() {
        s.onDirectFailure("g", ErrorKind.NETWORK)
        s.onWorkerSuccess("g")
        s.onDirectSuccess("g")
        assertTrue(s.directAllowed(t()))
        assertEquals(NetRoute.DIRECT, s.route(t()))
        assertTrue(s.snapshot().isEmpty())
    }

    @Test fun keysShareOneProviderEntry() {
        s.onDirectFailure("g#2", ErrorKind.NETWORK)
        assertFalse(s.directAllowed(t("g")))
        assertFalse(s.directAllowed(t("g#3")))
        assertTrue(s.directAllowed(t("h")))
    }

    @Test fun retryAtReturnsExpiryOrZero() {
        assertEquals(0L, s.retryAt(t()))
        now = 1_000
        s.onDirectFailure("g", ErrorKind.NETWORK)
        assertEquals(91_000L, s.retryAt(t()))
    }

    @Test fun clearDirectBadReportsWhetherAnythingChanged() {
        assertFalse(s.clearDirectBad())
        s.onDirectFailure("g", ErrorKind.NETWORK)
        assertTrue(s.clearDirectBad())
        assertTrue(s.directAllowed(t()))
        assertFalse(s.clearDirectBad())
    }

    @Test fun resetForgetsEverything() {
        s.onDirectFailure("g", ErrorKind.REGION)
        s.onWorkerSuccess("g")
        s.reset()
        assertTrue(s.directAllowed(t()))
        assertEquals(NetRoute.DIRECT, s.route(t()))
        assertTrue(s.snapshot().isEmpty())
    }

    @Test fun snapshotDescribesRoutes() {
        s.onDirectFailure("g", ErrorKind.REGION)
        s.onDirectFailure("h", ErrorKind.NETWORK)
        s.onWorkerSuccess("h")
        val rows = s.snapshot().associateBy { it.providerId }
        assertTrue(rows.getValue("g").directBad)
        assertFalse(rows.getValue("g").viaWorker)
        assertEquals(ErrorKind.REGION, rows.getValue("g").kind)
        assertTrue(rows.getValue("h").viaWorker)
        assertNull(s.snapshot().firstOrNull { it.providerId == "z" })
    }
}
