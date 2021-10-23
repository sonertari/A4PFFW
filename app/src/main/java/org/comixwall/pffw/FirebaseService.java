package org.comixwall.pffw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static org.comixwall.pffw.MainActivity.logger;
import static org.comixwall.pffw.MainActivity.sendToken;
import static org.comixwall.pffw.MainActivity.token;

public class FirebaseService extends FirebaseMessagingService implements OnCompleteListener<String> {
    private NotificationManagerCompat notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = NotificationManagerCompat.from(this);
    }

    @Override
    public void onNewToken(@NonNull String t) {
        logger.info("Firebase refreshed token= " + t);
        token = t;
        sendToken = true;
    }

    @Override
    public void onComplete(@NonNull Task<String> task) {
        token = task.getResult();
        // Controller sends the token before the next command it executes, and lowers this sendToken flag
        sendToken = true;
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        try {
            if (remoteMessage.getNotification() != null) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                Bundle bundle = new Bundle();
                bundle.putString("title", remoteMessage.getNotification().getTitle());
                bundle.putString("body", remoteMessage.getNotification().getBody());
                bundle.putString("data", remoteMessage.getData().get("data"));

                intent.putExtras(bundle);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                String channelId = getApplication().getString(R.string.notification_channel_id);

                // O+ needs notification channel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(channelId, "PFFW Notifier", NotificationManager.IMPORTANCE_DEFAULT);
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
                }

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, channelId)
                        .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                        .setSmallIcon(R.mipmap.notification)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.notification))
                        .setColor(Color.BLUE)
                        .setContentTitle(remoteMessage.getNotification().getTitle())
                        .setContentText(remoteMessage.getNotification().getBody())
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

                // Use 0 as notification id, because we cannot control the notifications received in the background anyway
                notificationManager.notify(0, mBuilder.build());
            }
        } catch (Exception ignored) {
        }
    }
}
