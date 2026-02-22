package com.identityforge.app.db

import com.identityforge.app.domain.*
import java.util.UUID

class SqliteIdentityRepository(private val db: SqliteDatabase) : IdentityRepository {

  override fun listActive(): List<Identity> =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT id, name, created_at, updated_at, deleted_at
        FROM identities
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
        """.trimIndent()
      ).use { ps ->
        ps.executeQuery().use { rs ->
          val out = mutableListOf<Identity>()
          while (rs.next()) {
            out.add(
              Identity(
                id = UUID.fromString(rs.getString("id")),
                name = rs.getString("name"),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at"),
                deletedAt = rs.getLongOrNull("deleted_at")
              )
            )
          }
          out
        }
      }
    }

  override fun getActive(id: UUID): Identity? =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT id, name, created_at, updated_at, deleted_at
        FROM identities
        WHERE id = ? AND deleted_at IS NULL
        LIMIT 1
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.executeQuery().use { rs ->
          if (!rs.next()) return@withConnection null
          Identity(
            id = UUID.fromString(rs.getString("id")),
            name = rs.getString("name"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            deletedAt = rs.getLongOrNull("deleted_at")
          )
        }
      }
    }

  override fun create(name: String, now: Long): Identity {
    val id = UUID.randomUUID()
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        INSERT INTO identities(id, name, created_at, updated_at, deleted_at)
        VALUES(?, ?, ?, ?, NULL)
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.setString(2, name)
        ps.setLong(3, now)
        ps.setLong(4, now)
        ps.executeUpdate()
      }
    }
    return Identity(id = id, name = name, createdAt = now, updatedAt = now, deletedAt = null)
  }
}

class SqliteHabitRepository(private val db: SqliteDatabase) : HabitRepository {

  override fun listActive(): List<Habit> =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT id, identity_id, name, created_at, updated_at, deleted_at
        FROM habits
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
        """.trimIndent()
      ).use { ps ->
        ps.executeQuery().use { rs ->
          val out = mutableListOf<Habit>()
          while (rs.next()) {
            out.add(
              Habit(
                id = UUID.fromString(rs.getString("id")),
                identityId = UUID.fromString(rs.getString("identity_id")),
                name = rs.getString("name"),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at"),
                deletedAt = rs.getLongOrNull("deleted_at")
              )
            )
          }
          out
        }
      }
    }

  override fun getActive(id: UUID): Habit? =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT id, identity_id, name, created_at, updated_at, deleted_at
        FROM habits
        WHERE id = ? AND deleted_at IS NULL
        LIMIT 1
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.executeQuery().use { rs ->
          if (!rs.next()) return@withConnection null
          Habit(
            id = UUID.fromString(rs.getString("id")),
            identityId = UUID.fromString(rs.getString("identity_id")),
            name = rs.getString("name"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            deletedAt = rs.getLongOrNull("deleted_at")
          )
        }
      }
    }

  override fun create(identityId: UUID, name: String, now: Long): Habit {
    val id = UUID.randomUUID()
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        INSERT INTO habits(id, identity_id, name, created_at, updated_at, deleted_at)
        VALUES(?, ?, ?, ?, ?, NULL)
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.setString(2, identityId.toString())
        ps.setString(3, name)
        ps.setLong(4, now)
        ps.setLong(5, now)
        ps.executeUpdate()
      }
    }
    return Habit(id = id, identityId = identityId, name = name, createdAt = now, updatedAt = now, deletedAt = null)
  }
}

class SqliteVoteRepository(private val db: SqliteDatabase) : VoteRepository {

  override fun listForHabit(habitId: UUID, limit: Int): List<Vote> =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT id, habit_id, value, created_at, deleted_at
        FROM votes
        WHERE habit_id = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT ?
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, habitId.toString())
        ps.setInt(2, limit)
        ps.executeQuery().use { rs ->
          val out = mutableListOf<Vote>()
          while (rs.next()) {
            out.add(
              Vote(
                id = UUID.fromString(rs.getString("id")),
                habitId = UUID.fromString(rs.getString("habit_id")),
                value = rs.getIntOrNull("value"),
                createdAt = rs.getLong("created_at"),
                deletedAt = rs.getLongOrNull("deleted_at")
              )
            )
          }
          out
        }
      }
    }

  override fun cast(habitId: UUID, value: Int?, now: Long): Vote {
    val id = UUID.randomUUID()
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        INSERT INTO votes(id, habit_id, value, created_at, deleted_at)
        VALUES(?, ?, ?, ?, NULL)
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.setString(2, habitId.toString())
        if (value == null) ps.setNull(3, java.sql.Types.INTEGER) else ps.setInt(3, value)
        ps.setLong(4, now)
        ps.executeUpdate()
      }
    }
    return Vote(id = id, habitId = habitId, value = value, createdAt = now, deletedAt = null)
  }

  override fun countForHabit(habitId: UUID): Int =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT COUNT(1) AS cnt
        FROM votes
        WHERE habit_id = ? AND deleted_at IS NULL
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, habitId.toString())
        ps.executeQuery().use { rs ->
          rs.next()
          rs.getInt("cnt")
        }
      }
    }

  override fun lastVoteAt(habitId: UUID): Long? =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT created_at
        FROM votes
        WHERE habit_id = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT 1
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, habitId.toString())
        ps.executeQuery().use { rs ->
          if (!rs.next()) null else rs.getLong("created_at")
        }
      }
    }
}

private fun java.sql.ResultSet.getLongOrNull(column: String): Long? {
  val v = getLong(column)
  return if (wasNull()) null else v
}

private fun java.sql.ResultSet.getIntOrNull(column: String): Int? {
  val v = getInt(column)
  return if (wasNull()) null else v
}
