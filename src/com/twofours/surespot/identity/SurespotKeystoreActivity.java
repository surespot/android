package com.twofours.surespot.identity;

import org.nick.androidkeystore.android.security.KeyStore;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class SurespotKeystoreActivity extends Activity {

	private static final String TAG = "SurespotKeystoreActivity";
	private String mUsername;
	private String mPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Utils.logIntent(TAG, getIntent());
		
		if (savedInstanceState != null)  {
			mUsername = savedInstanceState.getString("username");
			mPassword = savedInstanceState.getString("password");					
		}
		else {
			mUsername = getIntent().getStringExtra("username");
			mPassword = getIntent().getStringExtra("password");
		}
				
		this.unlock(this);

	}

	private void unlock(Activity activity) {
		KeyStore keystore = IdentityController.getKeystore();
		if (keystore == null) {
			finish();
			return;
		}
		if (keystore.state() == KeyStore.State.UNLOCKED) {
			Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
			if (mUsername != null && mPassword != null) {
				keystore.put(mUsername, mPassword.getBytes());
			}
			finish();
			return;
		}

		try {
			Intent intent = null;
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				intent = new Intent(IdentityController.OLD_UNLOCK_ACTION);
			}
			else {
				intent = new Intent(IdentityController.UNLOCK_ACTION);
			}
			Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
			this.startActivityForResult(intent, 100);
		}
		catch (ActivityNotFoundException e) {
			SurespotLog.e(TAG, e, "No UNLOCK activity: %s", e.getMessage());
			Utils.makeLongToast(activity, activity.getString(R.string.keystore_not_supported));
			Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
			finish();
			return;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		SurespotLog.d(TAG, "received activity result, requestCode: %d, resultcode; %d, data: %s", requestCode, resultCode, data);
		Utils.logIntent(TAG, data);

		boolean unlocked = IdentityController.isKeystoreUnlocked();
		
		SurespotLog.d(TAG, "keystore unlocked: %b", unlocked);

		if (IdentityController.isKeystoreUnlocked()) {
			if (mUsername != null && mPassword != null) {
				IdentityController.getKeystore().put(mUsername, mPassword.getBytes());
			}
		}
		else {			
			Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
		}
		finish();

	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mUsername != null) {
			outState.putString("username", mUsername);
		}
		if (mPassword != null) {
			outState.putString("password", mPassword);
		}

	}
	

}
