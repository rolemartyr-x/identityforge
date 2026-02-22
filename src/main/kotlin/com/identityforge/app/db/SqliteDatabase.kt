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
              habit_id TEXT NOT NULL,
              value INTEGER NULL,
              created_at INTEGER NOT NULL,
              deleted_at INTEGER NULL,
              FOREIGN KEY(habit_id) REFERENCES habits(id)
            )
            """.trimIndent()
          )

          st.execute("CREATE INDEX IF NOT EXISTS idx_habits_identity_id ON habits(identity_id)")
          st.execute("CREATE INDEX IF NOT EXISTS idx_votes_habit_id_created_at ON votes(habit_id, created_at DESC)")
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
