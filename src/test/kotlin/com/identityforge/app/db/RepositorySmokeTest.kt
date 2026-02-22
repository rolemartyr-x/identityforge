package com.identityforge.app.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
