package com.twofours.surespot.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class SettingsActivity extends SherlockPreferenceActivity {
	private static final String TAG = "SettingsActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		OnPreferenceClickListener onPreferenceClickListener = new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				SurespotApplication.mBackupManager.dataChanged();
				return true;
			}
		};

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

				//prefMgr.findPreference("pref_auto_android_backup_enabled").setOnPreferenceClickListener(onPreferenceClickListener);
				prefMgr.findPreference(getString(R.string.pref_notifications_enabled)).setOnPreferenceClickListener(
						onPreferenceClickListener);
				prefMgr.findPreference(getString(R.string.pref_notifications_sound))
						.setOnPreferenceClickListener(onPreferenceClickListener);
				prefMgr.findPreference(getString(R.string.pref_notifications_vibration)).setOnPreferenceClickListener(
						onPreferenceClickListener);
				prefMgr.findPreference(getString(R.string.pref_notifications_led)).setOnPreferenceClickListener(onPreferenceClickListener);

//				prefMgr.findPreference("pref_logging").setOnPreferenceClickListener(new OnPreferenceClickListener() {
//
//					@Override
//					public boolean onPreferenceClick(Preference preference) {
//						SurespotLog.setLogging(((CheckBoxPreference) preference).isChecked());
//						return true;
//					}
//				});

				prefMgr.findPreference("pref_crash_reporting").setOnPreferenceClickListener(new OnPreferenceClickListener() {

					@Override
					public boolean onPreferenceClick(Preference preference) {
						SurespotLog.setCrashReporting(((CheckBoxPreference) preference).isChecked());
						return true;
					}
				});
			}
			catch (NameNotFoundException e) {
				SurespotLog.w(TAG, "onCreate", e);
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
