package com.sateda.keyonekb2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.NotificationCompat;

public class NotificationProcessor {

    private android.support.v4.app.NotificationCompat.Builder builder;
    private Notification.Builder builder2;
    private NotificationManager notificationManager;

    public void Initialize(Context context) {
        if(notificationManager != null)
            return;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName("com.sateda.keyonekb2.satedakeyboard", "com.sateda.keyboard.keyonekb2.MainActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        if (Build.VERSION.SDK_INT >= 27) {
            //Борьба с Warning для SDK_V=27 (KEY2 вестимо)
            //04-10 20:36:34.040 13838-13838/xxx.xxxx.xxxx W/Notification: Use of stream types is deprecated for operations other than volume control
            //See the documentation of setSound() for what to use instead with android.media.AudioAttributes to qualify your playback use case

            String channelId = "KeyoneKbNotificationChannel2";
            String channelDescription = "KeyoneKbNotificationChannel";
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, channelDescription, NotificationManager.IMPORTANCE_UNSPECIFIED);
                //notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
                notificationChannel.enableVibration(false); //Set if it is necesssary
                notificationChannel.setSound(null, null);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            builder2 = new Notification.Builder(context, channelId);
            builder2.setOngoing(true);
            builder2.setAutoCancel(true);
            builder2.setVisibility(Notification.VISIBILITY_SECRET);
        }
        else
        {
            builder = new NotificationCompat.Builder(context);
            builder.setOngoing(true);
            builder.setAutoCancel(false);
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            builder.setPriority(NotificationCompat.PRIORITY_LOW);

        }

    }

    public void CancelAll() {
        notificationManager.cancelAll();
    }

    public void UpdateNotification() {
        if(builder != null)
            notificationManager.notify(1, builder.build());
        else if (builder2 != null) {
            notificationManager.notify(1, builder2.build());
        }
    }

    int currentIcon = 0;
    String currentTitle = "";

    public boolean SetSmallIcon(int icon1) {
        if(builder != null) {
            builder.setSmallIcon(icon1);
        } else if (builder2 != null) {
            builder2.setSmallIcon(icon1);
        }
        //Changed
        if(currentIcon != icon1) {
            currentIcon = icon1;
            return true;
        }
        else
            return false;
    }

    public boolean SetContentTitle(String title) {
        if(builder != null)
            builder.setContentTitle(title);
        else if (builder2 != null)
            builder2.setContentTitle(title);
        //Changed
        if(currentTitle.compareTo(title) != 0) {
            currentTitle = title;
            return true;
        }
        else
            return false;
    }
}
