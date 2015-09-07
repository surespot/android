package com.twofours.surespot.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat.Builder;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.ui.UIUtils;

@SuppressLint("NewApi")
public class ChatTransmissionService extends Service {
    private static final String TAG = "ChatTransmissionService";

    private final IBinder mBinder = new ChatTransmissionServiceBinder();

    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");

        Notification notification = null;

        // if we're < 4.3 then start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // if this is the first time using the app don't use foreground service
            // TODO: have Adam explain what this does, why it's used
            boolean alreadyPrevented = Utils.getSharedPrefsBoolean(this, "firstTimePreventedForegroundServiceChatTransmission");
            if (alreadyPrevented) {
                // in 4.3 and above they decide to fuck us by showing the notification
                // so make the text meaningful at least
                PendingIntent contentIntent = PendingIntent.getActivity(this, SurespotConstants.IntentRequestCodes.BACKGROUND_CHAT_SERVICE_NOTIFICATION,
                        new Intent(this, MainActivity.class), 0);
                notification = UIUtils.generateNotification(new Builder(this), contentIntent, getPackageName(), R.drawable.surespot_logo_grey,
                        getString(R.string.caching_service_notification_title).toString(), "starting secure chat transmission service");
                notification.priority = Notification.PRIORITY_MIN;
            } else {
                Utils.putSharedPrefsBoolean(this, "firstTimePreventedForegroundServiceChatTransmission", true);
            }
        }
        else {
            notification = new Notification(0, null, System.currentTimeMillis());
            notification.flags |= Notification.FLAG_NO_CLEAR;
        }

        if (notification != null) {
            startForeground(SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION, notification);
        }
    }

    public class ChatTransmissionServiceBinder extends Binder {
        public ChatTransmissionService getService() {
            return ChatTransmissionService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        SurespotLog.i(TAG, "onDestroy");
        // TODO
    }

    public void initializeService() {
        SurespotLog.d(TAG, "initializeService: ", this.getClass().getSimpleName());

    }
}

