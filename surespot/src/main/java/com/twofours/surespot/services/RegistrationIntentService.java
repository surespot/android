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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.Utils;

import java.util.List;


public class RegistrationIntentService extends JobIntentService {

    private static final String TAG = "RegIntentService";

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, RegistrationIntentService.class, SurespotConstants.IntentRequestCodes.FCM_REGISTRATION, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        onHandleIntent(intent);
    }

    protected void onHandleIntent(Intent intent) {
        try {
            FirebaseInstanceId.getInstance().getInstanceId()
                    .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                        @Override
                        public void onComplete(@NonNull Task<InstanceIdResult> task) {
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "getInstanceId failed", task.getException());
                                return;
                            }

                            // Get new Instance ID token
                            String token = task.getResult().getToken();
                            // [END get_token]
                            SurespotLog.i(TAG, "FCM Registration Token: " + token);

                            SurespotLog.i(TAG, "Received FCM token, saving it in shared prefs.");
                            Utils.putSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.GCM_ID_RECEIVED, token);
                            Utils.putSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.APP_VERSION, SurespotApplication.getVersion());

                            sendRegistrationToServer(token);
                        }
                    });

        }
        catch (Exception e) {
            SurespotLog.i(TAG, e, "Failed to complete token refresh");
        }
    }

    /**
     * Persist registration to third-party servers.
     * <p/>
     * Modify this method to associate the user's GCM registration token with any server-side account
     * maintained by your application.
     *
     * @param id The new token.
     */
    private void sendRegistrationToServer(final String id) {
        //todo use ChatManager

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                List<String> usernames = IdentityController.getIdentityNames(RegistrationIntentService.this);
                for (String username : usernames) {
                    NetworkManager.getNetworkController(RegistrationIntentService.this, username).registerGcmId(RegistrationIntentService.this, id);
                }
            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
    }
}
