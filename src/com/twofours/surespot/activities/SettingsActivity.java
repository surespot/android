package com.twofours.surespot.activities;

import android.os.Bundle;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.common.Utils;

public class SettingsActivity extends SherlockPreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// TODO put in fragment
		PreferenceManager prefMgr = getPreferenceManager();
		String user = IdentityController.getLoggedInUser();
		if (user != null) {
			prefMgr.setSharedPreferencesName(user);
			addPreferencesFromResource(R.xml.preferences);
			Utils.configureActionBar(this, "settings", user, true);
		}

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}
}
