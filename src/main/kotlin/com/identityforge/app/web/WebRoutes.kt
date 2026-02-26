package com.identityforge.app.web

import com.identityforge.app.db.SqliteDatabase
import com.identityforge.app.db.SqliteHabitRepository
import com.identityforge.app.db.SqliteIdentityRepository
import com.identityforge.app.domain.HabitRepository
import com.identityforge.app.domain.IdentityRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.*
import kotlinx.html.BODY
import kotlinx.html.HTML
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.option
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class WebRoutes(private val db: SqliteDatabase) {

  private val identities: IdentityRepository = SqliteIdentityRepository(db)
  private val habits: HabitRepository = SqliteHabitRepository(db)

  fun install(app: Application) {
    app.routing {
      get("/") { call.respondRedirect("/today") }

      get("/today") { renderToday(call) }
      post("/today/{identityId}/votes") { castTodayVote(call) }

      get("/history") { renderHistory(call) }

      get("/manage") { renderManage(call) }
      post("/manage/identities") { createIdentity(call) }
      post("/manage/identities/{id}/delete") { deleteIdentity(call) }
      post("/manage/habits") { createHabit(call) }
    }
  }

  private suspend fun renderToday(call: ApplicationCall) {
    val dashboard = identities.getIdentityDashboardItems(now = System.currentTimeMillis())
    call.respondHtml(HttpStatusCode.OK) {
      page("Today", "today") {
        h2 { +"Today" }
        if (dashboard.isEmpty()) {
          div("empty-state") {
            p { +"Create your first identity." }
            a("/manage") { +"Go to Manage" }
          }
          return@page
        }

        dashboard.forEach { item ->
          div("identity-card") {
            id = "identity-${item.identityId}"
            h3 { +item.identityName }
            p("supportive") { +"You are becoming this." }
            p { +"Votes Today: ${item.votesToday}" }
            p { +"Total Lifetime Votes: ${item.totalVotes}" }
            p {
              +(if (item.votesLast7Days > 0) {
                "Last 7 Days: ${item.votesLast7Days} votes"
              } else {
                "Start today."
              })
            }
            form(action = "/today/${item.identityId}/votes", method = FormMethod.post) {
              button {
                attributes["class"] = "cast-vote"
                attributes["data-identity-id"] = item.identityId.toString()
                type = kotlinx.html.ButtonType.submit
                +"Cast Vote"
              }
            }
          }
        }
        div {
          id = "snackbar"
          +""
        }
      }
    }
  }

  private suspend fun castTodayVote(call: ApplicationCall) {
    val identityId = call.parameters["identityId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      ?: return call.respondRedirect("/today")
    identities.castVote(identityId = identityId, now = System.currentTimeMillis())
    call.respondRedirect("/today?voted=$identityId")
  }

  private suspend fun renderHistory(call: ApplicationCall) {
    val history = identities.listVoteHistory(limit = 500)
    call.respondHtml(HttpStatusCode.OK) {
      page("History", "history") {
        h2 { +"History" }
        if (history.isEmpty()) {
          p { +"No votes yet." }
          return@page
        }
        ul {
          history.forEach { vote ->
            li {
              +"${vote.identityName} — ${fmt(vote.createdAt)}"
            }
          }
        }
      }
    }
  }

  private suspend fun renderManage(call: ApplicationCall) {
    val identityList = identities.listActive()
    val habitList = habits.listActive()

    call.respondHtml(HttpStatusCode.OK) {
      page("Manage", "manage") {
        h2 { +"Manage" }

        h3 { +"Create Identity" }
        form(action = "/manage/identities", method = FormMethod.post) {
          textInput {
            name = "name"
            placeholder = "Identity name"
          }
          submitInput { value = "Create Identity" }
        }

        h3 { +"Delete Identity" }
        if (identityList.isEmpty()) {
          p { +"No active identities." }
        } else {
          identityList.forEach { identity ->
            form(action = "/manage/identities/${identity.id}/delete", method = FormMethod.post) {
              +identity.name
              +" "
              submitInput { value = "Delete Identity" }
            }
          }
        }

        h3 { +"Create Habit" }
        if (identityList.isEmpty()) {
          p { +"Create an identity first." }
        } else {
          form(action = "/manage/habits", method = FormMethod.post) {
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
            submitInput { value = "Create Habit" }
          }
        }

        h3 { +"Active Habits" }
        if (habitList.isEmpty()) {
          p { +"No habits yet." }
        } else {
          ul {
            habitList.forEach { habit -> li { +habit.name } }
          }
        }
      }
    }
  }

  private suspend fun createIdentity(call: ApplicationCall) {
    val name = call.receiveParameters()["name"]?.trim().orEmpty()
    if (name.isNotBlank()) {
      identities.create(name = name, now = System.currentTimeMillis())
    }
    call.respondRedirect("/manage")
  }

  private suspend fun deleteIdentity(call: ApplicationCall) {
    val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      ?: return call.respondRedirect("/manage")
    identities.softDelete(id = id, now = System.currentTimeMillis())
    call.respondRedirect("/manage")
  }

  private suspend fun createHabit(call: ApplicationCall) {
    val params = call.receiveParameters()
    val name = params["name"]?.trim().orEmpty()
    val identityIdRaw = params["identityId"]?.trim().orEmpty()
    val identityId = runCatching { UUID.fromString(identityIdRaw) }.getOrNull()

    if (name.isNotBlank() && identityId != null && identities.getActive(identityId) != null) {
      habits.create(identityId = identityId, name = name, now = System.currentTimeMillis())
    }
    call.respondRedirect("/manage")
  }

  private fun HTML.page(titleText: String, activeTab: String, block: BODY.() -> Unit) {
    head {
      title { +"IdentityForge | $titleText" }
      style {
        +"""
          body { font-family: sans-serif; margin: 24px; max-width: 760px; }
          .bottom-nav { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; display: flex; justify-content: space-around; border-top: 1px solid #ddd; padding: 12px; }
          .bottom-nav a { color: #444; }
          .bottom-nav a.active { font-weight: bold; }
          .identity-card { border: 1px solid #ddd; border-radius: 12px; padding: 12px; margin-bottom: 12px; transition: transform 0.18s ease; }
          .identity-card.voted { transform: scale(1.05); }
          .supportive { color: #4b5563; }
          .cast-vote { padding: 8px 12px; }
          .empty-state { text-align: center; margin: 80px 0; }
          #snackbar { position: fixed; bottom: 72px; left: 50%; transform: translateX(-50%); background: #2d3748; color: white; padding: 8px 12px; border-radius: 8px; display: none; }
          #snackbar.show { display: block; }
        """.trimIndent()
      }
    }
    body {
      h1 { +"IdentityForge" }
      block()
      nav("bottom-nav") {
        a("/today", classes = if (activeTab == "today") "active" else null) { +"Today" }
        a("/history", classes = if (activeTab == "history") "active" else null) { +"History" }
        a("/manage", classes = if (activeTab == "manage") "active" else null) { +"Manage" }
      }
      script {
        unsafe {
          +"""
            const url = new URL(window.location.href);
            const votedIdentityId = url.searchParams.get('voted');
            if (votedIdentityId) {
              const card = document.querySelector('#identity-' + votedIdentityId);
              const snack = document.querySelector('#snackbar');
              if (card) {
                card.classList.add('voted');
                setTimeout(() => card.classList.remove('voted'), 220);
                const name = card.querySelector('h3')?.textContent || 'Identity';
                if (snack) {
                  snack.textContent = `Vote cast for ${'$'}{name}`;
                  snack.classList.add('show');
                  setTimeout(() => snack.classList.remove('show'), 1400);
                }
              }
              url.searchParams.delete('voted');
              window.history.replaceState({}, '', url.toString());
            }
          """.trimIndent()
        }
      }
    }
  }

  private fun fmt(epochMs: Long): String {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dt)
  }
}
