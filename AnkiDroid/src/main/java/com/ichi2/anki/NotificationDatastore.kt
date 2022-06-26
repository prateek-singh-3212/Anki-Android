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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.NotificationDatastore.Companion.getInstance
import com.ichi2.anki.model.DeckNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.*

/**
 * Time at which deck notification will trigger. Stores time in millisecond in EPOCH format.
 * */
typealias TimeOfNotification = String

/**
 * A list of DeckIds which is going to trigger at particular time.
 * */
typealias ListOfDeck = MutableList<Long>

/**
 * Store all the time and list of deck ids for a particular time.
 * */
typealias AllTimeAndDecksMap = TreeMap<TimeOfNotification, ListOfDeck>

/**
 * Stores the scheduled notification details
 * This class is singleton use [getInstance]
 * */
class NotificationDatastore private constructor(val context: Context) {

    private val objectMapper = ObjectMapper()

    /**
     * Stores the String in Notification Datastore
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
     * Calling this function guarantees to store value in database.
     * @param key The Key of value. Used in fetching the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE STRING)</b>.
     * */
    suspend fun putStringAsync(key: String, value: String) {
        val dataStoreKey = stringPreferencesKey(key)
        context.notificationDatastore.edit { metaData ->
            metaData[dataStoreKey] = value
        }
    }

    /**
     * Stores the String in Notification Datastore
     * It stores the data synchronously.
     * @param key The Key of value. Used in fetching the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE STRING)</b>.
     * */
    fun putStringSync(key: String, value: String) {
        CoroutineScope(Dispatchers.IO).launch {
            putStringAsync(key, value)
        }
    }

    /**
     * Fetches the String value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value associated to `key` by the last call to [putStringSync], [putStringAsync], or [default] if none
     * */
    suspend fun getString(key: String, default: String): String {
        val dataStoreKey = stringPreferencesKey(key)
        return context.notificationDatastore.data.firstOrNull()?.let {
            it[dataStoreKey]
        } ?: default
    }

    /**
     * Stores the Integer in Notification Datastore
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
     * Calling this function guarantees to store value in database.
     * @param key The Key of value. Created while storing the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE INTEGER)</b>.
     * */
    suspend fun putIntAsync(key: String, value: Int) {
        val dataStoreKey = intPreferencesKey(key)
        context.notificationDatastore.edit { metaDataEditor ->
            metaDataEditor[dataStoreKey] = value
        }
    }

    /**
     * Stores the Integer in Notification Datastore
     * It stores the data synchronously.
     * @param key The Key of value. Created while storing the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE INTEGER)</b>.
     * */
    fun putIntSync(key: String, value: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            putIntAsync(key, value)
        }
    }

    /**
     * Fetches the Integer value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value associated to `key` by the last call to [putIntSync], [putIntAsync], or [default] if none
     * */
    suspend fun getInt(key: String, default: Int): Int {
        val dataStoreKey = intPreferencesKey(key)
        return context.notificationDatastore.data.map { metaData ->
            metaData[dataStoreKey]
        }.firstOrNull() ?: default
    }

    /**
     * Stores the Map of time and deck list to Datastore
     * It stores the data asynchronously.
     * */
    suspend fun setTimeDeckData(data: Map<String, MutableList<Long>>) {
        val dataStoreKey = stringPreferencesKey("TIME_DECK_DATA")
        val jsonObj = JSONObject(data)
        context.notificationDatastore.edit { metaData ->
            metaData[dataStoreKey] = jsonObj.toString()
        }
    }

    /**
     * Fetches the Map of time and respective of deck-id at that time from Datastore.
     * @return Treemap (SORTED) of time and deckList.
     * */
    @Suppress("UNCHECKED_CAST")
    suspend fun getTimeDeckData(): AllTimeAndDecksMap? {
        val datastoreKey = stringPreferencesKey("TIME_DECK_DATA")
        return context.notificationDatastore.data.firstOrNull()?.let {
            try {
                objectMapper.readValue(
                    it[datastoreKey],
                    TreeMap::class.java
                ) as AllTimeAndDecksMap
            } catch (ex: JacksonException) {
                Timber.d(ex.cause)
                null
            }
        }
    }

    /**
     * Stores the details of particular deck scheduling.
     * @return operation successful of not.
     * */
    suspend fun setDeckSchedData(did: Long, data: DeckNotification): Boolean {
        val dataStoreKey = stringPreferencesKey(did.toString())
        return runCatching {
            val json = objectMapper.writeValueAsString(data)
            context.notificationDatastore.edit { metaData ->
                metaData[dataStoreKey] = json
            }
        }.isSuccess
    }

    /**
     * Fetches the details of particular deck scheduling.
     * @return Deck Notification model for particular deck.
     * */
    suspend fun getDeckSchedData(did: Long): DeckNotification? {
        val datastoreKey = stringPreferencesKey(did.toString())
        return context.notificationDatastore.data.map {
            objectMapper.readValue(
                it[datastoreKey],
                DeckNotification::class.java
            )
        }.catch { ex ->
            // Let the exception throw
            CrashReportService.sendExceptionReport(ex, "Notification Datastore-getDeckSchedData", "Exception Occurred during fetching of data.")
            throw Exception("Unable to find schedule data of given deck id: $did")
        }.firstOrNull()
    }

    companion object {
        @Volatile
        private var INSTANCE: NotificationDatastore? = null
        private val Context.notificationDatastore: DataStore<Preferences> by preferencesDataStore("NotificationDatastore")

        /**
         * There should be only 1 instance of preferencesDataStore. It is THREAD SAFE.
         * */
        fun getInstance(context: Context): NotificationDatastore {
            return INSTANCE ?: synchronized(this) {
                val newInstance = INSTANCE ?: NotificationDatastore(context).also {
                    INSTANCE = it
                }
                newInstance
            }
        }
    }
}
