package com.twofours.surespot.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.twofours.surespot.SurespotApplication;

public class StartupActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// if we have a key pair show the login activity
		if (SurespotApplication.getEncryptionController().hasIdentity()) {
			Intent intent = new Intent(this, LoginActivity.class);
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
