package com.twofours.surespot.activities;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.UIUtils;

public class SettingsActivity extends SherlockPreferenceActivity {
	private static final String TAG = "SettingsActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		OnPreferenceClickListener onPreferenceClickListener = new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				return true;
			}
		};

		// TODO put in fragment0
		PreferenceManager prefMgr = getPreferenceManager();
		String user = IdentityController.getLoggedInUser();
		if (user != null) {
			prefMgr.setSharedPreferencesName(user);

			addPreferencesFromResource(R.xml.preferences);
			Utils.configureActionBar(this, getString(R.string.settings), user, true);

			prefMgr.findPreference("pref_notifications_enabled").setOnPreferenceClickListener(onPreferenceClickListener);
			prefMgr.findPreference("pref_notifications_sound").setOnPreferenceClickListener(onPreferenceClickListener);
			prefMgr.findPreference("pref_notifications_vibration").setOnPreferenceClickListener(onPreferenceClickListener);
			prefMgr.findPreference("pref_notifications_led").setOnPreferenceClickListener(onPreferenceClickListener);

			prefMgr.findPreference("pref_help").setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {
					UIUtils.showHelpDialog(SettingsActivity.this, false);
					return true;
				}
			});
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

	//work around https://code.google.com/p/android/issues/detail?id=4611
	@SuppressWarnings("deprecation")
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		super.onPreferenceTreeClick(preferenceScreen, preference);
		if (preference != null)
			if (preference instanceof PreferenceScreen)
				if (((PreferenceScreen) preference).getDialog() != null)
					((PreferenceScreen) preference).getDialog().getWindow().getDecorView()
							.setBackgroundDrawable(this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
		return false;
	}
};
