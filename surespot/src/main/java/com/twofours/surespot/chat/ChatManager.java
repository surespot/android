package com.twofours.surespot.chat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.MenuItem;

import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.Utils;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by adam on 11/14/16.
 */

public class ChatManager {
    private static String TAG = "ChatManager";
    private static HashMap<String, ChatController> mChatControllers = new HashMap<>();
    private static HashMap<Integer, BroadcastReceiverHandler> mHandlers = new HashMap<>();
    private static boolean mPaused;
    private static String mAttachedUsername;
    private static NetworkInfo mActiveNetworkInfo;
    //private static HashSet<Integer> mIds = new HashSet<>(5);


    public static synchronized ChatController getChatController(String username) {
        if (TextUtils.isEmpty(username)) {
            return null;
        }

        return mChatControllers.get(username);
    }

    public static synchronized ChatController attachChatController(Activity context,
                                                                   String username,
                                                                   int id,
                                                                   ViewPager viewPager,
                                                                   FragmentManager fm,
                                                                   TitlePageIndicator pageIndicator,
                                                                   ArrayList<MenuItem> menuItems,
                                                                   IAsyncCallback<Boolean> progressCallback,
                                                                   IAsyncCallback<Void> sendIntentCallback,
                                                                   IAsyncCallback<Friend> tabShowingCallback,
                                                                   IAsyncCallback<Object> listener401) {
        SurespotLog.d(TAG, "attachChatController %d, username: %s", id, username);

        ChatController cc = mChatControllers.get(username);
        if (cc == null) {
            SurespotLog.d(TAG, "creating chat controller for %s", username);
            cc = new ChatController(context, username);
            mChatControllers.put(username, cc);
        }

        cc.attach(context, viewPager, fm, pageIndicator, menuItems, progressCallback, sendIntentCallback, tabShowingCallback, listener401);
        mAttachedUsername = username;

        //set connectivity state
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        mActiveNetworkInfo = cm.getActiveNetworkInfo();
        if (mActiveNetworkInfo != null) {
            SurespotLog.d(TAG, "attachChatController current active active network type %s", mActiveNetworkInfo.getTypeName());
        }


        BroadcastReceiverHandler handler = mHandlers.get(id);
        if (handler == null) {
            SurespotLog.d(TAG, "attachChatController %d, username: %s registering new broadcast receiver", id, username);
            handler = new BroadcastReceiverHandler();
            mHandlers.put(id, handler);
            try {
                context.registerReceiver(handler, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
            catch (Exception e) {
                SurespotLog.w(TAG, e, "attachChatController");
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


    public static synchronized void detach(Context context, int id) {
        SurespotLog.d(TAG, "detach %d", id);
        BroadcastReceiverHandler handler = mHandlers.get(id);
        if (handler != null) {
            SurespotLog.d(TAG, "detach, unregistering broadcast receiver");
            try {
                context.unregisterReceiver(handler);
            }
            catch (Exception e) {
                SurespotLog.w(TAG, e, "detach");
            }
            mHandlers.remove(id);
        }
    }

    public static synchronized void pause(String username, int id) {
        mPaused = true;
        //   mIds.remove(id);
        SurespotLog.d(TAG, "paused %d", id);
        ChatController cc = getChatController(username);
        if (cc != null) {
            cc.setMainActivityPaused();
            cc.save();
            cc.disconnect();
        }
    }

    public static synchronized void resume(String username, int id) {
        mPaused = false;
        // mIds.add(id);
        SurespotLog.d(TAG, "resumed %d", id);
        ChatController cc = getChatController(username);
        if (cc != null) {
            cc.resume();
        }
    }

    public static synchronized boolean isUIAttached() {
        return !mPaused;
        //return mIds.size() > 0;
    }

    public static synchronized void resetState(Context context) {
        mChatControllers.clear();
        mAttachedUsername = null;
        //mHandlers.clear();
        FileUtils.wipeFileUploadDir(context);
    }

    private static class BroadcastReceiverHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "Broadcast Receiver onReceive, isUIAttached: %b", isUIAttached());
            Utils.debugIntent(intent, TAG);

            Bundle extras = intent.getExtras();
            if (extras.containsKey("networkInfo")) {
                NetworkInfo newActiveNetwork = (NetworkInfo) extras.get("networkInfo");
                if (newActiveNetwork != null) {
                    if (newActiveNetwork.getState() == NetworkInfo.State.CONNECTED) {
                        SurespotLog.d(TAG, "onReceive,  CONNECTED");
                        boolean disconnect = false;
                        //if network different than active network, disconnect
                        if (mActiveNetworkInfo != null) {
                            SurespotLog.d(TAG, "Current active active network type %s, new active network type: %s", mActiveNetworkInfo.getTypeName(), newActiveNetwork.getTypeName());
                            if (newActiveNetwork.getType() != mActiveNetworkInfo.getType()) {
                                SurespotLog.d(TAG, "new active network different will disconnect socket if UI showing for chatcontroller for %s", mAttachedUsername);
                                disconnect = true;
                            }
                        }
                        mActiveNetworkInfo = newActiveNetwork;

                        if (isUIAttached() && mAttachedUsername != null) {
                            ChatController cc = getChatController(mAttachedUsername);
                            if (cc != null) {
                                synchronized (this) {
                                    if (disconnect) {
                                        SurespotLog.d(TAG, "disconnecting socket for %s", mAttachedUsername);
                                        cc.disconnect();
                                    }
                                    cc.clearError();
                                    cc.connect();
                                    cc.processNextMessage();
                                }
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
