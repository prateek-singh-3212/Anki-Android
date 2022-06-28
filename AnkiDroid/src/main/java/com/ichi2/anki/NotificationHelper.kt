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
import java.util.*
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import java.util.concurrent.TimeUnit

/**
 * Helper class for Notification. Manages all the notification for AnkiDroid.
 * */
class NotificationHelper(val context: Context) {

    private val TAG = "NOTIFICATION_WORKER"

    /**
     * Create the Scheduled Deck Notification using WorkManager.
     * @param did Unique identifier for deck.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * */
    fun createScheduledDeckNotification(
        did: Long,
        hourOfDay: Int,
        minutesOfHour: Int,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val notificationDatastore = NotificationDatastore.getInstance(context)
            val currentTimeMS = TimeManager.time.intTimeMS()

            // Set the calender for schedule time.
            val calendar = TimeManager.time.calendar()
            calendar.set(HOUR_OF_DAY, hourOfDay)
            calendar.set(MINUTE, minutesOfHour)

            // Set current appropriate time
            var scheduleTimeMS = calendar.time.time

            // Fetch all the time deck data.
            val timeDeckData: allTimeAndDecksMap =
                notificationDatastore.getTimeDeckData()
                    ?: allTimeAndDecksMap()

            if (currentTimeMS < scheduleTimeMS) {
                // Scheduled time is gone for today. add one day in current schedule time as calendar is set for today
                scheduleTimeMS += 86400000

                setScheduledNotification(
                    notificationDatastore,
                    timeDeckData,
                    did,
                    Type.NOTIFICATION_ELAPSED,
                    scheduleTimeMS
                )
            } else if (scheduleTimeMS < timeDeckData.firstKey().toLong()) {
                // Scheduled time will come today only. And it will come before the current schedule deck.
                setScheduledNotification(
                    notificationDatastore,
                    timeDeckData,
                    did,
                    Type.BEFORE_CURRENT_NOTIFICATION,
                    scheduleTimeMS
                )
            } else {
                // Scheduled time will come today only. And it will come after the current schedule deck.
                setScheduledNotification(
                    notificationDatastore,
                    timeDeckData,
                    did,
                    Type.AFTER_CURRENT_NOTIFICATION,
                    scheduleTimeMS
                )
            }
        }
    }

    /**
     * Sets the schedule notification according to TYPE.
     * */
    private suspend fun setScheduledNotification(
        notificationDatastore: NotificationDatastore,
        allTimeAndDecksMap: allTimeAndDecksMap,
        did: Long,
        notificationType: Type,
        scheduleTimeMS: Long,
    ) {
        val newMap: allTimeAndDecksMap = allTimeAndDecksMap.append(did, scheduleTimeMS)

        when (notificationType) {
            Type.NOTIFICATION_ELAPSED -> {
                // Only add time in time deck map. No need to reschedule work manager.
                notificationDatastore.setTimeDeckData(newMap)
            }
            Type.BEFORE_CURRENT_NOTIFICATION -> {
                // Updating data in map
                notificationDatastore.setTimeDeckData(newMap)

                // Replacing old work manager with new one. (initialDiff = currentTime - scheduleTime)
                val initialDiff = TimeManager.time.intTimeMS() - scheduleTimeMS
                startNotificationWorkManager(initialDiff, true)
            }
            Type.AFTER_CURRENT_NOTIFICATION -> {
                // Updating data in map
                notificationDatastore.setTimeDeckData(newMap)
            }
        }
    }

    /**
     * Updates the map data.
     * */
    private fun allTimeAndDecksMap.append(
        did: Long,
        scheduleTimeMS: Long,
    ): allTimeAndDecksMap {
        // Adding new data to map
        if (this.containsKey(scheduleTimeMS.toString())) {
            this[scheduleTimeMS.toString()]!!.add(did)
        } else {
            this[scheduleTimeMS.toString()] = mutableListOf(did)
        }
        return this
    }

    /**
     * Start the new notification Work Manager.
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
     * @param tag Unique identifier for worker.
     *        Note: For [NotificationWorkManager.Type.DECK_REMINDER] tag should be Deck id
     * */
    fun cancelScheduledDeckNotification() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /**
     * Triggers the notification immediately.
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
     * Builds the notification.
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
    ): Notification {
        return NotificationCompat.Builder(
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
    }

    private enum class Type {
        NOTIFICATION_ELAPSED,
        BEFORE_CURRENT_NOTIFICATION,
        AFTER_CURRENT_NOTIFICATION
    }
}
