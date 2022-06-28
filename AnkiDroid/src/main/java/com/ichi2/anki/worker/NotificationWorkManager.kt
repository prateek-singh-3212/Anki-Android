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
import timber.log.Timber

/**
 * Worker class to collect the data for notification and triggers all the notifications.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class NotificationWorkManager(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    // Lambda to check the collection is not null.
    private val mCollection: (context: Context) -> Collection? = {
        val collection = CollectionHelper.getInstance().getColSafe(it)
        if (collection != null) {
            collection
        } else {
            Timber.d("Unable to access collection.")
            null
        }
    }

    override suspend fun doWork(): Result {

        var date = CollectionHelper.getInstance().getTimeSafe(context).currentDate
        Timber.d("NotificationManagerWorker: Worker status -> started at $date")

        // Checking the type of work manager.
        val workerValue = inputData.getLong(WORKER_TYPE, Long.MIN_VALUE)
        val workerType = Type.values().find { it.typeValue == workerValue }
        if (workerType == null) {
            Timber.d("Worker type not specified. returning")
            return Result.failure()
        }

        // Update the data of Deck Metadata from HERE
        val sched = mCollection(context)?.sched ?: return Result.failure()
        val deckList: List<DeckDueTreeNode> = sched.deckDueList()

        when (workerType) {
            Type.DECK_REMINDER -> {
                val did = inputData.getLong(DECK_ID, Long.MIN_VALUE)
                if (did == Long.MIN_VALUE) {
                    Timber.d("Deck id not specified. returning")
                    return Result.failure()
                }

                val deckDetails = deckList.find { it.did == did }
                if (deckDetails == null) {
                    Timber.d("Invalid deck id, returning")
                    return Result.failure()
                }

                fireDeckNotification(deckDetails)
            }
            Type.ALL_DECK -> {
                fireAllDeckNotification(sched)
            }
        }

        date = CollectionHelper.getInstance().getTimeSafe(context).currentDate
        Timber.d("NotificationManagerWorker: Worker status -> finished at: ${date.time}")

        return Result.success() // Done work successfully...
    }

    private fun fireDeckNotification(did: DeckDueTreeNode) {
        val title = "Don't forget to study today"
        val counts = Counts(did.newCount, did.lrnCount, did.revCount)
        val message = String.format("%d cards due in %s", counts.count(), did.lastDeckNameComponent)

        Timber.d("$title $message ${counts.count()}")
        // TODO: Check the minimum no. of cards to send notification.
        // TODO: Build and fire notification.
    }

    private fun fireAllDeckNotification(sched: AbstractSched) {
        val ALL_DECK_NOTIFICATION_ID = 11
        val notificationHelper = NotificationHelper(context)

        val preferences = AnkiDroidApp.getSharedPrefs(context)
        val minCardsDue = preferences.getString(
            Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION,
            Preferences.PENDING_NOTIFICATIONS_ONLY.toString()
        )!!.toInt()

        // All Decks Notification.
        val totalDueCount = Counts()
        sched.deckDueList().forEach {
            totalDueCount.addLrn(it.lrnCount)
            totalDueCount.addNew(it.newCount)
            totalDueCount.addRev(it.revCount)
        }
        totalDueCount.count()

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
                "Where are you??",
                String().format(
                    "%d cards are waiting for you to review them. It only take %d mins",
                    totalDueCount,
                    eta
                ),
                resultPendingIntent
            )

            notificationHelper.triggerNotificationNow(ALL_DECK_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORKER_TYPE = "WORKER_TYPE"
        const val DECK_ID = "DECK_ID"
    }

    /**
     * Enum to Differentiates between the types of notification
     * Worker will execute the specific task depending on the TYPE of notification.
     * */
    enum class Type(val typeValue: Long) {
        ALL_DECK(200),
        DECK_REMINDER(300),
    }
}
