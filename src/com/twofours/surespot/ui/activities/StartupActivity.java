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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		GCMRegistrar.checkDevice(this);
		GCMRegistrar.checkManifest(this);

		final String regId = GCMRegistrar.getRegistrationId(this);
		if (regId.equals("")) {
			GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
		}
		else {
			Log.v("StartupActivity", "GCM Already registered");
		}

		// save the id if it's not there
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		String gcmId = settings.getString(SurespotConstants.GCM_ID, null);
		if (gcmId == null) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(SurespotConstants.GCM_ID, regId);
			editor.commit();
		}

		// NetworkController.unregister(this, regId);

		// if we have a key pair show the login activity
		if (EncryptionController.hasIdentity()) {
			Intent intent = new Intent(this, LoginActivity.class);

			String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
			if (name != null) {
				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
			}
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
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
