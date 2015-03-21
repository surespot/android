package com.twofours.surespot.network;

import android.content.Context;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;

public class NetworkHelper {
	private static final String TAG = "NetworkHelper";

	public static boolean reLogin(final Context context, final NetworkController networkController, final String username, final CookieResponseHandler cookieResponseHandler) {
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
						
						networkController.login(username, dPassword, signature, new CookieResponseHandler() {
							 
							@Override
							public void onSuccess(int responseCode, String result, Cookie cookie) {
								SurespotLog.d(TAG, "successfully re-logged in: %s", username);
								IdentityController.userLoggedIn(context, identity, cookie, password);
								cookieResponseHandler.onSuccess(responseCode, result, cookie);								
							}
							
							@Override
							public void onFailure(Throwable error, String content) {
								SurespotLog.d(TAG, "failed re-logging in: %s", username);
								cookieResponseHandler.onFailure(error, content);
							}
						});
					}
					return null;
				}
			}.execute();
			return true;
		}
		else {
			return false;
		}
		
	}
}
