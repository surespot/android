package com.twofours.surespot.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.encryption.EncryptionController;

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

		// save the id if it's not there
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		String gcmId = settings.getString(SurespotConstants.GCM_ID, null);

		boolean gcmIdChanged = false;
		if (regId != null && regId.length() > 0 && (gcmId == null || !gcmId.equals(regId))) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(SurespotConstants.GCM_ID, regId);
			editor.commit();
			// should probably update this in the db too so add it to the login intent
			gcmIdChanged = true;

		}

		// NetworkController.unregister(this, regId);

		if (EncryptionController.hasIdentity()) {
			// if we have a session
			// TODO save password instead of session
			// if (NetworkController.hasSession()) {
			// Intent intent;
			// //if we have a chat intent go to chat
			// String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
			// if (name != null) {
			// intent = new Intent(this, ChatActivity.class);
			// intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
			// }
			// else {
			// // go to main
			// intent = new Intent(this, MainActivity.class);
			// }
			// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// startActivity(intent);
			// }
			// else {
			// identity but no session, login
			Intent intent = new Intent(this, LoginActivity.class);
			String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
			if (name != null) {
				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
			}

			if (gcmIdChanged) {
				intent.putExtra(SurespotConstants.ExtraNames.GCM_CHANGED, regId);
			}

			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			// }
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
