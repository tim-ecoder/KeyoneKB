package com.sateda.keyonekb2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.NotificationCompat;

public class NotificationProcessor {

    public static final int NOTIFICATION_ID1 = 1;
    public static final int NOTIFICATION_ID2 = 2;
    private final String gestureModeChannelId1 = "KeyoneKb2_NotificationChannel_GestureMode";
    private final String gestureModeChannelDescription = "Режимы жестов (на полях ввода, на просмотре, выключен)";
    private final String layoutModeChannelId1 = "KeyoneKb2_NotificationChannel_KeyboardLayout";
    private final String layoutModeChannelDescription = "Раскладка клавиатуры (языки, символы и пр.)";
    private android.support.v4.app.NotificationCompat.Builder builderLayout;
    private Notification.Builder builder2Layout;
    private android.support.v4.app.NotificationCompat.Builder builderGesture;
    private Notification.Builder builder2Gesture;
    private NotificationManager notificationManager;
    private NotificationChannel notificationChannelLayoutMode;
    private NotificationChannel notificationChannelGestureMode;

    public void Initialize(Context context) {
        if(notificationManager != null)
            return;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName("com.sateda.keyonekb2.satedakeyboard", "com.sateda.keyboard.keyonekb2.MainActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        InitializeKeyboardLayoutBuilder(context);
        InitializeGestureModeBuilder(context);

    }

    private void InitializeKeyboardLayoutBuilder(Context context) {
        if (Build.VERSION.SDK_INT >= 27) {
            //Борьба с Warning для SDK_V=27 (KEY2 вестимо)
            //04-10 20:36:34.040 13838-13838/xxx.xxxx.xxxx W/Notification: Use of stream types is deprecated for operations other than volume control
            //See the documentation of setSound() for what to use instead with android.media.AudioAttributes to qualify your playback use case
            if(notificationChannelLayoutMode != null)
                return;

            notificationChannelLayoutMode = new NotificationChannel(layoutModeChannelId1, layoutModeChannelDescription, NotificationManager.IMPORTANCE_DEFAULT);
            //notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
            notificationChannelLayoutMode.enableVibration(false); //Set if it is necesssary
            notificationChannelLayoutMode.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannelLayoutMode);

            builder2Layout = new Notification.Builder(context, layoutModeChannelId1);
            builder2Layout.setOngoing(true);
            builder2Layout.setAutoCancel(true);
            builder2Layout.setVisibility(Notification.VISIBILITY_SECRET);
        }
        else
        {
            if(builderLayout != null)
                return;
            builderLayout = new NotificationCompat.Builder(context);
            builderLayout.setOngoing(true);
            builderLayout.setAutoCancel(false);
            builderLayout.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            builderLayout.setPriority(NotificationCompat.PRIORITY_DEFAULT);

        }
    }

    private void InitializeGestureModeBuilder(Context context) {
        if (Build.VERSION.SDK_INT >= 27) {
            //Борьба с Warning для SDK_V=27 (KEY2 вестимо)
            //04-10 20:36:34.040 13838-13838/xxx.xxxx.xxxx W/Notification: Use of stream types is deprecated for operations other than volume control
            //See the documentation of setSound() for what to use instead with android.media.AudioAttributes to qualify your playback use case

            if(notificationChannelGestureMode != null)
                return;

            notificationChannelGestureMode = new NotificationChannel(gestureModeChannelId1, gestureModeChannelDescription, NotificationManager.IMPORTANCE_DEFAULT);
            //notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
            notificationChannelGestureMode.enableVibration(false); //Set if it is necesssary
            notificationChannelGestureMode.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannelGestureMode);

            builder2Gesture = new Notification.Builder(context, gestureModeChannelId1);
            builder2Gesture.setOngoing(true);
            builder2Gesture.setAutoCancel(true);
            builder2Gesture.setVisibility(Notification.VISIBILITY_SECRET);
        }
        else
        {
            if(builderGesture != null)
                return;
            builderGesture = new NotificationCompat.Builder(context);
            builderGesture.setAutoCancel(false);
            builderGesture.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            builderGesture.setPriority(NotificationCompat.PRIORITY_DEFAULT);

        }
    }

    public void CancelAll() {
        notificationManager.cancelAll();
    }

    public void UpdateNotificationLayoutMode() {

        if(builderLayout != null)
            notificationManager.notify(NOTIFICATION_ID1, builderLayout.build());
        else if (builder2Layout != null) {
            notificationManager.notify(NOTIFICATION_ID1, builder2Layout.build());
        }


    }

    public void UpdateNotificationGestureMode() {
        if(builderGesture != null) {
            notificationManager.notify(NOTIFICATION_ID2, builderGesture.build());
        }
        else if (builder2Gesture != null) {
            notificationManager.notify(NOTIFICATION_ID2, builder2Gesture.build());
        }
    }

    int currentIconLayout = 0;
    String currentTitleLayout = "";

    public boolean SetSmallIconLayout(int icon1) {
        if(builderLayout != null) {
            builderLayout.setSmallIcon(icon1);
        } else if (builder2Layout != null) {
            builder2Layout.setSmallIcon(icon1);
        }
        //Changed
        if(currentIconLayout != icon1) {
            currentIconLayout = icon1;
            return true;
        }
        else
            return false;
    }

    public boolean SetContentTitleLayout(String title) {
        if(builderLayout != null)
            builderLayout.setContentTitle(title);
        else if (builder2Layout != null)
            builder2Layout.setContentTitle(title);
        //Changed
        if(currentTitleLayout.compareTo(title) != 0) {
            currentTitleLayout = title;
            return true;
        }
        else
            return false;
    }

    int currentIconGestures = 0;
    String currentTitleGestures = "";

    public boolean SetSmallIconGestureMode(int icon1) {
        if(builderGesture != null) {
            builderGesture.setSmallIcon(icon1);
        } else if (builder2Gesture != null) {
            builder2Gesture.setSmallIcon(icon1);
        }
        //Changed
        if(currentIconGestures != icon1) {
            currentIconGestures = icon1;
            return true;
        }
        else
            return false;
    }

    public boolean SetContentTitleGestureMode(String title) {
        if(builderGesture != null)
            builderGesture.setContentTitle(title);
        else if (builder2Gesture != null)
            builder2Gesture.setContentTitle(title);
        //Changed
        if(currentTitleGestures.compareTo(title) != 0) {
            currentTitleGestures = title;
            return true;
        }
        else
            return false;
    }
}
