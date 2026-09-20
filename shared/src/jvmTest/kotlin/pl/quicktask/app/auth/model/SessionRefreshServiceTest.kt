package pl.quicktask.app.auth.model

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import pl.quicktask.app.auth.data.SessionRefreshService
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.testing.TestSettings
import kotlin.test.*

class SessionRefreshServiceTest {
    private fun tokens(n: Int) = FinishLoginResponseDto("access-$n", "refresh-$n", "DPoP", 45, 90)

    @Test
    fun reuseIsLimitedToCurrentSessionAndWindow() = runTest {
        val session = SessionManager(TestSettings()).apply { saveSession("initial", "initial-refresh", "first") }
        var now = 10_000L
        var calls = 0
        val service = SessionRefreshService(session, { tokens(++calls) }, { session.clearSession() }, { now }, StandardTestDispatcher(testScheduler))
        service.refreshSession().getOrThrow()
        service.refreshSession().getOrThrow()
        assertEquals(1, calls)
        session.saveSession("other", "other-refresh", "second")
        service.refreshSession().getOrThrow()
        assertEquals(2, calls)
        service.reset()
        service.refreshSession().getOrThrow()
        assertEquals(3, calls)
        now += 2_000
        service.refreshSession().getOrThrow()
        assertEquals(4, calls)
    }

    @Test
    fun concurrentRefreshesShareResultAndCannotOverwriteChangedSession() = runTest {
        val session = SessionManager(TestSettings()).apply { saveSession("old", "old-refresh", "first") }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val service = SessionRefreshService(session, {
            calls++
            entered.complete(Unit)
            release.await()
            tokens(calls)
        }, { session.clearSession() }, { 10_000L }, StandardTestDispatcher(testScheduler))
        val first = async { service.refreshSession() }
        entered.await()
        val second = async { service.refreshSession() }
        release.complete(Unit)
        first.await().getOrThrow()
        second.await().getOrThrow()
        assertEquals(1, calls)

        val enteredAgain = CompletableDeferred<Unit>()
        val releaseAgain = CompletableDeferred<Unit>()
        val changing = SessionRefreshService(session, {
            enteredAgain.complete(Unit); releaseAgain.await(); tokens(99)
        }, { session.clearSession() }, dispatcher = StandardTestDispatcher(testScheduler))
        val result = async { changing.refreshSession() }
        enteredAgain.await()
        changing.reset()
        session.saveSession("new-session", "new-refresh", "second")
        releaseAgain.complete(Unit)
        assertTrue(result.await().isFailure)
        assertEquals("new-session", session.accessToken)
    }

    @Test
    fun cancellationAndApiFailureHaveDifferentSessionEffects() = runTest {
        val session = SessionManager(TestSettings()).apply { saveSession("a", "r", "first") }
        val cancelled = SessionRefreshService(session, { throw CancellationException() }, { session.clearSession() }, dispatcher = StandardTestDispatcher(testScheduler))
        assertFailsWith<CancellationException> { cancelled.refreshSession() }
        assertTrue(session.isLoggedIn)
        val failed = SessionRefreshService(session, { throw ApiException("expired", 401, "secret") }, { session.clearSession() }, dispatcher = StandardTestDispatcher(testScheduler))
        assertTrue(failed.refreshSession().isFailure)
        assertFalse(session.isLoggedIn)
    }
}
