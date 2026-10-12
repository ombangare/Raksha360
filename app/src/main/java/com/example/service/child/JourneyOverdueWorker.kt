package com.example.service.child

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.NaariKavachApp

class JourneyOverdueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val journeyId = inputData.getString(KEY_JOURNEY_ID) ?: return Result.failure()
        val app = applicationContext as? NaariKavachApp ?: return Result.failure()
        return try {
            if (app.childSafetyRepo.markJourneyOverdue(journeyId)) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_JOURNEY_ID = "journey_id"
    }
}
