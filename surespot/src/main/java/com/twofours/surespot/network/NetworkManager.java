package com.twofours.surespot.network;

import android.text.TextUtils;

import com.twofours.surespot.common.SurespotLog;

import java.util.HashMap;

/**
 * Created by adam on 11/11/16.
 * Manage http client per user
 */

public class NetworkManager {
    private static HashMap<String, NetworkController> mMap = new HashMap<>();
    private static String TAG = "NetworkManager";

    public static synchronized NetworkController getNetworkController() {
        SurespotLog.d(TAG, "creating network controller for no user");
        return new NetworkController(null);
    }

    public static synchronized NetworkController getNetworkController(String username) {
        if (TextUtils.isEmpty(username)) {
            throw new RuntimeException("null username");
        }
        NetworkController nc = mMap.get(username);
        if (nc == null) {
            SurespotLog.d(TAG, "creating network controller for %s", username);
            nc = new NetworkController(username);
            mMap.put(username,nc);
        }
        return nc;
    }

    public static synchronized void clearCaches() {
        for (NetworkController nc : mMap.values()) {
            nc.clearCache();
        }
    }
}
