package com.twofours.surespot;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//if we have a key pair show the login activity
		if (SurespotApplication.getEncryptionController().hasKeyPair()) {		
			startActivity(new Intent(this,LoginActivity.class));
		}
		//otherwise show the user / key management activity
		else {
			startActivity(new Intent(this,SignupActivity.class));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
