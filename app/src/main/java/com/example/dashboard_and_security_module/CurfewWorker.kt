package com.example.dashboard_and_security_module

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Tasks

class CurfewWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {

        val friendCode = inputData.getString("friendCode") ?: return Result.failure()
        val friendName = inputData.getString("friendName") ?: "Member"
        val homeLat = inputData.getDouble("homeLat", 0.0)
        val homeLon = inputData.getDouble("homeLon", 0.0)

        showDebugNotification("CurfewWorker", "Worker started for $friendName")

        try {
            val db = FirebaseFirestore.getInstance()

            // Step 1: Find real UID using inviteCode (like MembersActivity)
            val usersSnap = Tasks.await(db.collection("users").get())
            var friendUid: String? = null

            for (userDoc in usersSnap.documents) {
                val codeSnap = Tasks.await(
                    db.collection("users").document(userDoc.id)
                        .collection("meta").document("inviteCode")
                        .get()
                )
                if (codeSnap.getString("code") == friendCode) {
                    friendUid = userDoc.id
                    break
                }
            }

            if (friendUid == null) {
                showDebugNotification("CurfewWorker", "Friend UID not found for code $friendCode")
                return Result.failure()
            }

            // Step 2: Fetch latest location from history subcollection
            val snap = Tasks.await(
                db.collection("users").document(friendUid)
                    .collection("history")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
            )

            if (snap.isEmpty) {
                showDebugNotification("CurfewWorker", "No history found for $friendName")
                return Result.success()
            }

            val doc = snap.documents[0]
            val lat = doc.getDouble("latitude") ?: 0.0
            val lon = doc.getDouble("longitude") ?: 0.0
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            // Step 3: Calculate distance to home
            val distance = FloatArray(1)
            Location.distanceBetween(homeLat, homeLon, lat, lon, distance)
            val isAtHome = distance[0] <= 10f

            if (isAtHome) {
                showDebugNotification("CurfewWorker", "$friendName is at home. Distance: ${distance[0]}m")
            } else {
                showDebugNotification("CurfewWorker", "$friendName is NOT at home. Distance: ${distance[0]}m")
            }

        } catch (e: Exception) {
            showDebugNotification("CurfewWorker ERROR", e.message ?: "Unknown error")
            e.printStackTrace()
            return Result.failure()
        }

        showDebugNotification("CurfewWorker", "Worker finished for $friendName")
        return Result.success()
    }

    private fun showDebugNotification(title: String, message: String) {
        val channelId = "curfew_debug_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Curfew Debug", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
