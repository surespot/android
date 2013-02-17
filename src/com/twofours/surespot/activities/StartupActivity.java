package com.twofours.surespot.activities;

import java.util.Set;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;

public class StartupActivity extends Activity {
	private static final String TAG = "StartupActivity";

	private CredentialCachingService mCredentialCachingService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		Intent intent = new Intent(this, CredentialCachingService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
			SurespotLog.v(TAG, "caching service bound");
			CredentialCachingBinder binder = (CredentialCachingBinder) service;
			mCredentialCachingService = binder.getService();

			// make sure these are there so startup code can execute
			SurespotApplication.setCachingService(mCredentialCachingService);
			SurespotApplication.setNetworkController(new NetworkController(StartupActivity.this));
			startup();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void startup() {
		try {
			// device without GCM throws exception
			GCMRegistrar.checkDevice(this);
			GCMRegistrar.checkManifest(this);

			final String regId = GCMRegistrar.getRegistrationId(this);
			// boolean registered = GCMRegistrar.isRegistered(this);
			// boolean registeredOnServer = GCMRegistrar.isRegisteredOnServer(this);
			if (regId.equals("")) {
				SurespotLog.v(TAG, "Registering for GCM.");
				GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
			}
			else {
				SurespotLog.v(TAG, "GCM already registered.");
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "onCreate", e);
		}

		// NetworkController.unregister(this, regId);

		// if we have any users
		if (IdentityController.hasIdentity()) {

			Intent intent = getIntent();
			String action = intent.getAction();
			String type = intent.getType();
			Bundle extras = intent.getExtras();
			Set<String> categories = intent.getCategories();
			String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
			String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
			String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

			Utils.logIntent(intent);

			// if we have a current user we're logged in
			String user = IdentityController.getLoggedInUser();

			// make sure the gcm is set
			// use case:
			// user signs-up without google account (unlikely)
			// user creates google account
			// user opens app again, we have session so neither login or add user is called (which would set the gcm)

			// so we need to upload the gcm here if we haven't already

			if (user != null) {
				NetworkController networkController = SurespotApplication.getNetworkController();
				if (networkController != null) {
					networkController.registerGcmId(new AsyncHttpResponseHandler() {

						@Override
						public void onSuccess(int arg0, String arg1) {
							SurespotLog.v(TAG, "GCM registered in surespot server");
						}

						@Override
						public void onFailure(Throwable arg0, String arg1) {
							SurespotLog.e(TAG, arg0.toString(), arg0);
						}

					});
				}
			}

			Intent newIntent = null;

			// if we have a message to the currently logged in user, set the from and start the chat activity
			if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {
				if (messageTo.equals(user)) {
					SurespotLog.v(TAG, "found chat name, starting chat activity: " + messageTo);
					newIntent = new Intent(this, ChatActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE, notificationType);
					newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, messageFrom);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				}
				else {
					SurespotLog.v(TAG, "need different user, starting Login activity");
					// identity but no session, login
					newIntent = new Intent(this, LoginActivity.class);
					SurespotLog.v(TAG, "setting message to, " + messageFrom + ", from: " + messageTo);
					newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, messageTo);
					newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, messageFrom);
					newIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE, notificationType);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				}

			}
			else {
				// we have a send action so start friend activity so user can pick someone to send to
				if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
					SurespotLog.v(TAG, "send action, starting home activity so user can select recipient");
					newIntent = new Intent(this, FriendActivity.class);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
					newIntent.setAction(action);
					newIntent.setType(type);
					newIntent.putExtras(intent);
				}
				else {
					if (SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType)
							|| SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType)) {

						if (!messageTo.equals(user)) {
							SurespotLog.v(TAG, "need different user, starting Login activity");
							newIntent = new Intent(this, LoginActivity.class);
							newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
									| Intent.FLAG_ACTIVITY_CLEAR_TOP);
							newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, messageTo);
							newIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE,
									SurespotConstants.IntentFilters.INVITE_NOTIFICATION);
						}
					}
					else {
						// we saved a chat name so load the chat activity with that name
						String lastName = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT);
						if (lastName != null) {
							SurespotLog.v(TAG, "starting chat activity based on LAST_CHAT name");
							newIntent = new Intent(this, ChatActivity.class);
							newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, lastName);
							newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
									| Intent.FLAG_ACTIVITY_CLEAR_TOP);
						}
					}
				}
			}

			if (newIntent == null) {
				if (user == null) {
					newIntent = new Intent(this, LoginActivity.class);
				}
				else {
					newIntent = new Intent(this, FriendActivity.class);
				} // newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			}

			startActivity(newIntent);
		}
		// otherwise show the user / key management activity
		else {
			SurespotLog.v(TAG, "starting signup activity");
			Intent intent = new Intent(this, SignupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}

		finish();
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		unbindService(mConnection);
	}
}
