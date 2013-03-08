package com.twofours.surespot.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class SettingsActivity extends SherlockPreferenceActivity {
	private static final String TAG = "SettingsActivity";

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
			
			PackageManager manager = this.getPackageManager();
			PackageInfo info = null;
			try {
				info = manager.getPackageInfo(this.getPackageName(), 0);				
				Preference version = prefMgr.findPreference("pref_version");
				version.setTitle("version: " + info.versionName);
			}
			catch (NameNotFoundException e) {
				SurespotLog.w(TAG,"onCreate", e);
			}
			
		
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
};
