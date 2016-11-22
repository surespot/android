package com.twofours.surespot.chat;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.MenuItem;

import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.IAsyncCallback;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by adam on 11/14/16.
 */

public class ChatManager {
    private static String TAG = "ChatManager";
    private static HashMap<String, ChatController> mMap = new HashMap<>();
    private static BroadcastReceiverHandler mConnectivityReceiver;
    private static boolean mPaused;
    private static String mAttachedUsername;



    public static synchronized ChatController getChatController(String username) {
       if (TextUtils.isEmpty(username)) {
           return null;
        }

        return mMap.get(username);
    }

    public static synchronized ChatController attachChatController(Context context,
                                                                   String username,
                                                                   ViewPager viewPager,
                                                                   FragmentManager fm,
                                                                   TitlePageIndicator pageIndicator,
                                                                   ArrayList<MenuItem> menuItems,
                                                                   IAsyncCallback<Boolean> progressCallback,
                                                                   IAsyncCallback<Void> sendIntentCallback,
                                                                   IAsyncCallback<Friend> tabShowingCallback,
                                                                   IAsyncCallback<Object> listener) {
        SurespotLog.d(TAG, "attachChatController, username: %s", username);

        ChatController cc = mMap.get(username);
        if (cc == null) {
            SurespotLog.d(TAG, "creating chat controller for %s", username);
            cc = new ChatController(context, username);
            mMap.put(username, cc);
        }

        cc.attach(context, viewPager, fm, pageIndicator, menuItems, progressCallback, sendIntentCallback, tabShowingCallback, listener);
        mAttachedUsername = username;
        if (mConnectivityReceiver == null) {
            SurespotLog.d(TAG, "attachChatController, username: %s registering new broadcast receiver", username);
            mConnectivityReceiver = new BroadcastReceiverHandler();
            try {
                context.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            } catch (Exception e) {
                SurespotLog.w(TAG, e, "detach");
            }
        }

        return cc;
    }

    public static boolean isChatControllerAttached(String username) {
        if (mAttachedUsername != null) {
            return (mAttachedUsername.equals(username));
        }
        return false;
    }


    public static synchronized void detach(Context context) {
        SurespotLog.d(TAG, "detach");
        if (mConnectivityReceiver != null) {
            SurespotLog.d(TAG, "detach, unregistering broadcast receiver");
            try {
                context.unregisterReceiver(mConnectivityReceiver);
            } catch (Exception e) {
                SurespotLog.w(TAG, e, "detach");
            }
            mConnectivityReceiver = null;
        }
    }

    public static synchronized void pause(String username) {
        mPaused = true;
        ChatController cc = getChatController(username);
        if (cc != null) {
            cc.save();
            cc.disconnect();
        }
    }

    public static synchronized void resume(String username) {
        mPaused = false;
        ChatController cc = getChatController(username);
        if (cc != null) {
            cc.resume();
        }
    }

    public static synchronized boolean isUIAttached() {
        return !mPaused;
    }

    public static synchronized void resetState(Context context) {
        mMap.clear();
        mAttachedUsername = null;
        mConnectivityReceiver = null;
        FileUtils.wipeFileUploadDir(context);
    }

    private static class BroadcastReceiverHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "onReceive");
            Utils.debugIntent(intent, TAG);

            if (mAttachedUsername != null) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey("networkInfo")) {
                    NetworkInfo networkInfo2 = (NetworkInfo) extras.get("networkInfo");

                    ChatController cc = getChatController(mAttachedUsername);
                    if (cc != null) {

                        if (networkInfo2.getState() == NetworkInfo.State.CONNECTED) {
                            SurespotLog.d(TAG, "onReceive,  CONNECTED");
                            synchronized (this) {
                                cc.clearError();
                                cc.connect();
                                cc.processNextMessage();
                            }
                            return;
                        }

                        if (networkInfo2.getState() == NetworkInfo.State.DISCONNECTED) {
                            SurespotLog.d(TAG, "onReceive,  DISCONNECTED");
                            synchronized (this) {
                                cc.disconnect();
                                cc.processNextMessage();
                            }
                        }
                    }
                }
            }
        }
    }

    public static synchronized String getLoggedInUser() {
        return mAttachedUsername;
    }
}
