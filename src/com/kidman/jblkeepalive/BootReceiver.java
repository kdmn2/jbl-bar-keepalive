package com.kidman.jblkeepalive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (KeepAlivePrefs.isEnabled(context) && KeepAlivePrefs.isStartOnBoot(context)) {
            Intent svc = new Intent(context, KeepAliveService.class);
            svc.setAction(KeepAliveService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
