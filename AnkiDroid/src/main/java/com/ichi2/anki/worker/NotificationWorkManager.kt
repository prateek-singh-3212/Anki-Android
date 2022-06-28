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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ichi2.anki.*
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber

/**
 * Worker class to collect the data for notification and triggers all the notifications.
 * It will calculate the next time of execution after completing the task of current execution i.e Execution time is dynamic.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class NotificationWorkManager(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        Timber.d("NotificationManagerWorker: Worker status -> STARTED")

        // Access the collection.
        val collection = CollectionHelper.getInstance().getColSafe(context)

        // Collect the deck details.
        val sched = collection?.sched ?: kotlin.run {
            rescheduleNextWorker()
            return Result.failure()
        }

        val deckList = sched.deckDueTree().map { it.value }

        processSingleDeckNotifications(deckList)
        fireAllDeckNotification(deckList)

        rescheduleNextWorker()
        Timber.d("NotificationManagerWorker: Worker status -> FINISHED")
        return Result.success() // Done work successfully...
    }

    private suspend fun processSingleDeckNotifications(deckList: List<DeckDueTreeNode>) {
        Timber.d("Processing all deck notification...")
        val currentTime = TimeManager.time.currentDate.time

        // Collect all the notification data which need to triggered.
        val timeDeckData = NotificationDatastore.getInstance(context).getTimeDeckData()
        if (timeDeckData.isNullOrEmpty()) {
            Timber.d("No time deck data found, returning")
            return
        }

        // Sorted all the decks whose notification time is less than current time.
        val filteredTimeDeckData = timeDeckData.filterTo(HashMap()) { it.key.toLong() <= currentTime }

        // Creating list of all decks whose notification is going to trigger
        val deckIdsToTrigger = filteredTimeDeckData
            .flatMap { it.value }
            .toHashSet()

        // Triggering the deck notification
        val deckNotificationData = deckList.filter { deckIdsToTrigger.contains(it.did) }
        for (deck in deckNotificationData) {
            fireDeckNotification(deck)
            deckIdsToTrigger.remove(deck.did)
        }

        // Decks may have been deleted
        if (deckIdsToTrigger.size != 0) {
            Timber.d("Decks deleted")
            // TODO: Cancel deck notification when user deletes a particular deck to handle case [user deletes a deck between the notification being added and executed]
        }

        // Updating time for next trigger.
        filteredTimeDeckData.forEach {
            timeDeckData.remove(it.key)
            timeDeckData[it.key + ONE_DAY_MS] = it.value
        }

        // Saving the new Time Deck Data.
        NotificationDatastore.getInstance(context).setTimeDeckData(timeDeckData)
    }

    /**
     * Fire the notification for [deck] if needed.
     * We consider it is needed if [deck] or any of its subdecks have cards, we trigger notification only for [deck] itself in any case
     */
    private fun fireDeckNotification(deck: DeckDueTreeNode) {
        Timber.d("Firing deck notification for did -> %d", deck.did)
        val title = context.getString(R.string.reminder_title)
        val counts =
            Counts(deck.newCount, deck.lrnCount, deck.revCount)
        val message = context.resources.getQuantityString(
            R.plurals.reminder_text,
            counts.count(),
            deck.fullDeckName
        )

        // TODO: Remove log used for now to remove compilation error.
        Timber.d("$title $counts $message")
        // TODO: Check the minimum no. of cards to send notification.
        // TODO: Build and fire notification.
    }

    /**
     * Fire the notification for due cards in all decks including subdecks.
     * */
    private fun fireAllDeckNotification(deckList: List<DeckDueTreeNode>) {
        Timber.d("Firing all deck notification.")
        val preferences = AnkiDroidApp.getSharedPrefs(context)
        val minCardsDue = preferences.getInt(
            Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION,
            Preferences.PENDING_NOTIFICATIONS_ONLY
        )

        // All Decks Notification.
        val totalDueCount = Counts()
        deckList.forEach {
            totalDueCount.addLrn(it.lrnCount)
            totalDueCount.addNew(it.newCount)
            totalDueCount.addRev(it.revCount)
        }

        if (totalDueCount.count() < minCardsDue) {
            // Due card limit is higher.
            return
        }
        // TODO: Build & Fire all deck notification.
    }

    /**
     * Reschedule Next Worker which need to be done irrespective of worker failed or succeed.
     * */
    private suspend fun rescheduleNextWorker() {
        Timber.d("Task Completed. Rescheduling...")
        val notificationDatastore = NotificationDatastore.getInstance(context)
        val timeAndDeckData = notificationDatastore.getTimeDeckData() ?: NotificationTodo()

        val nextTriggerTime = getTriggerTime(timeAndDeckData)
        val initialDiff = TimeManager.time.intTimeMS() - nextTriggerTime

        Timber.d("Next trigger time $nextTriggerTime")
//        TODO: Start work manager with initial delay though Notification Helper.
    }

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000
        const val ONE_DAY_MS = ONE_HOUR_MS * 24

        /**
         * Calculates the next time to trigger the Notification WorkManager.
         * it's not the next time a notification should be shown, but a time at most in one hour to check all deck notification.
         * @param allTimeDeckData Mapping from time to the list of decks whose notification should be sent at this time. [NotificationTodo]
         * @return next trigger time in Milli Seconds.
         * */
        fun getTriggerTime(allTimeDeckData: NotificationTodo): Long {
            val currentTime = TimeManager.time.currentDate.time

            val nextTimeKey = allTimeDeckData.keys.firstOrNull { it.toLong() >= currentTime }
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
