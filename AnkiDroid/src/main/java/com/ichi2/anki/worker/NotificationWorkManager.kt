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

package com.ichi2.anki.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ichi2.anki.*
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.AbstractSched
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber

/**
 * Worker class to collect the data for notification and triggers all the notifications.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class NotificationWorkManager(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    // Lambda to check the collection is not null.
    private val collection: (context: Context) -> Collection? = {
        val collection = CollectionHelper.getInstance().getColSafe(it)
        if (collection != null) {
            collection
        } else {
            Timber.d("Unable to access collection.")
            null
        }
    }

    override suspend fun doWork(): Result {
        Timber.d("NotificationManagerWorker: Worker status -> STARTED")
        val currentTime = TimeManager.time.currentDate.time

        // Update the data of Deck Metadata from HERE
        val sched = collection(context)?.sched ?: return Result.failure()
        val deckList: List<DeckDueTreeNode> = sched.deckDueTree().map { it.value }

        // Collect all the notification data which need to triggered.
        val timeDeckData = NotificationDatastore.getInstance(context).getTimeDeckData()
        if (timeDeckData.isNullOrEmpty()) {
            Timber.d("No time deck data found, returning")
            fireAllDeckNotification(sched, deckList)

            // Scheduling next work manager. Next hour.
            NotificationHelper(context).startNotificationWorkManager(initialDelay = ONE_HOUR_MS.toLong(), force = true)
            return Result.success()
        }

        // Sorted all the decks whose notification time is less than current time.
        val filteredTimeDeckData = timeDeckData.filter { it.key.toLong() <= currentTime }

        // Creating list of all decks whose notification is going to trigger
        val deckIdsToTrigger = filteredTimeDeckData.flatMap { it.value }

        // TODO: Cancel deck notification when user deletes a particular deck to handle case [user deletes a deck between the notification being added and executed]
        deckIdsToTrigger.forEach { deckId ->
            val did = deckList.firstOrNull { it.did == deckId }
            // Handles case when deck is deleted.
            if (did != null) {
                fireDeckNotification(did)
            }
        }

        fireAllDeckNotification(sched, deckList)

        // Schedule next alarm
        val diff = getTriggerTime(timeDeckData) - TimeManager.time.currentDate.time
        NotificationHelper(context).startNotificationWorkManager(initialDelay = diff, force = true)

        Timber.d("NotificationManagerWorker: Worker status -> FINISHED")
        return Result.success() // Done work successfully...
    }

    private fun fireDeckNotification(deckDueTreeNode: DeckDueTreeNode) {
        Timber.d("Firing deck notification for did -> ${deckDueTreeNode.did}")
        val title = context.getString(R.string.reminder_title)
        val counts = Counts(deckDueTreeNode.newCount, deckDueTreeNode.lrnCount, deckDueTreeNode.revCount)
        val message = context.resources.getQuantityString(
            R.plurals.reminder_text,
            counts.count(),
            deckDueTreeNode.fullDeckName
        )
        // FIXME: Used For now to remove gradle error
        Timber.d("$title $message")
        // TODO: Check the minimum no. of cards to send notification.
        // TODO: Build and fire notification.
    }

    private fun fireAllDeckNotification(sched: AbstractSched, deckList: List<DeckDueTreeNode>) {
        Timber.d("Firing all deck notification.")
        val ALL_DECK_NOTIFICATION_ID = 11
        val notificationHelper = NotificationHelper(context)

        val preferences = AnkiDroidApp.getSharedPrefs(context)
        val minCardsDue = preferences.getString(
            Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION,
            Preferences.PENDING_NOTIFICATIONS_ONLY.toString()
        )!!.toInt()

        // All Decks Notification.
        val totalDueCount = Counts()
        deckList.forEach {
            totalDueCount.addLrn(it.lrnCount)
            totalDueCount.addNew(it.newCount)
            totalDueCount.addRev(it.revCount)
        }

        // Creates an explicit intent for an DeckPiker Activity.
        val resultIntent = Intent(context, DeckPicker::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val resultPendingIntent = CompatHelper.compat.getImmutableActivityIntent(
            context, 0, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val eta = sched.eta(totalDueCount)

        if (totalDueCount.count() > minCardsDue) {
            // Build the notification
            val notification = notificationHelper.buildNotification(
                NotificationChannels.Channel.GENERAL,
                context.getString(R.string.all_deck_notification_new_title),
                String().format(
                    context.getString(R.string.all_deck_notification_new_message),
                    totalDueCount,
                    eta
                ),
                resultPendingIntent
            )

            notificationHelper.triggerNotificationNow(ALL_DECK_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ONE_HOUR_MS = 3600000
        const val ONE_DAY_MS = 86400000

        fun getTriggerTime(data: allTimeAndDecksMap): Long {
            val currentTime = TimeManager.time.currentDate.time

            val nextTimeKey = data.keys.firstOrNull { it.toLong() >= currentTime }
                ?: return currentTime + ONE_HOUR_MS // No deck for complete day. Restarting after 1 hour for all deck notification

            val timeDiff = nextTimeKey.toLong() - currentTime

            return if (timeDiff < ONE_HOUR_MS) {
                nextTimeKey.toLong()
            } else {
                // No deck is scheduled in next hour. Restart service after 1 hour.
                currentTime + ONE_HOUR_MS
            }
        }
    }
}
