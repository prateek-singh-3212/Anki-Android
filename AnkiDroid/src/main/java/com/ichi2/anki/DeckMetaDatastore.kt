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
import com.ichi2.anki.model.DeckMetaDataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.NumberFormatException

/**
 * DataStore to store the deck meta data.
 * It is not Type safe double check the datatype of value that needs to be fetched.
 * WARNING: This class is singleton.
 * */
class DeckMetaDatastore private constructor(val context: Context) {

    /**
     * Use this to store data in preference datastore.
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
     * @param key The Key of value. Used in fetching the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE STRING)</b>.
     * */
    suspend fun putStringAsync(key: String, value: String) {
        val dataStoreKey = stringPreferencesKey(key)
        context.deckMetaDataStore.edit { metaData ->
            metaData[dataStoreKey] = value
        }
    }

    /**
     * Use this to store data in preference datastore.
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
     * Use this to fetch string from preference datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value that needs to be fetched <b>(VALUE WILL BE STRING)</b>.
     * */
    suspend fun getString(key: String, default: String): String {
        val dataStoreKey = stringPreferencesKey(key)
        return context.deckMetaDataStore.data.map { metaData ->
            metaData[dataStoreKey]
        }.firstOrNull() ?: default
    }

    /**
     * Use this to store int in preference datastore.
     * It stores the data asynchronously. It will create Coroutine Scope Internally.
     * @param key The Key of value. Created while storing the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE INTEGER)</b>.
     * */
    suspend fun putIntAsync(key: String, value: Int) {
        val dataStoreKey = intPreferencesKey(key)
        context.deckMetaDataStore.edit { metaDataEditor ->
            metaDataEditor[dataStoreKey] = value
        }
    }

    /**
     * Use this to store int in preference datastore.
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
     * Use this to fetch integer from preference datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value that needs to be fetched <b>(VALUE WILL BE INTEGER)</b>.
     * */
    suspend fun getInt(key: String, default: Int): Int {
        val dataStoreKey = intPreferencesKey(key)
        return context.deckMetaDataStore.data.map { metaData ->
            metaData[dataStoreKey]
        }.firstOrNull() ?: default
    }

    /**
     * Use this to store meta data in preference datastore.
     * @param key The Key of deck whose data you want to fetch. (deck id is the for deck)
     * @param value Object of Metadata Model
     * */
    suspend fun setMetaData(key: String, value: DeckMetaDataModel) {
        val dataStoreKey = stringPreferencesKey(key)

        context.deckMetaDataStore.edit { metaData ->
            metaData[dataStoreKey] = value.toJSON()
        }
    }

    /**
     * Use this to fetch meta data from preference datastore if Deck ID (did) is known.
     * @prams The Key of deck whose data you want to fetch. (deck id is the for deck)
     * @return Object of Metadata Model.
     * */
    suspend fun getMetaData(key: Preferences.Key<*>): DeckMetaDataModel? {
        if (!key.name.isLong()) {
            // Invalid key of deck
            return null
        }

        val jsonData = context.deckMetaDataStore.data.map { metaData ->
            metaData[key] as String
        }.firstOrNull()

        Timber.d(jsonData)

        return DeckMetaDataModel.fromJson(jsonData!!)
    }

    /**
     * Use this to fetch all meta data from preference datastore.
     * @return List of all Meta Data Object.
     * */
    suspend fun getAllMetaData(): List<DeckMetaDataModel> {
        return readAllKeys().mapNotNull { getMetaData(it) }
    }

    /**
     * Used to fetch all the keys from preference.
     * */
    private suspend fun readAllKeys(): Set<Preferences.Key<*>> {
        val keys = context.deckMetaDataStore.data
            .map { metaData ->
                metaData.asMap().keys
            }
        return keys.first()
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

    companion object {
        private var INSTANCE: DeckMetaDatastore? = null
        private val Context.deckMetaDataStore: DataStore<Preferences> by preferencesDataStore("DeckMetaData")
        /**
         * Their should be only 1 instance of preferencesDataStore. so using the singleton pattern here.
         * */
        fun getInstance(context: Context): DeckMetaDatastore {
            return if (INSTANCE == null) {
                INSTANCE = DeckMetaDatastore(context)
                INSTANCE!!
            } else {
                INSTANCE!!
            }
        }
    }
}
