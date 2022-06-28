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
import com.ichi2.libanki.Collection
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

    // Lambda to check the collection is not null.
    private val collection: (context: Context) -> Collection? = {
        CollectionHelper.getInstance().getColSafe(it)
    }

    override suspend fun doWork(): Result {
        Timber.d("NotificationManagerWorker: Worker status -> STARTED")

        // Collect the fresh deck details.
        val sched = collection(context)?.sched ?: return rescheduleNextWorker(Result.failure())
        val deckList: List<DeckDueTreeNode> = sched.deckDueTree().map { it.value }.sortedBy { it.did }

        processSingleDeckNotifications(deckList)
        fireAllDeckNotification(deckList)

        Timber.d("NotificationManagerWorker: Worker status -> FINISHED")
        return rescheduleNextWorker(Result.success()) // Done work successfully...
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
        val filteredTimeDeckData = timeDeckData.filter { it.key.toLong() <= currentTime }

        // Creating list of all decks whose notification is going to trigger
        val deckIdsToTrigger = filteredTimeDeckData.flatMap { it.value }.sorted().toMutableList()

        // Triggering the deck notification
        for (deckDueTreeNode in 0..deckList.size) {
            if (deckIdsToTrigger.isEmpty()) {
                // All deck notification triggered
                break
            }
            val currDeckNode = deckList[deckDueTreeNode]
            if (currDeckNode.did == deckIdsToTrigger[0]) {
                // Deck found. Trigger notification.
                fireDeckNotification(currDeckNode)
                deckIdsToTrigger.removeFirst()
            }
        }

        // If deckIdsToTrigger contains did after firing all deck notification. Then remaining decks are deleted.
        if (deckIdsToTrigger.isNotEmpty()) {
            Timber.d("Decks deleted: Removing $deckIdsToTrigger from list.")
            // TODO: Cancel deck notification when user deletes a particular deck to handle case [user deletes a deck between the notification being added and executed]
        }
    }

    /**
     * Fire the deck notification for a particular deck.
     * */
    private fun fireDeckNotification(deckDueTreeNode: DeckDueTreeNode) {
        Timber.d("Firing deck notification for did -> ${deckDueTreeNode.did}")
        val title = context.getString(R.string.reminder_title)
        val counts =
            Counts(deckDueTreeNode.newCount, deckDueTreeNode.lrnCount, deckDueTreeNode.revCount)
        val message = context.resources.getQuantityString(
            R.plurals.reminder_text,
            counts.count(),
            deckDueTreeNode.fullDeckName
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
     * @param result Result of the worker i.e [Result.failure] or [Result.success]
     * */
    private suspend fun rescheduleNextWorker(result: Result): Result {
        Timber.d("Task Completed. Rescheduling...")
        val notificationDatastore = NotificationDatastore.getInstance(context)
        val timeAndDeckData = notificationDatastore.getTimeDeckData() ?: return result

        val nextTriggerTime = getTriggerTime(timeAndDeckData)
        val initialDiff = TimeManager.time.intTimeMS() - nextTriggerTime

        Timber.d("Next trigger time $initialDiff")
//        TODO: Start work manager with initial delay though Notification Helper.

        return result
    }

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000
        const val ONE_DAY_MS = ONE_HOUR_MS * 24

        /**
         * Calculates the next time to trigger the Notification WorkManager.
         * @param data map of deck trigger time and list of deck at particular time. [AllTimeAndDecksMap]
         * @return next trigger time in Milli Seconds.
         * */
        fun getTriggerTime(data: AllTimeAndDecksMap): Long {
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
