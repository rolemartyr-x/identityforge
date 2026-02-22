package com.identityforge.app.web

import com.identityforge.app.db.SqliteDatabase
import com.identityforge.app.db.SqliteHabitRepository
import com.identityforge.app.db.SqliteIdentityRepository
import com.identityforge.app.db.SqliteVoteRepository
import com.identityforge.app.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class WebRoutes(private val db: SqliteDatabase) {

  private val identities: IdentityRepository = SqliteIdentityRepository(db)
  private val habits: HabitRepository = SqliteHabitRepository(db)
  private val votes: VoteRepository = SqliteVoteRepository(db)

  fun install(app: Application) {
    app.routing {
      get("/") { call.respondRedirect("/habits") }

      get("/identities") { renderIdentities(call) }
      post("/identities") { createIdentity(call) }

      get("/habits") { renderHabits(call) }
      post("/habits") { createHabit(call) }

      get("/habits/{id}") { renderHabitDetail(call) }
      post("/habits/{id}/votes") { castVote(call) }
    }
  }

  private suspend fun renderIdentities(call: ApplicationCall) {
    val list = identities.listActive()
    call.respondHtml(HttpStatusCode.OK) {
      page("Identities") {
        h2 { +"Identities" }
        p { a("/habits") { +"Habits" } }

        h3 { +"Create identity" }
        form(action = "/identities", method = FormMethod.post) {
          textInput {
            name = "name"
            placeholder = "Identity name"
          }
          submitInput { value = "Create" }
        }

        h3 { +"Existing" }
        if (list.isEmpty()) {
          p { +"No identities yet." }
        } else {
          ul {
            list.forEach {
              li { +it.name }
            }
          }
        }
      }
    }
  }

  private suspend fun createIdentity(call: ApplicationCall) {
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    if (name.isBlank()) {
      return call.respondRedirect("/identities")
    }
    identities.create(name = name, now = System.currentTimeMillis())
    call.respondRedirect("/identities")
  }

  private suspend fun renderHabits(call: ApplicationCall) {
    val identityList = identities.listActive()
    val habitList = habits.listActive()

    val identityById = identityList.associateBy { it.id }

    val enriched = habitList.mapNotNull { h ->
      val identity = identityById[h.identityId] ?: return@mapNotNull null
      HabitWithStats(
        habit = h,
        identity = identity,
        voteCount = votes.countForHabit(h.id),
        lastVoteAt = votes.lastVoteAt(h.id)
      )
    }

    call.respondHtml(HttpStatusCode.OK) {
      page("Habits") {
        h2 { +"Habits" }
        p { a("/identities") { +"Identities" } }

        if (identityList.isEmpty()) {
          p {
            +"You need at least one identity before creating habits. "
            a("/identities") { +"Create an identity" }
            +"."
          }
        } else {
          h3 { +"Create habit" }
          form(action = "/habits", method = FormMethod.post) {
            label {
              +"Identity: "
              select {
                name = "identityId"
                identityList.forEach { i ->
                  option {
                    value = i.id.toString()
                    +i.name
                  }
                }
              }
            }
            br()
            textInput {
              name = "name"
              placeholder = "Habit name"
            }
            submitInput { value = "Create" }
          }
        }

        h3 { +"Existing" }
        if (enriched.isEmpty()) {
          p { +"No habits yet." }
        } else {
          table {
            thead {
              tr {
                th { +"Habit" }
                th { +"Identity" }
                th { +"Votes" }
                th { +"Last vote" }
              }
            }
            tbody {
              enriched.forEach { row ->
                tr {
                  td { a("/habits/${row.habit.id}") { +row.habit.name } }
                  td { +row.identity.name }
                  td { +row.voteCount.toString() }
                  td { +(row.lastVoteAt?.let { fmt(it) } ?: "Never") }
                }
              }
            }
          }
        }
      }
    }
  }

  private suspend fun createHabit(call: ApplicationCall) {
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    val identityIdRaw = params["identityId"]?.trim().orEmpty()

    if (name.isBlank()) return call.respondRedirect("/habits")

    val identityId = runCatching { UUID.fromString(identityIdRaw) }.getOrNull()
      ?: return call.respondRedirect("/habits")

    val identity = identities.getActive(identityId) ?: return call.respondRedirect("/habits")

    val habit = habits.create(identityId = identity.id, name = name, now = System.currentTimeMillis())
    call.respondRedirect("/habits/${habit.id}")
  }

  private suspend fun renderHabitDetail(call: ApplicationCall) {
    val habitId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      ?: return call.respondRedirect("/habits")

    val habit = habits.getActive(habitId) ?: return call.respondRedirect("/habits")
    val identity = identities.getActive(habit.identityId) ?: return call.respondRedirect("/habits")

    val history = votes.listForHabit(habitId, limit = 200)
    val count = votes.countForHabit(habitId)

    call.respondHtml(HttpStatusCode.OK) {
      page("Habit") {
        h2 { +habit.name }
        p {
          +"Identity: "
          b { +identity.name }
        }
        p {
          a("/habits") { +"Back to habits" }
          +" | "
          a("/identities") { +"Identities" }
        }

        h3 { +"Cast vote" }
        form(action = "/habits/${habit.id}/votes", method = FormMethod.post) {
          label {
            +"Value (optional integer): "
            textInput {
              name = "value"
              placeholder = "blank means no value"
            }
          }
          submitInput { value = "Cast vote" }
        }

        h3 { +"Summary" }
        p { +"Total votes: $count" }

        h3 { +"Vote history" }
        if (history.isEmpty()) {
          p { +"No votes yet." }
        } else {
          table {
            thead {
              tr {
                th { +"When" }
                th { +"Value" }
              }
            }
            tbody {
              history.forEach { v ->
                tr {
                  td { +fmt(v.createdAt) }
                  td { +(v.value?.toString() ?: "") }
                }
              }
            }
          }
        }
      }
    }
  }

  private suspend fun castVote(call: ApplicationCall) {
    val habitId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      ?: return call.respondRedirect("/habits")

    val habit = habits.getActive(habitId) ?: return call.respondRedirect("/habits")

    val params = call.receiveParameters()
    val valueRaw = params["value"]?.trim().orEmpty()
    val value = if (valueRaw.isBlank()) null else valueRaw.toIntOrNull()

    votes.cast(habitId = habit.id, value = value, now = System.currentTimeMillis())
    call.respondRedirect("/habits/${habit.id}")
  }

  private fun HTML.page(titleText: String, block: BODY.() -> Unit) {
    head {
      title { +"IdentityForge | $titleText" }
      style {
        +"""
          body { font-family: sans-serif; margin: 24px; max-width: 900px; }
          table { border-collapse: collapse; width: 100%; margin-top: 8px; }
          th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
          th { background: #f5f5f5; }
          input, select { padding: 6px; margin: 4px 0; }
          form { margin: 8px 0 16px 0; }
          a { text-decoration: none; }
        """.trimIndent()
      }
    }
    body {
      h1 { +"IdentityForge" }
      block()
    }
  }

  private fun fmt(epochMs: Long): String {
    val dt = Instant.ofEpochMilli(epochMs)
      .atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dt)
  }
}
