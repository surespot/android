package com.twofours.surespot.network;

import android.content.Context;
import android.os.AsyncTask;

import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;

import okhttp3.Cookie;

public class NetworkHelper {
    private static final String TAG = "NetworkHelper";

    public static void reLogin(final Context context, final String username, final CookieResponseHandler cookieResponseHandler) {
        // if we have password login again and retry
        String pw = null;

        if (username != null) {
            pw = IdentityController.getStoredPasswordForIdentity(context, username);
        }
        final String password = pw;
        if (username != null && password != null) {

            SurespotLog.d(TAG, "password is in keystore, logging in %s", username);

            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {

                    final SurespotIdentity identity = IdentityController.getIdentity(context, username, password);
                    if (identity != null) {
                        byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
                        final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

                        String signature = EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword);

                        NetworkManager.getNetworkController(username).login(username, dPassword, signature, new CookieResponseHandler() {

                            @Override
                            public void onSuccess(int responseCode, String result, okhttp3.Cookie cookie) {
                                SurespotLog.d(TAG, "successfully re-logged in: %s", username);
                                IdentityController.userLoggedIn(context, identity, cookie, password);
                                cookieResponseHandler.onSuccess(responseCode, result, cookie);
                            }


                            @Override
                            public void onFailure(Throwable error, int code, String content) {
                                SurespotLog.d(TAG, "failed re-logging in: %s", username);
                                cookieResponseHandler.onFailure(error, code, content);
                            }
                        });
                    }
                    return null;
                }
            }.execute();

        }
        else {
            //send 401 as we don't have a password
            cookieResponseHandler.onFailure(null, 401, null);
        }
    }

    public static boolean reLoginSync(final Context context, final NetworkController networkController, final String username) {
        // if we have password login again and retry
        String pw = null;

        if (username != null) {
            pw = IdentityController.getStoredPasswordForIdentity(context, username);
        }
        final String password = pw;
        if (username != null && password != null) {

            SurespotLog.d(TAG, "password is in keystore, logging in %s", username);


            final SurespotIdentity identity = IdentityController.getIdentity(context, username, password);
            if (identity != null) {
                byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
                final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

                String signature = EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword);

                Cookie cookie = networkController.loginSync(username, dPassword, signature);
                if (cookie != null) {
                    IdentityController.userLoggedIn(context, identity, cookie, password);
                    return true;
                }
            }
        }

        return false;
    }
}
