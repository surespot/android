/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twofours.surespot.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.impl.client.BasicCookieStore;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";
    public static final String SENDER_ID = "428168563991";
    private static final String[] TOPICS = {"global"};

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            // [START register_for_gcm]
            // Initially this call goes out to the network to retrieve the token, subsequent calls
            // are local.
            // [START get_token]
            InstanceID instanceID = InstanceID.getInstance(this);
            String token = instanceID.getToken(SENDER_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            // [END get_token]
            SurespotLog.i(TAG, "GCM Registration Token: " + token);

            SurespotLog.i(TAG, "Received gcm id, saving it in shared prefs.");
            Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.GCM_ID_RECEIVED, token);
            Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.APP_VERSION, SurespotApplication.getVersion());

            // TODO: Implement this method to send any registration to your app's servers.
            sendRegistrationToServer(token);

            // Subscribe to topic channels
            subscribeTopics(token);

            // You should store a boolean that indicates whether the generated token has been
            // sent to your server. If the boolean is false, send the token to your server,
            // otherwise your server should have already received the token.
//            sharedPreferences.edit().putBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, true).apply();
            // [END register_for_gcm]
        } catch (Exception e) {
            SurespotLog.i(TAG, e, "Failed to complete token refresh");
            // If an exception happens while fetching the new token or updating our registration data
            // on a third-party server, this ensures that we'll attempt the update at a later time.
            //  sharedPreferences.edit().putBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false).apply();
        }
        // Notify UI that registration has completed, so the progress indicator can be hidden.
        //Intent registrationComplete = new Intent(QuickstartPreferences.REGISTRATION_COMPLETE);
        //LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete);
    }

    /**
     * Persist registration to third-party servers.
     *
     * Modify this method to associate the user's GCM registration token with any server-side account
     * maintained by your application.
     *
     * @param id The new token.
     */
    private void sendRegistrationToServer(String id) {
        if (IdentityController.hasLoggedInUser()) {

            String username = IdentityController.getLoggedInUser();
            //see if it's different for this user
            String sentId = Utils.getUserSharedPrefsString(this, username, SurespotConstants.PrefNames.GCM_ID_SENT);

            if (id.equals(sentId)) {
                //if it's not different don't upload it
                SurespotLog.i(TAG, "GCM id already registered on surespot server.");
                return;
            }

            SurespotLog.i(TAG, "Attempting to register gcm id on surespot server.");
            // do this synchronously so android doesn't kill the service thread before it's done

            SyncHttpClient client = null;
            try {
                client = new SyncHttpClient(this) {

                    @Override
                    public String onRequestFailed(Throwable arg0, String arg1) {
                        SurespotLog.i(TAG, "Error saving gcmId on surespot server: " + arg1);
                        return "failed";
                    }
                };
            } catch (IOException e) {
                // TODO tell user shit is fucked
                return;
            }

            Cookie cookie = IdentityController.getCookieForUser(IdentityController.getLoggedInUser());
            if (cookie != null) {

                CookieStore cookieStore = new BasicCookieStore();
                cookieStore.addCookie(cookie);
                client.setCookieStore(cookieStore);

                Map<String, String> params = new HashMap<String, String>();
                params.put("gcmId", id);

                String result = client.post(SurespotConfiguration.getBaseUrl() + "/registergcm", new RequestParams(params));
                // success returns 204 = null result
                if (result == null) {
                    SurespotLog.i(TAG, "Successfully saved GCM id on surespot server.");

                    // the server and client match, we're golden
                    Utils.putUserSharedPrefsString(this, username, SurespotConstants.PrefNames.GCM_ID_SENT, id);
                }
            }
        } else {
            SurespotLog.i(TAG, "Can't save GCM id on surespot server as user is not logged in.");
        }
    }

    /**
     * Subscribe to any GCM topics of interest, as defined by the TOPICS constant.
     *
     * @param token GCM token
     * @throws IOException if unable to reach the GCM PubSub service
     */
    // [START subscribe_topics]
    private void subscribeTopics(String token) throws IOException {
        GcmPubSub pubSub = GcmPubSub.getInstance(this);
        for (String topic : TOPICS) {
            pubSub.subscribe(token, "/topics/" + topic, null);
        }
    }
    // [END subscribe_topics]

}
