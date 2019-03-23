/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twofours.surespot.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.Utils;

import java.util.ArrayList;
import java.util.Date;

public class SurespotFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SurespotFirebaseMessagingService";

    private PowerManager mPm;
    NotificationCompat.Builder mBuilder;
    NotificationManagerCompat mNotificationManager;
    private static final String CHANNEL_ID = "surespot_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        mPm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        mNotificationManager = NotificationManagerCompat.from(this);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Intent intent = new Intent(this, RegistrationIntentService.class);
        RegistrationIntentService.enqueueWork(this, intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        SurespotLog.d(TAG, "received Fcm message: %s", remoteMessage);
        String to = remoteMessage.getData().get("to");

        // make sure to is someone on this phone
        if (!IdentityController.getIdentityNames(this).contains(to)) {
            return;
        }

        String type = remoteMessage.getData().get("type");
        String from = remoteMessage.getData().get("sentfrom");

        //show different notification on lollipop and above
        boolean is21 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

        ChatController chatController = ChatManager.getChatController(to);

        //get alias
        String fromName = Utils.getAlias(this, to, from);

        //if we don't have alias use from
        if (TextUtils.isEmpty(fromName)) {
            fromName = from;
        }

        if ("message".equals(type)) {
            // if the chat is currently showing don't show a notification
            // TODO setting for this

            boolean isScreenOn = false;
            if (mPm != null) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    isScreenOn = mPm.isInteractive();
                } else {
                    isScreenOn = mPm.isScreenOn();
                }
            }

            boolean hasLoggedInUser = IdentityController.hasLoggedInUser();
            boolean sameUser = ChatManager.isChatControllerAttached(to);
            boolean chatControllerConnected = false;

            //if current chat controller is for to user
            boolean tabOpenToUser = false;
            boolean muted = false;
            Friend friend = null;

            if (chatController != null) {
                if (to.equals(chatController.getUsername())) {
                    //if tab is open on from user
                    if (from.equals(chatController.getCurrentChat())) {
                        tabOpenToUser = true;
                    }

                    chatControllerConnected = chatController.isConnected();

                    friend = chatController.getFriendAdapter().getFriend(from);
                    if (friend != null) {
                        muted = friend.isMuted();
                    }
                }
            }

            boolean uiAttached = ChatManager.isUIAttached();

            SurespotLog.d(TAG, "gcm is screen on: %b, uiAttached: %b, hasLoggedInUser: %b, sameUser: %b, tabOpenToUser: %b, connected: %b", isScreenOn, uiAttached, hasLoggedInUser,
                    sameUser, tabOpenToUser, chatControllerConnected);

            if (hasLoggedInUser && isScreenOn && sameUser && tabOpenToUser && uiAttached && chatControllerConnected) {
                SurespotLog.d(TAG, "not displaying gcm notification because the tab is open for it and the chat controller is connected.");
                return;
            }

            if (friend == null) {
                StateController.FriendState friendState = SurespotApplication.getStateController().loadFriends(to);
                if (friendState != null) {
                    friend = FriendAdapter.getFriend(friendState.friends, from);
                    if (friend != null) {
                        muted = friend.isMuted();
                    }
                }
            }

            SurespotLog.d(TAG, "muted: %b", muted);

            boolean tabVisibleButNotConnected = hasLoggedInUser && isScreenOn && sameUser && tabOpenToUser && uiAttached && !chatControllerConnected;
            String spot = ChatUtils.getSpot(from, to);

            // add the message if it came in the GCM
            String message = remoteMessage.getData().get("message");
            if (message != null) {
                SurespotMessage sm = SurespotMessage.toSurespotMessage(message);
                if (sm != null) {
                    sm.setGcm(true);
                    // see if we can add it to existing chat controller
                    boolean added = false;
                    if (chatController != null) {
                        if (chatController.addMessageExternal(sm)) {
                            SurespotLog.d(TAG, "adding gcm message to controller");
                            chatController.saveMessages(from);
                            added = true;
                        }
                    }

                    // if not add it directly
                    if (!added) {
                        ArrayList<SurespotMessage> messages = SurespotApplication.getStateController().loadMessages(to, spot);
                        if (!messages.contains(sm)) {
                            messages.add(sm);
                            SurespotLog.d(TAG, "added gcm message directly to disk");
                            added = true;
                            SurespotApplication.getStateController().saveMessages(to, spot, messages);
                        } else {
                            SurespotLog.d(TAG, "did not add gcm message directly to disk as it's already there");
                            // AEP what was happening here is it wasn't adding the message because
                            // it's already been received on the websocket and saved to disk before the push message arrives
                            // so gonna show notification now; was unnecessary before because the socket would have been
                            // disconnected before push arrived if we got this far thanks to above isscreenon...etc.  check
                            // OE hmmm... is there a flag we can set if the main activity is not paused to indicate the user has truly "seen" the message or not?
                            //     added = true;
                        }
                    }

                    if (added) {
                        boolean notified = false;
                        //tab visible but not connected, and message added by the gcm, so just notify the chat adapter
                        //  SurespotLog.d(TAG, "tab visible but not connected, not showing notification, notifying chat adapter data set changed");
                        if (chatController != null) {
                            notified = chatController.notifyChatAdapterDataSetChanged(from) && tabVisibleButNotConnected;
                        }
                        if (!notified && !muted) {
                            //otherwise show notification
                            //String password = IdentityController.getStoredPasswordForIdentity(this, to);
                            //SurespotLog.d(TAG, "GOT PASSWORD: %s",  password);


                            generateNotification(
                                    this,
                                    SurespotConstants.IntentFilters.MESSAGE_RECEIVED,
                                    from,
                                    to,
                                    is21 ? to : getString(R.string.notification_title),
                                    is21 ? getString(R.string.notification_message_21, fromName) : getString(R.string.notification_message, to, fromName),
                                    to + ":" + spot,
                                    SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
                        }
                    }
                }
            }
            return;
        }

        if ("invite".equals(type)) {
            generateNotification(
                    this,
                    SurespotConstants.IntentFilters.INVITE_REQUEST,
                    from,
                    to,
                    is21 ? to : getString(R.string.notification_title),
                    is21 ? getString(R.string.notification_invite_21, fromName) : getString(R.string.notification_invite, to, fromName),
                    to + ":" + from,
                    SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
            return;
        }

        if ("inviteResponse".equals(type)) {
            generateNotification(
                    this,
                    SurespotConstants.IntentFilters.INVITE_RESPONSE,
                    from,
                    to,
                    is21 ? to : getString(R.string.notification_title),
                    is21 ? getString(R.string.notification_invite_accept_21, fromName) : getString(R.string.notification_invite_accept, to, fromName),
                    to,
                    SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
            return;
        }
    }
    // [END receive_message]

    private int getNotificationIcon() {
        boolean useTransparentIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        return useTransparentIcon ? R.drawable.surespot_logo_transparent : R.drawable.surespot_logo;
    }

    private void generateNotification(Context context, String type, String from, String to, String title, String message, String tag, int id) {
        SurespotLog.d(TAG, "generateNotification");
        // get shared prefs
        SharedPreferences pm = context.getSharedPreferences(to, Context.MODE_PRIVATE);
        if (!pm.getBoolean("pref_notifications_enabled", true)) {
            return;
        }

        int icon = getNotificationIcon();
       // Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.surespot_logo);

        // need to use same builder for only alert once to work:
        // http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
        mBuilder.setSmallIcon(icon)
                .setColor(getResources().getColor(R.color.surespotBlue))
           //     .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentText(message);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        Intent mainIntent = null;
        mainIntent = new Intent(context, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, to);
        mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, from);
        mainIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE, type);

        stackBuilder.addNextIntent(mainIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent((int) new Date().getTime(), PendingIntent.FLAG_CANCEL_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);
        int defaults = 0;

        boolean showLights = pm.getBoolean("pref_notifications_led", true);
        boolean makeSound = pm.getBoolean("pref_notifications_sound", true);
        boolean vibrate = pm.getBoolean("pref_notifications_vibration", true);
        int color = pm.getInt("pref_notification_color", getResources().getColor(R.color.surespotBlue));

        if (showLights) {
            SurespotLog.v(TAG, "showing notification led");
            mBuilder.setLights(color, 500, 5000);
            defaults |= Notification.FLAG_SHOW_LIGHTS; // shouldn't need this - setLights does it.  Just to make sure though...
        } else {
            mBuilder.setLights(color, 0, 0);
        }

        if (makeSound) {
            SurespotLog.v(TAG, "making notification sound");
            defaults |= Notification.DEFAULT_SOUND;
        }

        if (vibrate) {
            SurespotLog.v(TAG, "vibrating notification");
            defaults |= Notification.DEFAULT_VIBRATE;
        }

        mBuilder.setWhen(new Date().getTime());

        mBuilder.setDefaults(defaults);
        mNotificationManager.notify(tag, id, mBuilder.build());
    }


}
