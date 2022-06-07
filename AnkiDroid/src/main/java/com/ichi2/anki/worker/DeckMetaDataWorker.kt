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
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.model.DeckMetaDataModel
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.DeckDueTreeNode
import timber.log.Timber

/**
 * This Worker is responsible to Collect all the metadata and store it in preference Datastore.
 * Technically it refreshes the Deck Meta Data. Whenever this Worker is called
 * then I will collect the new data of decks.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class DeckMetaDataWorker(val context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters) {

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
        Timber.d("DeckMetaData: Worker status -> started at $date")

        // Update the data of Deck Metadata from HERE
        val sched = mCollection(context)?.sched ?: return Result.failure()
        val deckList: List<DeckDueTreeNode> = sched.deckDueList()

        deckList.forEach {
            val count = Counts(it.newCount, it.lrnCount, it.revCount)
            val data = DeckMetaDataModel(
                it.did,
                it.fullDeckName,
                count,
                sched.eta(count, false)
            )
        }

        return Result.success() // Done work successfully...
    }

    companion object {
        const val DECK_META_WORKER = "DeckMetaData"
    }
}
