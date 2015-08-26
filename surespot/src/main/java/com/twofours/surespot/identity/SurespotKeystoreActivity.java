package com.twofours.surespot.identity;

import org.nick.androidkeystore.android.security.KeyStore;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
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

	private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

	private KeyguardManager mKeyguardManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Utils.logIntent(TAG, getIntent());			
		this.unlock(this);
	}

	@TargetApi(Build.VERSION_CODES.M)
	private void unlockM() {
		mKeyguardManager = (KeyguardManager) getSystemService(this.KEYGUARD_SERVICE);
		if (!mKeyguardManager.isKeyguardSecure()) {
			// Show a message that the user hasn't set up a lock screen.
			// TODO: string resource
			Utils.makeLongToast(this, "Secure lock screen hasn't set up.\n" + "Go to 'Settings -> Security -> Screenlock' to set up a lock screen");
			return;
		}
		Intent intent = mKeyguardManager.createConfirmDeviceCredentialIntent(null, null);
		if (intent != null) {
			startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
			// Challenge completed, proceed with using cipher
			if (resultCode == RESULT_OK) {
				Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
				setResult(resultCode);
				finishActivity(REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
			} else {
				// The user canceled or didn’t complete the lock screen
				// operation. Go to error/cancellation flow.
				Utils.makeLongToast(this, this.getString(R.string.keystore_not_unlocked));
				Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
				setResult(resultCode);
				finishActivity(REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
			}
		}
	}


	private void unlock(Activity activity) {

		if (IdentityController.USE_PUBLIC_KEYSTORE_M) {
			unlockM();
			return;
		}

		KeyStore keystore = IdentityController.getKeystore();
		if (keystore == null) {
			finish();
			return;
		}
		if (keystore.state() == KeyStore.State.UNLOCKED) {
			Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
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
			this.startActivity(intent);
		}
		catch (ActivityNotFoundException e) {
			SurespotLog.e(TAG, e, "No UNLOCK activity: %s", e.getMessage());
			Utils.makeLongToast(activity, activity.getString(R.string.keystore_not_supported));
			Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
			finish();
			return;
		}
	}
}
