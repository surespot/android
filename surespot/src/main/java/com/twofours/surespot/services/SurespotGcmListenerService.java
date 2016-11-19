/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twofours.surespot.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import com.google.android.gms.gcm.GcmListenerService;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.UIUtils;

import java.util.ArrayList;
import java.util.Date;

public class SurespotGcmListenerService extends GcmListenerService {

    private static final String TAG = "SurespotGcmListenerService";

    private PowerManager mPm;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mPm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        mBuilder = new NotificationCompat.Builder(this);
        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    }


    /**
     * Called when message is received.
     *
     * @param senderId SenderID of the sender.
     * @param bundle Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(String senderId, Bundle bundle) {
        SurespotLog.i(TAG, "received GCM message, bundle: " + bundle);
        String to = bundle.getString("to");
        String type = bundle.getString("type");
        String from = bundle.getString("sentfrom");

        ChatController chatController = ChatManager.getChatController(this, to);

        if ("message".equals(type)) {
            // make sure to is someone on this phone
            if (!IdentityController.getIdentityNames(this).contains(to)) {
                return;
            }

            // if the chat is currently showing don't show a notification
            // TODO setting for this

            boolean isScreenOn = false;
            if (mPm != null) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    isScreenOn = mPm.isInteractive();
                }
                else {
                    isScreenOn = mPm.isScreenOn();
                }
            }
            boolean hasLoggedInUser = IdentityController.hasLoggedInUser();
            boolean sameUser = ChatManager.isChatControllerAttached(to);

            //if current chat controller is for to user
            boolean tabOpenToUser = false;

            if (chatController != null) {
                if (to.equals(chatController.getUsername())) {
                    //if tab is open on from user
                    if (from.equals(chatController.getCurrentChat())) {
                        tabOpenToUser = true;
                    }
                }
            }

            boolean uiAttached = ChatManager.isUIAttached();

            SurespotLog.d(TAG, "gcm is screen on: %b, uiAttached: %b, hasLoggedInUser: %b, sameUser: %b, tabOpenToUser: %b", isScreenOn, uiAttached, hasLoggedInUser,
                    sameUser, tabOpenToUser);

            if (hasLoggedInUser && isScreenOn && sameUser && tabOpenToUser && uiAttached) {
                SurespotLog.d(TAG, "not displaying gcm notification because the tab is open for it.");
                return;
            }

            String spot = ChatUtils.getSpot(from, to);

            // add the message if it came in the GCM
            String message = bundle.getString("message");
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
                            SurespotApplication.getStateController().saveMessages(to, spot, messages, 0);
                        }
                        else {
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
                        //String password = IdentityController.getStoredPasswordForIdentity(this, to);
                        //SurespotLog.d(TAG, "GOT PASSWORD: %s",  password);


                        String fromName = null;
                        //get friend name if we can otherwise no name
                        if (sameUser && chatController != null) {
                            fromName = chatController.getAliasedName(from);
                        }

                        generateNotification(
                                this,
                                SurespotConstants.IntentFilters.MESSAGE_RECEIVED,
                                from,
                                to,
                                getString(R.string.notification_title),
                                TextUtils.isEmpty(fromName) ?
                                        getString(R.string.notification_message_no_from, to) :
                                        getString(R.string.notification_message, to, fromName),
                                to + ":" + spot,
                                SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
                    }
                }
            }
            return;
        }

        if ("invite".equals(type)) {
            // make sure to is someone on this phone
            if (!IdentityController.getIdentityNames(this).contains(to)) {
                return;
            }

            boolean sameUser = ChatManager.isChatControllerAttached(to);
            String fromName = null;
            //get friend name if we can otherwise no name
            if (sameUser && chatController != null) {
                fromName = chatController.getAliasedName(from);
            }

            generateNotification(
                    this,
                    SurespotConstants.IntentFilters.INVITE_REQUEST,
                    from,
                    to,
                    getString(R.string.notification_title),
                    TextUtils.isEmpty(fromName) ?
                            getString(R.string.notification_invite_no_from, to) :
                            getString(R.string.notification_invite, to, fromName),
                    to + ":" + from,
                    SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
            return;
        }

        if ("inviteResponse".equals(type)) {
            // make sure to is someone on this phone
            if (!IdentityController.getIdentityNames(this).contains(to)) {
                return;
            }

            boolean sameUser = ChatManager.isChatControllerAttached(to);
            String fromName = null;
            //get friend name if we can otherwise no name
            if (sameUser && chatController != null) {
                fromName = chatController.getAliasedName(from);
            }

            generateNotification(
                    this,
                    SurespotConstants.IntentFilters.INVITE_RESPONSE,
                    from,
                    to,
                    getString(R.string.notification_title),
                    TextUtils.isEmpty(fromName) ?
                            getString(R.string.notification_invite_accept_no_from, to) :
                            getString(R.string.notification_invite_accept, to, fromName),
                    to,
                    SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
            return;
        }

        if ("system".equals(type)) {
            String tag = bundle.getString("tag");
            String title = bundle.getString("title");
            String message = bundle.getString("message");

            if (!TextUtils.isEmpty(tag) && !TextUtils.isEmpty(message) && !TextUtils.isEmpty(title)) {
                generateSystemNotification(this, title, message, tag, SurespotConstants.IntentRequestCodes.SYSTEM_NOTIFICATION);
            }
        }
    }
    // [END receive_message]

    private void generateNotification(Context context, String type, String from, String to, String title, String message, String tag, int id) {
        SurespotLog.d(TAG, "generateNotification");
        // get shared prefs
        SharedPreferences pm = context.getSharedPreferences(to, Context.MODE_PRIVATE);
        if (!pm.getBoolean("pref_notifications_enabled", true)) {
            return;
        }

        int icon = R.drawable.surespot_logo;

        // need to use same builder for only alert once to work:
        // http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
        mBuilder.setSmallIcon(icon).setContentTitle(title).setAutoCancel(true).setOnlyAlertOnce(false).setContentText(message);
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
        }
        else {
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

        mBuilder.setDefaults(defaults);
        mNotificationManager.notify(tag, id, mBuilder.build());
    }

    private void generateSystemNotification(Context context, String title, String message, String tag, int id) {

        // need to use same builder for only alert once to work:
        // http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
        mBuilder.setAutoCancel(true).setOnlyAlertOnce(true);

        int defaults = 0;

        mBuilder.setLights(0xff0000FF, 500, 5000);
        defaults |= Notification.DEFAULT_SOUND;
        defaults |= Notification.DEFAULT_VIBRATE;

        mBuilder.setDefaults(defaults);

        PendingIntent contentIntent = PendingIntent.getActivity(context, (int) new Date().getTime(), new Intent(), PendingIntent.FLAG_CANCEL_CURRENT);

        Notification notification = UIUtils.generateNotification(mBuilder, contentIntent, getPackageName(), title, message);

        mNotificationManager.notify(tag, id, notification);
    }}
