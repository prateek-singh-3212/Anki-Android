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
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ichi2.anki.worker.NotificationWorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Helper class for Notification. Manages all the notification for AnkiDroid.
 * */
class NotificationHelper(val context: Context) {

    /**
     * Create the Scheduled Notification using WorkManager.
     * @param tag Unique identifier for worker.
     *        Note: For [NotificationWorkManager.Type.DECK_REMINDER] tag should be Deck id
     * @param workManagerType Specify the type of work Manager see: [NotificationWorkManager.Type]
     * @param repeatInterval Interval of time after which notification should repeat.
     * @param initialDelay Initial amount time after which worker should start. DEFAULT value = 0 i.e immediately
     * */
    fun createScheduledNotification(
        tag: String,
        @NonNull workManagerType: NotificationWorkManager.Type,
        @NonNull repeatInterval: Long,
        @NonNull repeatIntervalTimeUnit: TimeUnit,
        initialDelay: Long = 0,
        initialDelayTimeUnit: TimeUnit = TimeUnit.SECONDS
    ) {
        // Set the input data for work manager according to the Notification Work Manager Type.
        val data = if (workManagerType == NotificationWorkManager.Type.DECK_REMINDER) {
            workDataOf(
                NotificationWorkManager.WORKER_TYPE to workManagerType.typeValue,
                NotificationWorkManager.DECK_ID to tag.toLong()
            )
        } else {
            workDataOf(
                NotificationWorkManager.WORKER_TYPE to workManagerType.typeValue,
            )
        }

        // Create a Periodic Work Request which is used to call the work manager periodically
        val deckMetaDataWorker = PeriodicWorkRequest.Builder(
            NotificationWorkManager::class.java,
            repeatInterval,
            repeatIntervalTimeUnit
        )
            .addTag(tag)
            .setInitialDelay(initialDelay, initialDelayTimeUnit)
            .setInputData(data)
            .build()

        // Register the periodic work manager.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                tag,
                ExistingPeriodicWorkPolicy.REPLACE,
                deckMetaDataWorker
            )

        NotificationWorkManager.setupNewWorker(context, workManagerType, tag)
    }

    /**
     * Cancels the previously scheduled notification or Worker which triggers the notification.
     * @param tag Unique identifier for worker.
     *        Note: For [NotificationWorkManager.Type.DECK_REMINDER] tag should be Deck id
     * */
    fun cancelScheduledNotification(tag: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
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
}
