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

/**
 * Stores the notification details of particular deck.
 * */
data class DeckNotification(
    val enabled: Boolean,
    val did: Long,
    val deckName: String,
    val schedHour: Int,
    val schedMinutes: Int,
    val minCardDue: Int
) {

    // Empty constructor is required for jackson to serialize
    constructor() : this(
        false,
        Long.MAX_VALUE,
        "deck",
        0,
        0,
        0
    )
}