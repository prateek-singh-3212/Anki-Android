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

package com.ichi2.anki.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.ichi2.anki.NotificationDatastore
import com.ichi2.anki.NotificationHelper
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

/**
 * Broadcast receiver to listen timezone change of user.
 * Changes the notification triggering time when user timezone changes.
 * */
class TimeZoneChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("TimeZoneChangeReceiver Started...")

        CoroutineScope(Dispatchers.Default).launch {
            val notificationDatastore = NotificationDatastore.getInstance(context)

            val oldTimezone: String = notificationDatastore.getString(TIMEZONE_KEY, "null")
            val newTimezone: String = TimeZone.getDefault().id
            val now = TimeManager.time.intTimeMS()

            /**
             * Get the current offset according to given TimeZone.
             * */
            fun getOffsetFromNow(timezone: String) = TimeZone.getTimeZone(timezone).getOffset(now)

            if (oldTimezone != "null" || getOffsetFromNow(oldTimezone) == getOffsetFromNow(newTimezone)) {
                Timber.d("No Timezone changed found...")
                return@launch
            }

            Timber.d("Timezone changed...")
            // Change the timezone to new timezone and recalculate the notification according to new time zone.
            notificationDatastore.putStringAsync(TIMEZONE_KEY, newTimezone)

            val oldCurrTimeMS = getZonedCurrTimeMS(oldTimezone)
            val newCurrTimeMS = getZonedCurrTimeMS(newTimezone)
            val timeDiffMS = oldCurrTimeMS - newCurrTimeMS
            Timber.d("Timezone: Current time -> $newCurrTimeMS Time difference -> $timeDiffMS")

            // Calibrate notification time.
            NotificationHelper(context).calibrateNotificationTime()
        }
    }

    companion object {
        private const val TIMEZONE_KEY = "TIMEZONE"

        /**
         * Registers timezone change receiver on app start.
         * */
        fun registerTimeZoneChangeReceiver(context: Context, receiver: BroadcastReceiver) {
            Timber.d("Registering Timezone change receiver...")

            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            context.registerReceiver(receiver, filter)
        }

        /**
         * Get current time according the timezone of user.
         * @return epoch time in milliseconds
         * */
        fun getZonedCurrTimeMS(zoneId: String): Long {
            val currTimeMS = TimeManager.time.intTimeMS()
            val offset = TimeZone.getTimeZone(zoneId).getOffset(currTimeMS)
            return currTimeMS + offset
        }
    }
}
