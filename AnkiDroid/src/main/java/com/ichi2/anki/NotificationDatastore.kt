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
import com.ichi2.anki.model.NotificationManagerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.NumberFormatException

/**
 * Stores the scheduled notification details
 * It is not Type safe double check the datatype of value that needs to be fetched.
 * WARNING: This class is singleton.
 * */
class NotificationDatastore private constructor(val context: Context) {

    /**
     * Stores the String in Notification Datastore
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
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
        CoroutineScope(Dispatchers.Default).launch {
            putStringAsync(key, value)
        }
    }

    /**
     * Fetches the String value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value that needs to be fetched <b>(VALUE WILL BE STRING)</b>.
     * */
    suspend fun getString(key: String, default: String): String {
        val dataStoreKey = stringPreferencesKey(key)
        return context.notificationDatastore.data.map { metaData ->
            metaData[dataStoreKey]
        }.firstOrNull() ?: default
    }

    /**
     * Stores the Integer in Notification Datastore
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
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
        CoroutineScope(Dispatchers.Default).launch {
            putIntAsync(key, value)
        }
    }

    /**
     * Fetches the Integer value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value that needs to be fetched <b>(VALUE WILL BE INTEGER)</b>.
     * */
    suspend fun getInt(key: String, default: Int): Int {
        val dataStoreKey = intPreferencesKey(key)
        return context.notificationDatastore.data.map { metaData ->
            metaData[dataStoreKey]
        }.firstOrNull() ?: default
    }

    /**
     * Stores the NotificationManager in Notification Datastore
     * @param key The Key of deck whose data you want to fetch. (deck id is the for deck)
     * @param value Object of Metadata Model
     * */
    suspend fun setNotificationData(key: String, value: NotificationManagerModel) {
        val dataStoreKey = stringPreferencesKey(key)

        context.notificationDatastore.edit { metaData ->
            metaData[dataStoreKey] = value.toJSON()
        }
    }

    /**
     * Fetches the NotificationManagerModel value from Datastore.
     * @prams The Key of deck whose data you want to fetch. (deck id is the for deck)
     * @return Object of Metadata Model.
     * */
    suspend fun getNotificationData(key: Preferences.Key<*>): NotificationManagerModel? {
        if (!key.name.isLong()) {
            // Invalid key of deck
            return null
        }

        val jsonData = context.notificationDatastore.data.map { notificationData ->
            notificationData[key] as String
        }.firstOrNull()

        Timber.d(jsonData)

        return NotificationManagerModel.fromJson(jsonData!!)
    }

    companion object {
        private var INSTANCE: NotificationDatastore? = null
        private val Context.notificationDatastore: DataStore<Preferences> by preferencesDataStore("NotificationDatastore")
        /**
         * Their should be only 1 instance of preferencesDataStore. so using the singleton pattern here.
         * */
        fun getInstance(context: Context): NotificationDatastore {
            return if (INSTANCE == null) {
                INSTANCE = NotificationDatastore(context)
                INSTANCE!!
            } else {
                INSTANCE!!
            }
        }
    }

    /**
     * This is extension function to check that string is Long or not.
     * In our use case it is used sort deck and any addition data (like WORKER_CREATED, TIMES_FETCHED etc.)
     * */
    private fun String.isLong(): Boolean = try {
        this.toLong()
        true
    } catch (e: NumberFormatException) {
        false
    }
}
