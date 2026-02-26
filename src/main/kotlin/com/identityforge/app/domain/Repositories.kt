package com.identityforge.app.domain

import java.util.UUID

interface IdentityRepository {
  fun listActive(): List<Identity>
  fun getActive(id: UUID): Identity?
  fun create(name: String, now: Long): Identity
  fun softDelete(id: UUID, now: Long): Boolean
  fun getIdentityDashboardItems(now: Long): List<IdentityDashboardItem>
  fun castVote(identityId: UUID, now: Long): Vote
  fun listVoteHistory(limit: Int = 200): List<VoteHistoryItem>
}

interface HabitRepository {
  fun listActive(): List<Habit>
  fun getActive(id: UUID): Habit?
  fun create(identityId: UUID, name: String, now: Long): Habit
}

interface VoteRepository {
  fun listForHabit(habitId: UUID, limit: Int = 200): List<Vote>
  fun cast(habitId: UUID, value: Int?, now: Long): Vote
  fun countForHabit(habitId: UUID): Int
  fun lastVoteAt(habitId: UUID): Long?
}
