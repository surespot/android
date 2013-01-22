package com.twofours.surespot.ui.activities;

import java.security.acl.LastOwnerException;
import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
		} else {
			Log.v(TAG, "GCM already registered.");
		}

		// NetworkController.unregister(this, regId);
		if (EncryptionController.hasIdentity()) {

			Intent intent = getIntent();
			String action = intent.getAction();
			String type = intent.getType();
			Bundle extras = intent.getExtras();
			Set<String> categories = intent.getCategories();

			Log.v(TAG, "Intent action: " + action);
			Log.v(TAG, "Intent type: " + type);
			Log.v(TAG, "Intent categories: " + (categories == null ? "null" : categories.toString()));
			Log.v(TAG, "Extras: " + (extras == null ? "null" : extras.toString()));

			// if we have a session assume we're logged in
			if (NetworkController.hasSession()) {
				// make sure the gcm is set
				// use case:
				// user signs-up without google account (unlikely)
				// user creates google account
				// user opens app again, we have session so neither login or add user is called (which would set the gcm)

				// so we need to upload the gcm here if we haven't already

				NetworkController.registerGcmId(new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int arg0, String arg1) {
						Log.v(TAG, "GCM registered in surespot server");
					}

					@Override
					public void onFailure(Throwable arg0, String arg1) {
						Log.e(TAG, arg0.toString());
					}

				});

				Intent newIntent = null;
				// if we have a chat intent go to chat
				String intentName = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);

				// if we have an intent name it's coming from a notification so show the chat
				if (intentName != null) {
					newIntent = new Intent(this, ChatActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, intentName);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					
				} else {
					//we have a send action so start friend activity so user can pick someone to send to
					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						newIntent = new Intent(this, FriendActivity.class);
						newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);
					} else {
						//we saved a chat name so load the chat activity with that name
						String lastName = Utils.getSharedPrefsString(SurespotConstants.PrefNames.LAST_CHAT);
						if (lastName != null) {
							newIntent = new Intent(this, ChatActivity.class);
							newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, lastName);
							newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						}

					}
					
				}
				
				if (newIntent == null) {
					newIntent = new Intent(this, FriendActivity.class);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				}
				startActivity(newIntent);
			} else {
				// identity but no session, login
				Intent newIntent = new Intent(this, LoginActivity.class);
				String name = intent.getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
				if (name != null) {
					newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
				} else {
					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);
					}

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

	void handleSendText(Intent intent) {
		String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
		if (sharedText != null) {
			// Update UI to reflect text being shared
		}
	}

	void handleSendImage(Intent intent) {
		Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (imageUri != null) {
			// Update UI to reflect image being shared
		}
	}

	void handleSendMultipleImages(Intent intent) {
		ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		if (imageUris != null) {
			// Update UI to reflect multiple images being shared
		}
	}

}
