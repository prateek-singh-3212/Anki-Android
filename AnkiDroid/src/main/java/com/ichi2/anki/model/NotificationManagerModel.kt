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

package com.ichi2.anki.model

import com.ichi2.utils.JSONObject

data class NotificationManagerModel(
    val deckName: String,
    val did: Long,
    val schedTime: Long,
    val isScheduled: Boolean,
    val dueCards: Int,
    val eta: Int
) {

    fun toJSON(): String {
        val jsonObject = JSONObject()
        jsonObject.put("deckName", deckName)
        jsonObject.put("did", did)
        jsonObject.put("schedTime", schedTime)
        jsonObject.put("isScheduled", isScheduled)
        jsonObject.put("dueCards", dueCards)
        jsonObject.put("eta", eta)
        return jsonObject.toString()
    }

    companion object {
        fun fromJson(jsonData: String): NotificationManagerModel {
            val jsonObject = JSONObject(jsonData)
            return NotificationManagerModel(
                jsonObject.getString("deckName"),
                jsonObject.getLong("did"),
                jsonObject.getLong("schedTime"),
                jsonObject.getBoolean("isScheduled"),
                jsonObject.getInt("dueCards"),
                jsonObject.getInt("eta"),
            )
        }
    }
}
