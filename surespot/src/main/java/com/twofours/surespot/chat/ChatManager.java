package com.twofours.surespot.chat;

import android.app.FragmentManager;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.MenuItem;

import com.twofours.surespot.common.SurespotLog;
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

    public static synchronized ChatController getChatController(String username) {
        if (TextUtils.isEmpty(username)) {
            throw new RuntimeException("null username");
        }
        ChatController nc = mMap.get(username);
        if (nc == null) {
            SurespotLog.d(TAG, "creating chat controller for %s", username);
            nc = new ChatController(username);
            mMap.put(username,nc);
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
        cc.attach(context,viewPager,fm, pageIndicator, menuItems, progressCallback,sendIntentCallback,tabShowingCallback);
        return cc;
    }

    public static boolean isUIAttached() {
        return !mUIAttached;
    }
}
