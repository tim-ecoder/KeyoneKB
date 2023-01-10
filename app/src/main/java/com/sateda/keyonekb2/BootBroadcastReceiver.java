package com.sateda.keyonekb2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.support.v4.os.BuildCompat;

import java.util.Arrays;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean bootCompleted;
        String action = intent.getAction();

        if (BuildCompat.isAtLeastN()) {
            bootCompleted = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action);
        } else {
            bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(action);
        }
        if (!bootCompleted) {
            return;
        }

        LoadShortcuts();

    }

    private void LoadShortcuts() {

        Context c = KeyoneIME.Instance;

        ShortcutInfo dsQuickSettings = new ShortcutInfo.Builder(c, "QuickSettings")
                .setShortLabel("QuickSettings")
                .setLongLabel("QuickSettings")
                .setIcon(Icon.createWithResource(c, R.drawable.ic_rus_shift_all))
                .setIntents(
                        new Intent[]{
                                new Intent(IntentQuickSettings.ACTION).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                .setRank(1)
                .build();

        ShortcutInfo dsNotifications = new ShortcutInfo.Builder(c, "Notifications")
                .setShortLabel("Notifications")
                .setLongLabel("Notifications")
                .setIcon(Icon.createWithResource(c, R.drawable.ic_rus_shift_all))
                .setIntents(
                        new Intent[]{
                                new Intent(IntentNotifications.ACTION).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                .setRank(1)
                .build();

        final ShortcutManager shortcutManager = c.getSystemService(ShortcutManager.class);
        shortcutManager.setDynamicShortcuts(Arrays.asList(dsQuickSettings, dsNotifications));
    }
}
