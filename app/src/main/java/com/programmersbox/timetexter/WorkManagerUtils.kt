package com.programmersbox.timetexter

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.SimpleDateFormat
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.ui.util.fastMap
import androidx.work.*
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit


fun queueItem(context: Context, item: TextInfo) {

    val manager = WorkManager.getInstance(context)

    //val currentTime = System.currentTimeMillis()

    //c.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS
    //wanted time - current time == time until!
    //that's the initial delay

    val time = when(item.timeInfo.type) {
        TimeType.DAILY -> 1L to TimeUnit.DAYS
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
            c[Calendar.DAY_OF_MONTH] = LocalDate.parse(item.timeInfo.date).dayOfMonth
        }
        TimeType.YEARLY -> {
            c[Calendar.DAY_OF_YEAR] = LocalDate.parse(item.timeInfo.date).dayOfYear
        }
    }

    println(SimpleDateFormat.getDateTimeInstance().format(c.timeInMillis))
    println(c.timeInMillis -  System.currentTimeMillis())

    manager.enqueueUniquePeriodicWork(
        item.id,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<TextWorker>(time.first, time.second)
            .setInitialDelay(c.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            //.setInitialDelay(5000, TimeUnit.MILLISECONDS)
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

        item?.numbers?.forEach { applicationContext.sendSMS(it, item.text) }

        return Result.success()
    }

}

//sent sms
fun Context.sendSMS(phoneNumber: String, message: String) {
    val SENT = "SMS_SENT"
    val DELIVERED = "SMS_DELIVERED"
    val sentPI = PendingIntent.getBroadcast(this, 0, Intent(SENT), PendingIntent.FLAG_IMMUTABLE)
    val deliveredPI = PendingIntent.getBroadcast(this, 0, Intent(DELIVERED), PendingIntent.FLAG_IMMUTABLE)

    // ---when the SMS has been sent---
    registerReceiver(object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent?) {
            when (resultCode) {
                Activity.RESULT_OK -> Toast.makeText(
                    arg0, "SMS sent",
                    Toast.LENGTH_SHORT
                ).show()
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Toast.makeText(
                    arg0, "Generic failure",
                    Toast.LENGTH_SHORT
                ).show()
                SmsManager.RESULT_ERROR_NO_SERVICE -> Toast.makeText(
                    arg0, "No service",
                    Toast.LENGTH_SHORT
                ).show()
                SmsManager.RESULT_ERROR_NULL_PDU -> Toast.makeText(
                    arg0, "Null PDU",
                    Toast.LENGTH_SHORT
                ).show()
                SmsManager.RESULT_ERROR_RADIO_OFF -> Toast.makeText(
                    arg0, "Radio off",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }, IntentFilter(SENT))

    // ---when the SMS has been delivered---
    registerReceiver(object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent?) {
            when (resultCode) {
                Activity.RESULT_OK -> Toast.makeText(
                    arg0, "SMS delivered",
                    Toast.LENGTH_SHORT
                ).show()
                Activity.RESULT_CANCELED -> Toast.makeText(
                    arg0, "SMS not delivered",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }, IntentFilter(DELIVERED))
    val sms: SmsManager = SmsManager.getDefault()
    sms.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
}