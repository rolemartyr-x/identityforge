package com.identityforge.app

import com.identityforge.app.db.SqliteDatabase
import com.identityforge.app.web.WebRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  val host = "0.0.0.0"
  val dbPath = System.getenv("DB_PATH") ?: "identityforge.db"
  val jdbcUrl = "jdbc:sqlite:$dbPath"

  val database = SqliteDatabase(jdbcUrl)
  database.migrate()

  embeddedServer(Netty, port = port, host = host) {
    module(database)
  }.start(wait = true)
}