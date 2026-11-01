package uk.matthew.tappay;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Posts the fake payment notifications. Every one is labelled as a simulation. */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "tappay_demo_payments";
    private static final int NOTIFICATION_ID = 1001;

    private NotificationHelper() {
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notif_channel_desc));
        manager.createNotificationChannel(channel);
    }

    public static void postDemoPaymentNotification(Context context, String amount, String source) {
        ensureChannel(context);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // permission not granted; the in-app dialog still shows the result
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentText(context.getString(R.string.notif_body, amount, source))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notif_body_big, amount, source)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }
}
