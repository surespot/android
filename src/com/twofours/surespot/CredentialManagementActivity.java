package com.twofours.surespot;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class CredentialManagementActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_credential_management);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_credential_management, menu);
		return true;
	}

}
