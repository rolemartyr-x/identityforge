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

  override fun softDelete(id: UUID, now: Long): Boolean =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        UPDATE identities
        SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """.trimIndent()
      ).use { ps ->
        ps.setLong(1, now)
        ps.setLong(2, now)
        ps.setString(3, id.toString())
        ps.executeUpdate() > 0
      }
    }

  override fun getIdentityDashboardItems(now: Long): List<IdentityDashboardItem> =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT
          i.id AS identity_id,
          i.name AS identity_name,
          COALESCE(SUM(CASE WHEN v.created_at >= ? THEN 1 ELSE 0 END), 0) AS votes_today,
          COALESCE(COUNT(v.id), 0) AS total_votes,
          COALESCE(SUM(CASE WHEN v.created_at >= ? THEN 1 ELSE 0 END), 0) AS votes_last_7_days
        FROM identities i
        LEFT JOIN votes v
          ON v.identity_id = i.id
          AND v.deleted_at IS NULL
        WHERE i.deleted_at IS NULL
        GROUP BY i.id, i.name
        ORDER BY i.created_at DESC
        """.trimIndent()
      ).use { ps ->
        val startOfDayMs = now - (now % 86_400_000L)
        val last7DaysMs = now - 7 * 86_400_000L
        ps.setLong(1, startOfDayMs)
        ps.setLong(2, last7DaysMs)
        ps.executeQuery().use { rs ->
          val out = mutableListOf<IdentityDashboardItem>()
          while (rs.next()) {
            out.add(
              IdentityDashboardItem(
                identityId = UUID.fromString(rs.getString("identity_id")),
                identityName = rs.getString("identity_name"),
                votesToday = rs.getInt("votes_today"),
                totalVotes = rs.getInt("total_votes"),
                votesLast7Days = rs.getInt("votes_last_7_days")
              )
            )
          }
          out
        }
      }
    }

  override fun castVote(identityId: UUID, now: Long): Vote =
    db.withConnection { conn ->
      val habitId = conn.prepareStatement(
        """
        SELECT id
        FROM habits
        WHERE identity_id = ? AND deleted_at IS NULL
        ORDER BY created_at ASC
        LIMIT 1
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, identityId.toString())
        ps.executeQuery().use { rs ->
          if (!rs.next()) {
            val newHabitId = UUID.randomUUID()
            conn.prepareStatement(
              """
              INSERT INTO habits(id, identity_id, name, created_at, updated_at, deleted_at)
              VALUES(?, ?, ?, ?, ?, NULL)
              """.trimIndent()
            ).use { insertPs ->
              insertPs.setString(1, newHabitId.toString())
              insertPs.setString(2, identityId.toString())
              insertPs.setString(3, "Default Habit")
              insertPs.setLong(4, now)
              insertPs.setLong(5, now)
              insertPs.executeUpdate()
            }
            newHabitId
          } else {
            UUID.fromString(rs.getString("id"))
          }
        }
      }

      val voteId = UUID.randomUUID()
      conn.prepareStatement(
        """
        INSERT INTO votes(id, identity_id, habit_id, value, created_at, updated_at, deleted_at)
        VALUES(?, ?, ?, NULL, ?, ?, NULL)
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, voteId.toString())
        ps.setString(2, identityId.toString())
        ps.setString(3, habitId.toString())
        ps.setLong(4, now)
        ps.setLong(5, now)
        ps.executeUpdate()
      }

      Vote(id = voteId, identityId = identityId, habitId = habitId, value = null, createdAt = now, updatedAt = now, deletedAt = null)
    }

  override fun listVoteHistory(limit: Int): List<VoteHistoryItem> =
    db.withConnection { conn ->
      conn.prepareStatement(
        """
        SELECT v.id AS vote_id, i.name AS identity_name, v.created_at
        FROM votes v
        JOIN identities i ON i.id = v.identity_id
        WHERE v.deleted_at IS NULL
          AND i.deleted_at IS NULL
        ORDER BY v.created_at DESC
        LIMIT ?
        """.trimIndent()
      ).use { ps ->
        ps.setInt(1, limit)
        ps.executeQuery().use { rs ->
          val out = mutableListOf<VoteHistoryItem>()
          while (rs.next()) {
            out.add(
              VoteHistoryItem(
                voteId = UUID.fromString(rs.getString("vote_id")),
                identityName = rs.getString("identity_name"),
                createdAt = rs.getLong("created_at")
              )
            )
          }
          out
        }
      }
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
        SELECT id, identity_id, habit_id, value, created_at, updated_at, deleted_at
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
                identityId = UUID.fromString(rs.getString("identity_id")),
                habitId = UUID.fromString(rs.getString("habit_id")),
                value = rs.getIntOrNull("value"),
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

  override fun cast(habitId: UUID, value: Int?, now: Long): Vote {
    val id = UUID.randomUUID()
    val identityId = db.withConnection { conn ->
      conn.prepareStatement("SELECT identity_id FROM habits WHERE id = ? LIMIT 1").use { ps ->
        ps.setString(1, habitId.toString())
        ps.executeQuery().use { rs ->
          if (!rs.next()) error("Habit not found for vote")
          UUID.fromString(rs.getString("identity_id"))
        }
      }
    }

    db.withConnection { conn ->
      conn.prepareStatement(
        """
        INSERT INTO votes(id, identity_id, habit_id, value, created_at, updated_at, deleted_at)
        VALUES(?, ?, ?, ?, ?, ?, NULL)
        """.trimIndent()
      ).use { ps ->
        ps.setString(1, id.toString())
        ps.setString(2, identityId.toString())
        ps.setString(3, habitId.toString())
        if (value == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setInt(4, value)
        ps.setLong(5, now)
        ps.setLong(6, now)
        ps.executeUpdate()
      }
    }
    return Vote(id = id, identityId = identityId, habitId = habitId, value = value, createdAt = now, updatedAt = now, deletedAt = null)
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
