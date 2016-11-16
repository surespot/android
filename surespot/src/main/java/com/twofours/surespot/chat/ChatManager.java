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

    private static ChatController mAttachedChatController;
    private static BroadcastReceiverHandler mConnectivityReceiver;
    private static Context mContext;
    private static boolean mPaused;

    public static synchronized ChatController getChatController(String username) {
        if (TextUtils.isEmpty(username)) {
            throw new RuntimeException("null username");
        }
        ChatController nc = mMap.get(username);
        if (nc == null) {
            SurespotLog.d(TAG, "creating chat controller for %s", username);
            nc = new ChatController(username);
            mMap.put(username, nc);
        }

        return nc;
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
        ChatController cc = getChatController(username);

        cc.attach(context, viewPager, fm, pageIndicator, menuItems, progressCallback, sendIntentCallback, tabShowingCallback, listener);
        mAttachedChatController = cc;
        if (mConnectivityReceiver == null) {
            SurespotLog.d(TAG, "attachChatController, username: %s registering new broadcast receiver", username);
            mConnectivityReceiver = new BroadcastReceiverHandler();
            context.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            mContext = context;
        }

        return cc;
    }

    public static boolean isChatControllerAttached(String username) {
        if (mAttachedChatController != null) {
            return (mAttachedChatController.getUsername().equals(username));
        }
        return false;
    }


    public static synchronized void detach(Context context) {
        SurespotLog.d(TAG, "detach");
        if (mConnectivityReceiver != null) {
            if (mContext == context) {
                SurespotLog.d(TAG, "detach, unregistering broadcast receiver");
                mContext.unregisterReceiver(mConnectivityReceiver);
                mConnectivityReceiver = null;
            }
        }
    }

    public static synchronized void pause(String username) {
        mPaused = true;
        if (mAttachedChatController != null && mAttachedChatController.getUsername().equals(username)) {
            mAttachedChatController.save();
            mAttachedChatController.disconnect();
        }
    }

    public static synchronized void resume(String username) {
        mPaused = false;
        if (mAttachedChatController != null && mAttachedChatController.getUsername().equals(username)) {
            mAttachedChatController.resume();
        }
    }

    public static synchronized boolean isUIAttached() {
        return !mPaused;
    }

    public static synchronized void resetState() {
        mMap.clear();
        mAttachedChatController = null;
        mConnectivityReceiver = null;
        FileUtils.wipeFileUploadDir(mContext);
    }

    private static class BroadcastReceiverHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "onReceive");
            Utils.debugIntent(intent, TAG);

            if (mAttachedChatController != null) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey("networkInfo")) {
                    NetworkInfo networkInfo2 = (NetworkInfo) extras.get("networkInfo");
                    if (networkInfo2.getState() == NetworkInfo.State.CONNECTED) {
                        SurespotLog.d(TAG, "onReceive,  CONNECTED");
                        synchronized (this) {
                            mAttachedChatController.clearError();
                            mAttachedChatController.connect();
                            mAttachedChatController.processNextMessage();
                        }
                        return;
                    }

                    if (networkInfo2.getState() == NetworkInfo.State.DISCONNECTED) {
                        SurespotLog.d(TAG, "onReceive,  DISCONNECTED");
                        synchronized (this) {
                            mAttachedChatController.disconnect();
                            mAttachedChatController.processNextMessage();
                        }
                    }
                }
            }
        }
    }

    public static synchronized String getLoggedInUser() {
        if (mAttachedChatController != null) {
            return mAttachedChatController.getUsername();
        }
        else {
            return null;
        }
    }
}
