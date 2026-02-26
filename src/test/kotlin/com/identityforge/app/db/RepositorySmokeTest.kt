package com.identityforge.app.db

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepositorySmokeTest {

  @Test
  fun `can create identity habit vote and read back`() {
    val db = SqliteDatabase("jdbc:sqlite::memory:")
    db.migrate()

    val identities = SqliteIdentityRepository(db)
    val habits = SqliteHabitRepository(db)
    val votes = SqliteVoteRepository(db)

    val now = System.currentTimeMillis()

    val identity = identities.create("Present father", now)
    assertNotNull(identities.getActive(identity.id))

    val habit = habits.create(identity.id, "Read to kids", now)
    assertNotNull(habits.getActive(habit.id))

    votes.cast(habit.id, value = null, now = now + 1000)

    assertEquals(1, votes.countForHabit(habit.id))
    assertNotNull(votes.lastVoteAt(habit.id))
    assertEquals(1, votes.listForHabit(habit.id).size)
  }

  @Test
  fun `identity dashboard and cast vote aggregate at identity level`() {
    val db = SqliteDatabase("jdbc:sqlite::memory:")
    db.migrate()

    val identities = SqliteIdentityRepository(db)
    val habits = SqliteHabitRepository(db)

    val now = 1_700_000_000_000L
    val identity = identities.create("Consistent runner", now)
    habits.create(identity.id, "Morning run", now)

    identities.castVote(identity.id, now + 100)
    identities.castVote(identity.id, now + 200)

    val dashboard = identities.getIdentityDashboardItems(now + 300)
    assertEquals(1, dashboard.size)
    assertEquals(2, dashboard.first().votesToday)
    assertEquals(2, dashboard.first().totalVotes)
    assertEquals(2, dashboard.first().votesLast7Days)
  }

  @Test
  fun `soft deleted identities are excluded and vote history remains filtered`() {
    val db = SqliteDatabase("jdbc:sqlite::memory:")
    db.migrate()

    val identities = SqliteIdentityRepository(db)
    val habits = SqliteHabitRepository(db)
    val votes = SqliteVoteRepository(db)
    val now = 1_700_000_000_000L

    val identity = identities.create("Lifelong learner", now)
    val habit = habits.create(identity.id, "Read papers", now)
    votes.cast(habit.id, value = null, now = now + 100)

    assertEquals(1, identities.listVoteHistory().size)
    assertTrue(identities.softDelete(identity.id, now + 200))
    assertFalse(identities.softDelete(identity.id, now + 300))

    assertEquals(0, identities.getIdentityDashboardItems(now + 400).size)
    assertEquals(0, identities.listVoteHistory().size)
  }
}
