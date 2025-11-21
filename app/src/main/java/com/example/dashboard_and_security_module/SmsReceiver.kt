package com.example.dashboard_and_security_module

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFS_NAME = "MyAppPrefs"
        private const val FRIENDS_KEY = "friends_list"
        private const val CHANNEL_ID = "sms_notifications"
        private const val MAP_CHANNEL_ID = "map_sms_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        createNotificationChannel(context)
        createMapNotificationChannel(context)

        val bundle = intent.extras ?: return
        val format = bundle.getString("format")
        val pdus = bundle["pdus"] as? Array<*> ?: return

        for (pdu in pdus) {
            try {
                val sms = if (format != null) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }

                val sender = sms.displayOriginatingAddress ?: ""
                val content = sms.displayMessageBody?.trim() ?: ""

                Log.d(TAG, "Received SMS from $sender: $content")

                when {
                    content.startsWith("LOC_REQ:", ignoreCase = true) -> {
                        val code = content.substringAfter("LOC_REQ:").trim()
                        handleLocationRequest(context, normalizePhone(sender), code)
                    }

                    content.startsWith("LOC_RESP:", ignoreCase = true) -> {
                        handleLocationResponse(context, content)
                    }

                    else -> {
                        simpleNotification(context, "Invalid Format", "From: $sender\n$content")
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse SMS: ${t.message}")
                simpleNotification(context, "Error", "Failed to parse incoming SMS.")
            }
        }
    }

    // ----------------------------------------------------------
    //  WHEN FRIEND SENDS LOCATION BACK
    // ----------------------------------------------------------
    private fun handleLocationResponse(context: Context, content: String) {
        val data = content.substringAfter("LOC_RESP:").trim()
        val parts = data.split(",")

        if (parts.size != 2) {
            simpleNotification(context, "Invalid Response", "Wrong format: $content")
            return
        }

        val lat = parts[0].toDoubleOrNull()
        val lon = parts[1].toDoubleOrNull()

        if (lat == null || lon == null) {
            simpleNotification(context, "Invalid Coordinates", "Could not parse: $content")
            return
        }

        showMapNotification(context, lat, lon)
    }

    private fun showMapNotification(context: Context, lat: Double, lon: Double) {

        //  Intent to open LocationActivity with coordinates
        val mapIntent = Intent(context, LocationActivity::class.java).apply {
            putExtra("friend_lat", lat)
            putExtra("friend_lon", lon)
            putExtra("friend_name", "SMS Friend")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingMapIntent = PendingIntent.getActivity(
            context,
            200,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MAP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Location Received")
            .setContentText("Tap GO TO MAP to view your Family Members location.")
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_mylocation, "GO TO MAP", pendingMapIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9999, builder.build())
    }

    // ----------------------------------------------------------
    //  YOUR ORIGINAL REQUEST VERIFICATION LOGIC
    // ----------------------------------------------------------
    private fun handleLocationRequest(context: Context, senderNormalized: String, requestedCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(FRIENDS_KEY, null) ?: run {
            simpleNotification(context, "No Cached Friends", "Cannot verify sender.")
            return
        }

        val type = object : TypeToken<List<Friend>>() {}.type
        val friends: List<Friend> = Gson().fromJson(json, type)

        val matching = friends.find {
            normalizePhone(it.phone) == senderNormalized && it.code == requestedCode
        }

        if (matching != null) {
            simpleNotification(context, "Verified Match", "Matched with: ${matching.name}")
            sendCurrentLocation(context, senderNormalized)
        } else {
            simpleNotification(context, "Denied Request", "Unauthorized request.")
        }
    }

    private fun sendCurrentLocation(context: Context, phoneNumberNormalized: String) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            simpleNotification(context, "Location Error", "Missing location permissions.")
            return
        }

        val location: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (location != null) {
            val msg = "LOC_RESP:${location.latitude},${location.longitude}"

            try {
                SmsManager.getDefault().sendTextMessage(phoneNumberNormalized, null, msg, null, null)
                simpleNotification(context, "Location Sent", "Sent to $phoneNumberNormalized")
            } catch (e: Exception) {
                simpleNotification(context, "SMS Failed", "Error: ${e.message}")
            }
        } else {
            simpleNotification(context, "Location Unavailable", "No recent location found.")
        }
    }

    // ----------------------------------------------------------
    // 🔔 SIMPLE NOTIFICATION (YOUR OLD ONE)
    // ----------------------------------------------------------
    private fun simpleNotification(context: Context, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SMS Notifications", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createMapNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(MAP_CHANNEL_ID, "SMS Location Map", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun normalizePhone(phone: String): String {
        var cleaned = phone.replace(Regex("\\D"), "")
        if (cleaned.startsWith("63") && cleaned.length == 12) cleaned = "0" + cleaned.substring(2)
        return cleaned
    }
}


//20/11/2025
