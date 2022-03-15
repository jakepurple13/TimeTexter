package com.programmersbox.timetexter

import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.compose.ui.util.fastMap
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.*
import kotlinx.coroutines.flow.singleOrNull
import java.util.*
import java.util.concurrent.TimeUnit

fun queueItem(context: Context, item: TextInfo) {

    val manager = WorkManager.getInstance(context)

    //val currentTime = System.currentTimeMillis()

    //c.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS
    //wanted time - current time == time until!
    //that's the initial delay

    val time = when(item.timeInfo.type) {
        TimeType.DAILY -> 1L to TimeUnit.MINUTES//TimeUnit.DAYS
        TimeType.WEEKLY -> 7L to TimeUnit.DAYS
        TimeType.MONTHLY -> 30L to TimeUnit.DAYS
        TimeType.YEARLY -> 365L to TimeUnit.DAYS
    }

    val c = Calendar.getInstance()

    val t = item.timeInfo.time.split(":").fastMap { it.toIntOrNull() }.filterNotNull()
    val ampm = item.timeInfo.amPm
    ampm?.let { AMPMHours.DayTime.valueOf(it) }
        ?.let {
            when(it) {
                AMPMHours.DayTime.AM -> t.first()
                AMPMHours.DayTime.PM -> t.first()
            }
        }

    c[Calendar.HOUR] = t.first()
    c[Calendar.MINUTE] = t.last()

    when(item.timeInfo.type) {
        TimeType.WEEKLY -> {
            c[Calendar.DAY_OF_WEEK] = item.timeInfo.weekDay ?: 0
        }
        TimeType.MONTHLY -> {
            c[Calendar.DAY_OF_MONTH] = item.timeInfo.date.dayOfMonth
        }
        TimeType.YEARLY -> {
            c[Calendar.DAY_OF_YEAR] = item.timeInfo.date.dayOfYear
        }
    }

    println(SimpleDateFormat.getDateTimeInstance().format(c.timeInMillis))
    println(c.timeInMillis -  System.currentTimeMillis())

    manager.enqueueUniquePeriodicWork(
        item.id,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<TextWorker>(time.first, time.second, flexTimeInterval = 5L, flexTimeIntervalUnit = TimeUnit.SECONDS)
            //.setInitialDelay(c.timeInMillis -  System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInitialDelay(5000, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to item.id))
            .build()
    )

}

class TextWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dao by lazy { ItemDatabase.getInstance(applicationContext).itemDao() }

        val id = inputData.getString("id")

        println(id)

        val item = id?.let { dao.getItem(it) }

        println(item)

        return Result.success()
    }

}