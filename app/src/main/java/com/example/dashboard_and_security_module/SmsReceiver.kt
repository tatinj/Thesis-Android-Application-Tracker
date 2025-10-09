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
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
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

        private var handler: Handler? = null
        private var toastRunnable: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") {
            startToastLoop(context)
            return
        }

        stopToastLoop()
        showToast(context, "📩 SMS received, checking message...")

        createNotificationChannel(context)
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
                showNotification(context, "Message Received", "From: $sender\n$content")

                showToast(context, "📬 Checking SMS content...")

                when {
                    content.startsWith("LOC_REQ:", ignoreCase = true) -> {
                        showToast(context, "📍 Location request detected...")
                        val code = content.substringAfter("LOC_REQ:").trim()
                        handleLocationRequest(context, normalizePhone(sender), code)
                    }

                    content.startsWith("LOC_RESP:", ignoreCase = true) -> {
                        showToast(context, "✅ Location response received.")
                        showNotification(context, "Location Response", "Response from $sender: $content")
                    }

                    else -> {
                        showToast(context, "⚠️ Invalid SMS format.")
                        showNotification(context, "Invalid Format", "Message from $sender is not valid.")
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse SMS: ${t.message}")
            }
        }

        startToastLoop(context) // restart waiting loop after processing
    }

    private fun handleLocationRequest(context: Context, senderNormalized: String, requestedCode: String) {
        showToast(context, "🔎 Verifying sender...")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(FRIENDS_KEY, null) ?: run {
            showToast(context, "❌ No cached friends found.")
            showNotification(context, "No Cached Friends", "Cannot verify sender.")
            return
        }

        val type = object : TypeToken<List<Friend>>() {}.type
        val friends: List<Friend> = Gson().fromJson(json, type)

        val matching = friends.find {
            normalizePhone(it.phone) == senderNormalized && it.code == requestedCode
        }

        if (matching != null) {
            showToast(context, "✅ Verified! Sending location...")
            showNotification(context, "Verified Request", "Sending location to ${matching.name}")
            sendCurrentLocation(context, senderNormalized)
        } else {
            showToast(context, "🚫 Unverified sender or code mismatch.")
            showNotification(context, "Denied Request", "Unverified sender or code mismatch.")
        }
    }

    private fun sendCurrentLocation(context: Context, phoneNumberNormalized: String) {
        showToast(context, "📡 Getting current location...")

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showToast(context, "⚠️ Missing location permission.")
            showNotification(context, "Location Error", "Missing location permissions.")
            return
        }

        val location: Location? = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
            null
        }

        if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            val msg = "LOC_RESP:$lat,$lon"

            try {
                SmsManager.getDefault().sendTextMessage(phoneNumberNormalized, null, msg, null, null)
                showToast(context, "📨 Location sent via SMS.")
                showNotification(context, "Location Sent", "Sent to $phoneNumberNormalized")
            } catch (e: Exception) {
                showToast(context, "❌ Failed to send SMS: ${e.message}")
                showNotification(context, "SMS Failed", "Error sending SMS: ${e.message}")
            }
        } else {
            showToast(context, "❌ No location found.")
            showNotification(context, "Location Unavailable", "No recent location found.")
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MembersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Notifications"
            val descriptionText = "Notifications for incoming SMS messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun normalizePhone(phone: String): String = phone.replace(Regex("\\D"), "")

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startToastLoop(context: Context) {
        if (handler == null) handler = Handler(Looper.getMainLooper())
        toastRunnable = object : Runnable {
            override fun run() {
                showToast(context, "⏳ Waiting for SMS...")
                handler?.postDelayed(this, 5000)
            }
        }
        handler?.post(toastRunnable!!)
    }

    private fun stopToastLoop() {
        handler?.removeCallbacks(toastRunnable ?: return)
    }
}



