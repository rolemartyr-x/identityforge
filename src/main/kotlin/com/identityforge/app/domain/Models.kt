package com.identityforge.app.domain

import java.util.UUID

data class Identity(
  val id: UUID,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long?
)

data class Habit(
  val id: UUID,
  val identityId: UUID,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long?
)

data class Vote(
  val id: UUID,
  val identityId: UUID,
  val habitId: UUID,
  val value: Int?,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long?
)

data class IdentityDashboardItem(
  val identityId: UUID,
  val identityName: String,
  val votesToday: Int,
  val totalVotes: Int,
  val votesLast7Days: Int
)

data class VoteHistoryItem(
  val voteId: UUID,
  val identityName: String,
  val createdAt: Long
)

data class HabitWithStats(
  val habit: Habit,
  val identity: Identity,
  val voteCount: Int,
  val lastVoteAt: Long?
)
