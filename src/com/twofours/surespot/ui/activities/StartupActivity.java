package com.twofours.surespot.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.NetworkController;

public class StartupActivity extends Activity {
	private static final String TAG = "StartupActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		GCMRegistrar.checkDevice(this);
		GCMRegistrar.checkManifest(this);

		final String regId = GCMRegistrar.getRegistrationId(this);
		// boolean registered = GCMRegistrar.isRegistered(this);
		// boolean registeredOnServer = GCMRegistrar.isRegisteredOnServer(this);
		if (regId.equals("")) {
			Log.v(TAG, "Registering for GCM.");
			GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
		}
		else {
			Log.v(TAG, "GCM already registered.");
		}

		// NetworkController.unregister(this, regId);
		if (EncryptionController.hasIdentity()) {
			// if we have a session assume we're logged in 
			if (NetworkController.hasSession()) {
				//make sure the gcm is set 
				//use case:
				//user signs-up without google account (unlikely)
				//user creates google account
				//user opens app again, we have session so neither login or add user is called (which would set the gcm)

				//so we need to upload the gcm here if we haven't already
			
				NetworkController.registerGcmId(new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int arg0, String arg1) {
						Log.v(TAG,"GCM registered in surespot server");
					}
					
					@Override
					public void onFailure(Throwable arg0, String arg1) {
						Log.e(TAG,arg0.toString());
					}
					
				});
				
				
				Intent intent;
				// if we have a chat intent go to chat
				String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
				
				//if we don't have an intent, see if we have saved chat
				if (name == null) {
					name = Utils.getSharedPrefsString(SurespotConstants.PrefNames.LAST_CHAT);
				}				
				
				if (name != null) {
					intent = new Intent(this, ChatActivity.class);
					intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
				}
				else {
					// go to main					
					intent = new Intent(this, FriendActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
				}				
			}
			else {
				// identity but no session, login
				Intent intent = new Intent(this, LoginActivity.class);
				String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
				if (name != null) {
					intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
				}				

				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		}
		// otherwise show the user / key management activity
		else {
			Intent intent = new Intent(this, SignupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}

		finish();
	}

}
