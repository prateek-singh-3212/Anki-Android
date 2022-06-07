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

import com.ichi2.libanki.sched.Counts
import com.ichi2.utils.JSONObject

data class DeckMetaDataModel(
    val did: Long,
    val deckName: String,
    val count: Counts,
    val eta: Int
) {

    fun toJSON(): String {
        val jsonObject = JSONObject()
        jsonObject.put("did", did)
        jsonObject.put("deckName", deckName)
        jsonObject.put("new", count.new)
        jsonObject.put("lrn", count.lrn)
        jsonObject.put("rev", count.rev)
        jsonObject.put("eta", eta)
        return jsonObject.toString()
    }

    companion object {
        fun fromJson(jsonData: String): DeckMetaDataModel {
            val jsonObject = JSONObject(jsonData)
            return DeckMetaDataModel(
                jsonObject.getLong("did"),
                jsonObject.getString("deckName"),
                Counts(
                    jsonObject.getInt("new"),
                    jsonObject.getInt("lrn"),
                    jsonObject.getInt("rev")
                ),
                jsonObject.getInt("eta"),
            )
        }
    }
}
