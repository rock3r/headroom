package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Which windows an account's own provider reported, and which a second sign-in added, has to
 * survive storage: the account's headline, pace, sorting and stats all read it back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WindowOriginStorageTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val claude = DemoData.accounts(now).first { it.account.provider == Provider.Claude }
    private lateinit var db: HeadroomDatabase

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabase()
    }

    @AfterTest fun tearDown() = db.close()

    @Test
    fun `a window a second sign-in added stays marked as one through storage`() =
        runTest(UnconfinedTestDispatcher()) {
            val own = claude.snapshot!!.windows.first()
            val added = own.copy(id = "grok-bot", label = "Grok Bot", isSecondary = true)
            val snapshot = claude.snapshot!!.copy(windows = listOf(own, added))
            val repo =
                RoomQuotaRepository(
                    db.quotaDao(),
                    db.accountOrderDao(),
                    { QuotaResult.Success(snapshot) },
                    clock = { now },
                    scope = backgroundScope,
                )
            repo.addAccount(claude.account)
            repo.refresh()

            val stored = repo.current().single().snapshot!!.windows
            // Without this the account leads with the added window again as soon as it is read
            // back, and its headline, pace and sorting follow the wrong provider.
            assertEquals(true, stored.single { it.id == "grok-bot" }.isSecondary)
            assertEquals(false, stored.single { it.id == own.id }.isSecondary)
        }
}
