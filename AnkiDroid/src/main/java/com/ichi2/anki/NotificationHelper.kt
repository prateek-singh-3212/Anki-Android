/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.ichi2.anki.worker.NotificationWorkManager
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import java.util.concurrent.TimeUnit

/**
 * Helper class for Notification. Manages all the notification for AnkiDroid.
 * */
class NotificationHelper(val context: Context) {

    private val TAG = "NOTIFICATION_WORKER"

    /**
     * Adds a daily recurring notification for the provided deck to the notification schedule.
     * @param did Unique identifier for deck.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * */
    fun scheduledDeckNotification(
        did: Long,
        hourOfDay: Int,
        minutesOfHour: Int,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            Timber.d("CreateScheduledDeckNotification-> did: $did hour: $hourOfDay min: $minutesOfHour")
            val notificationDatastore = NotificationDatastore.getInstance(context)

            // Fetch all the time deck data.
            val timeDeckData: AllTimeAndDecksMap =
                notificationDatastore.getTimeDeckData()
                    ?: AllTimeAndDecksMap()

            // Calculate the work type and schedule time
            val notificationData = computeNotificationTime(hourOfDay, minutesOfHour, timeDeckData)

            // Add schedule time in deck time data.
            timeDeckData.append(did, notificationData.scheduleTime)

            saveScheduledNotification(
                notificationDatastore,
                timeDeckData,
                notificationData.notificationWorkType,
            )
        }
    }

    /**
     * Calculates the notification and type of work that needs to be done to trigger notification.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * @param timeDeckData allDeckTimeData map to compute work type
     * @return pair of schedule time in long and work type [NotificationWorkType]
     * */
    private fun computeNotificationTime(
        hourOfDay: Int,
        minutesOfHour: Int,
        timeDeckData: AllTimeAndDecksMap
    ): NotificationData {

        val currentTimeMS = TimeManager.time.intTimeMS()

        // Set the calender for schedule time. It will return the time according to user time zone.
        val calendar = TimeManager.time.calendar().apply {
            this.set(HOUR_OF_DAY, hourOfDay)
            this.set(MINUTE, minutesOfHour)
        }

        // Set current appropriate time
        val scheduleTimeMS = calendar.timeInMillis

        if (scheduleTimeMS < currentTimeMS) {
            Timber.d("Scheduled time is gone for today")
            // Scheduled time is gone for today.Add one day in current schedule time as calendar is set for today
            // Add time in time deck map. No need to reschedule work manager.
            return NotificationData(
                scheduleTimeMS + NotificationWorkManager.ONE_DAY_MS,
                NotificationWorkType.SAVE
            )
        } else if (timeDeckData.size == 0) {
            Timber.d("Creating new time deck data ")
            // Scheduled time will come today only. But No previous time deck data exist creating new data.
            // Recalculating the schedule time because new deck notification might come earlier.
            return NotificationData(scheduleTimeMS, NotificationWorkType.SAVE_AND_RESCHEDULE)
        } else if (scheduleTimeMS < timeDeckData.firstKey().toLong()) {
            Timber.d("Scheduled time will come today only. And it will come before the current schedule deck.")
            return NotificationData(scheduleTimeMS, NotificationWorkType.SAVE_AND_RESCHEDULE)
        } else {
            Timber.d("Scheduled time will come today only. And it will come after the current schedule deck.")
            return NotificationData(scheduleTimeMS, NotificationWorkType.SAVE)
        }
    }

    /**
     * Sets the schedule notification according to [NotificationWorkType].
     * */
    private suspend inline fun saveScheduledNotification(
        notificationDatastore: NotificationDatastore,
        allTimeAndDecksMap: AllTimeAndDecksMap,
        notificationWorkType: NotificationWorkType,
    ) {
        // Save the data in data store
        notificationDatastore.setTimeDeckData(allTimeAndDecksMap)

        // Rescheduling work manager.
        if (notificationWorkType == NotificationWorkType.SAVE_AND_RESCHEDULE) {
            // Replacing old work manager with new one. (initialDiff = currentTime - nextTriggerTime)
            val nextTriggerTime = NotificationWorkManager.getTriggerTime(allTimeAndDecksMap)
            val initialDiff = nextTriggerTime - TimeManager.time.intTimeMS()
            Timber.d("Next trigger time $nextTriggerTime")
            startNotificationWorkManager(initialDiff, true)
        }
    }

    /**
     * Adds the did and schedule time in [AllTimeAndDecksMap]
     * */
    private fun AllTimeAndDecksMap.append(
        did: Long,
        scheduleTimeMS: Long,
    ): AllTimeAndDecksMap {
        // Adding new data to map
        if (this.containsKey(scheduleTimeMS.toString())) {
            this[scheduleTimeMS.toString()]!!.add(did)
        } else {
            this[scheduleTimeMS.toString()] = mutableListOf(did)
        }
        return this
    }

    /**
     * Calibrates the notification time. i.e It recreates the deck time Map. Generally used when user time zone changes.
     * @param currentTimeMS Current time in milli second for a particular timezone
     * @param timeDiff Difference of timezone between current timezone and previous timezone.
     * */
    fun calibrateNotificationTime(
        currentTimeMS: Long,
        timeDiff: Long
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            Timber.d("Calibrating the time deck data... currTime: $currentTimeMS timeDiff: $timeDiff")
            val notificationDatastore = NotificationDatastore.getInstance(context)

            // Fetch all the time deck data.
            val oldTimeDeckData: AllTimeAndDecksMap =
                notificationDatastore.getTimeDeckData()
                    ?: AllTimeAndDecksMap()

            // Creating new time deck data
            val newTimeDeckData = AllTimeAndDecksMap()

            // Filtering all deck ids form the map
            val listOfDeck = oldTimeDeckData.values.flatten()
            Timber.d("List of all deck %s".format(listOfDeck.toString()))

            // Compute new sched timing for all deck ids.
            for (element in 0..listOfDeck.size) {
                val deckData = notificationDatastore.getDeckSchedData(listOfDeck[element])
                    ?: continue
                val notificationData = computeNotificationTime(
                    deckData.schedHour,
                    deckData.schedMinutes,
                    newTimeDeckData
                )
                newTimeDeckData.append(deckData.did, notificationData.scheduleTime)
            }

            // Save and reschedule work manager.
            saveScheduledNotification(
                notificationDatastore,
                newTimeDeckData,
                NotificationWorkType.SAVE_AND_RESCHEDULE
            )
        }
    }

    /**
     * Start the new notification Work Manager.
     * @param initialDelay delay after work manager should start.
     * @param force if true then it will destroy the existing work manager and recreates the new one.
     * */
    fun startNotificationWorkManager(initialDelay: Long, force: Boolean) {
        Timber.d("Starting work manager with initial delay $initialDelay force: $force")

        // Create a One Time Work Request with initial delay.
        val deckMetaDataWorker = OneTimeWorkRequest.Builder(
            NotificationWorkManager::class.java,
        )
            .addTag(TAG)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        val workPolicy = if (force) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        // Register the periodic work manager.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                TAG,
                workPolicy,
                deckMetaDataWorker
            )
    }

    /**
     * Cancels the previously scheduled notification or Worker which triggers the notification.
     * */
    fun cancelScheduledDeckNotification() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /**
     * Triggers the notification immediately.
     * It will check internally that notification is enabled or not
     * @param id Notification id
     * @param notification Notification which should be displayed.
     *        Build Notification using [NotificationHelper.buildNotification]
     * */
    fun triggerNotificationNow(id: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Check notification is enabled or not.
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            Timber.v("Notifications disabled, returning")
            return
        }

        notificationManager.notify(id, notification)
    }

    /**
     * Builds the notification that is going to trigger.
     * @param notificationChannel Channel on which notification should trigger.
     * @param title Title of notification.
     * @param body Text message for Body of Notification.
     * @param pendingIntent Activity which need to open on notification tap.
     * */
    fun buildNotification(
        notificationChannel: NotificationChannels.Channel,
        title: String,
        body: String,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(
        context,
        NotificationChannels.getId(notificationChannel)
    )
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setColor(ContextCompat.getColor(context, R.color.material_light_blue_700))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    /**
     * Work that needs to be so that notification should trigger.
     * SAVE: It only saves the data in notification datastore.
     * SAVE_AND_RESCHEDULE: Saves the data and reschedule the notification work manager.
     * See [saveScheduledNotification]
     * */
    private enum class NotificationWorkType {
        SAVE,
        SAVE_AND_RESCHEDULE,
    }

    /**
     * Data of notification that need to be scheduled.
     * */
    private data class NotificationData(
        val scheduleTime: Long,
        val notificationWorkType: NotificationWorkType
    )
}
