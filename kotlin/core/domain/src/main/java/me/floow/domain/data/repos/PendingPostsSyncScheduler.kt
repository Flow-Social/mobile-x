package me.floow.domain.data.repos

interface PendingPostsSyncScheduler {
	fun enqueueNow()
	fun ensurePeriodic()
}
