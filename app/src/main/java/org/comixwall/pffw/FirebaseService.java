package org.comixwall.pffw;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static org.comixwall.pffw.MainActivity.logger;

public class FirebaseService extends FirebaseMessagingService {
    private NotificationManagerCompat notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = NotificationManagerCompat.from(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
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

                // TODO: Need CHANNEL_ID for O?
                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
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
