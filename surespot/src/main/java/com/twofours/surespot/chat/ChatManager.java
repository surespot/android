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
    private static boolean mUIAttached;
    private static boolean mMainActivityPaused;
    private static ChatController mActiveChatController;
    private static BroadcastReceiverHandler mConnectivityReceiver;

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
                                                                   IAsyncCallback<Friend> tabShowingCallback) {
        ChatController cc = getChatController(username);

        cc.attach(context, viewPager, fm, pageIndicator, menuItems, progressCallback, sendIntentCallback, tabShowingCallback);
        mActiveChatController = cc;
        if (mConnectivityReceiver == null) {
            mConnectivityReceiver= new BroadcastReceiverHandler();
        }
        context.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        return cc;
    }

    public static boolean isUIAttached() {
        return !mUIAttached;
    }

    // sets if the main activity is paused or not
    public static void setMainActivityPaused(boolean paused) {
        mMainActivityPaused = paused;

        if (mActiveChatController != null) {
            if (paused) {
                mActiveChatController.save();
                mActiveChatController.disconnect();
            } else {
                mActiveChatController.connect();
            }
        }
    }

    private static class BroadcastReceiverHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "onReceive");
            Utils.debugIntent(intent, TAG);

            if (mActiveChatController != null) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey("networkInfo")) {
                    NetworkInfo networkInfo2 = (NetworkInfo) extras.get("networkInfo");
                    if (networkInfo2.getState() == NetworkInfo.State.CONNECTED) {
                        SurespotLog.d(TAG, "onReceive,  CONNECTED");
                        synchronized (this) {
                            mActiveChatController.clearError();
                            mActiveChatController.disconnect();
                            mActiveChatController.connect();
                            mActiveChatController.processNextMessage();
//                        mErrored = false;
//                        disconnect();
//                        connect();
//                        processNextMessage();
                        }
                        return;
                    }
                }
            }
        }

    }


}
