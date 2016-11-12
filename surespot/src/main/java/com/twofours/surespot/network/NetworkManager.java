package com.twofours.surespot.network;

import java.util.HashMap;

/**
 * Created by adam on 11/11/16.
 * Manage http client per user
 */

public class NetworkManager {
    private static HashMap<String, NetworkController> mMap = new HashMap<>();

    public static synchronized NetworkController getNetworkController() {
        return new NetworkController(null);
    }

    public static synchronized NetworkController getNetworkController(String username) {
        NetworkController nc = mMap.get(username);
        if (nc == null) {
            nc = new NetworkController(username);
        }
        return nc;
    }

    public static synchronized void clearCaches() {
        for (NetworkController nc : mMap.values()) {
            nc.clearCache();
        }
    }
}
