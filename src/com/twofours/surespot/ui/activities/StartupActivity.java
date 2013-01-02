package com.twofours.surespot.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;

public class StartupActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		
		//if we have a key pair show the login activity
		if (SurespotApplication.getEncryptionController().hasKeyPair()) {		
			startActivity(new Intent(this,LoginActivity.class));
		}
		//otherwise show the user / key management activity
		else {
			startActivity(new Intent(this,SignupActivity.class));
		}
	}

	

}
