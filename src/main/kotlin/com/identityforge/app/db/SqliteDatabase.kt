package com.identityforge.app.db

import java.sql.Connection
import java.sql.DriverManager

class SqliteDatabase(private val jdbcUrl: String) {

  fun <T> withConnection(block: (Connection) -> T): T {
    DriverManager.getConnection(jdbcUrl).use { conn ->
      conn.autoCommit = true
      return block(conn)
    }
  }

  fun migrate() {
    withConnection { conn ->
      conn.createStatement().use { st ->
        st.execute(
          """
          CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            applied_at INTEGER NOT NULL
          )
          """.trimIndent()
        )
      }

      applyMigration(conn, version = 1) {
        conn.createStatement().use { st ->
          st.execute(
            """
            CREATE TABLE IF NOT EXISTS identities (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              deleted_at INTEGER NULL
            )
            """.trimIndent()
          )

          st.execute(
            """
            CREATE TABLE IF NOT EXISTS habits (
              id TEXT PRIMARY KEY,
              identity_id TEXT NOT NULL,
              name TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              deleted_at INTEGER NULL,
              FOREIGN KEY(identity_id) REFERENCES identities(id)
            )
            """.trimIndent()
          )

          st.execute(
            """
            CREATE TABLE IF NOT EXISTS votes (
              id TEXT PRIMARY KEY,
              identity_id TEXT NOT NULL,
              habit_id TEXT NOT NULL,
              value INTEGER NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              deleted_at INTEGER NULL,
              FOREIGN KEY(identity_id) REFERENCES identities(id),
              FOREIGN KEY(habit_id) REFERENCES habits(id)
            )
            """.trimIndent()
          )

          st.execute("CREATE INDEX IF NOT EXISTS idx_habits_identity_id ON habits(identity_id)")
          st.execute("CREATE INDEX IF NOT EXISTS idx_votes_habit_id_created_at ON votes(habit_id, created_at DESC)")
        }
      }

      applyMigration(conn, version = 2) {
        conn.createStatement().use { st ->
          if (!conn.columnExists("votes", "identity_id")) {
            st.execute("ALTER TABLE votes ADD COLUMN identity_id TEXT")
          }
          if (!conn.columnExists("votes", "updated_at")) {
            st.execute("ALTER TABLE votes ADD COLUMN updated_at INTEGER")
          }
        }

        conn.createStatement().use { st ->
          st.execute(
            """
            UPDATE votes
            SET identity_id = (
              SELECT habits.identity_id
              FROM habits
              WHERE habits.id = votes.habit_id
            )
            WHERE identity_id IS NULL
            """.trimIndent()
          )
          st.execute("UPDATE votes SET updated_at = created_at WHERE updated_at IS NULL")
        }
      }
    }
  }

  private fun applyMigration(conn: Connection, version: Int, body: () -> Unit) {
    val alreadyApplied = conn.prepareStatement(
      "SELECT 1 FROM schema_migrations WHERE version = ? LIMIT 1"
    ).use { ps ->
      ps.setInt(1, version)
      ps.executeQuery().use { rs -> rs.next() }
    }

    if (alreadyApplied) return

    body()

    conn.prepareStatement(
      "INSERT INTO schema_migrations(version, applied_at) VALUES(?, ?)"
    ).use { ps ->
      ps.setInt(1, version)
      ps.setLong(2, System.currentTimeMillis())
      ps.executeUpdate()
    }
  }
}

private fun Connection.columnExists(table: String, column: String): Boolean {
  prepareStatement("PRAGMA table_info($table)").use { ps ->
    ps.executeQuery().use { rs ->
      while (rs.next()) {
        if (rs.getString("name") == column) {
          return true
        }
      }
    }
  }
  return false
}
