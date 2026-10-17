package com.airocta.vyron;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps an active, user-started microphone conversation alive while another app is shown. */
public final class VoiceKeeperService extends Service {
    private static final String CHANNEL="vyron_voice";
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"VYRON voice",
                NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.vyron_icon)
                .setContentTitle("VYRON voice is active")
                .setContentText("Tap to return to the live conversation")
                .setContentIntent(pending).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(4242,notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(4242,notification);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
