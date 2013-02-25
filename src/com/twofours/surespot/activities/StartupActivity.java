package com.twofours.surespot.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.chat.ChatController;
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

		SurespotApplication.setStartupIntent(getIntent());
		Utils.logIntent(TAG, getIntent());

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
			SurespotLog.v(TAG, "caching service bound");
			CredentialCachingBinder binder = (CredentialCachingBinder) service;
			mCredentialCachingService = binder.getService();

			// make sure these are there so startup code can execute
			SurespotApplication.setCachingService(mCredentialCachingService);
			SurespotApplication.setNetworkController(new NetworkController(getApplicationContext()));
			SurespotApplication.setChatController(new ChatController(getApplicationContext()));
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
		Intent intent = getIntent();
		// if we have any users or we've used the signup intent to workaround can't clear back stack insanity
		if (IdentityController.hasIdentity() || intent.getBooleanExtra("signup", false)) {

			// if we have a current user we're logged in
			String user = IdentityController.getLoggedInUser();

			String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
			String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);

			// if we have a message to the currently logged in user, set the from and start the chat activity
			if ((user == null)
					|| (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType) || (SurespotConstants.IntentFilters.INVITE_REQUEST
							.equals(notificationType) || (SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType))
							&& (!messageTo.equals(user))))) {

				SurespotLog.v(TAG, "need a (different) user, showing login");
				Intent newIntent = new Intent(this, LoginActivity.class);
				startActivityForResult(newIntent, SurespotConstants.IntentRequestCodes.LOGIN);
			}
			else {
				launch(getIntent());
			}

		}
		// otherwise show the signup activity
		else {
			SurespotLog.v(TAG, "starting signup activity");
			Intent newIntent = new Intent(this, SignupActivity.class);
			// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(newIntent);
			// finish();
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);
		if (requestCode == SurespotConstants.IntentRequestCodes.LOGIN && resultCode == RESULT_OK) {
			launch(data);
		}
	}

	private void launch(Intent intent) {

		NetworkController networkController = SurespotApplication.getNetworkController();
		if (networkController != null) {
			// make sure the gcm is set

			// use case:
			// user signs-up without google account (unlikely)
			// user creates google account
			// user opens app again, we have session so neither login or add user is called (which would set the gcm)

			// so we need to upload the gcm here if we haven't already

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

		String action = intent.getAction();
		String type = intent.getType();

		String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
		String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

		Intent newIntent = null;
		String lastName = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
		// if we have a message to the currently logged in user, or we don't need to pick someone to send to, set the from and start the
		// chat activity
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)
				|| (!Intent.ACTION_SEND.equals(action) && lastName != null)) {

			SurespotLog.v(TAG, "found chat name, starting chat activity, to: " + messageTo + ", from: " + messageFrom);

			TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
			stackBuilder.addParentStack(FriendActivity.class);

			newIntent = new Intent(this, ChatActivity.class);
			if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {
				newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, messageFrom);
			}
			else {
				newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, lastName);
			}
			// newIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

			stackBuilder.addNextIntent(newIntent);
			stackBuilder.startActivities();
			// finish();

		}
		else {
			newIntent = new Intent(this, FriendActivity.class);
		}

		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
			newIntent.setAction(action);
			newIntent.setType(type);

			// newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, messageFrom);
			newIntent.putExtras(intent);
		}

		newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(newIntent);
		finish();
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		unbindService(mConnection);
	}
}
