package com.twofours.surespot.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
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

			Preference clearLocalCachePref = findPreference("pref_clear_local_cache");
			clearLocalCachePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {
					new AsyncTask<Void, Void, Void>() {
						protected Void doInBackground(Void... params) {
							// clear out some shiznit
							SurespotLog.v(TAG, "clearing local cache");

							// state
							for (String identityName : IdentityController.getIdentityNames(SettingsActivity.this)) {
								StateController.wipeState(SettingsActivity.this, identityName);
							}

							// wipe last viewed message ids
							SurespotApplication.getStateController().saveLastViewedMessageIds(null);

							// wipe active chats
							SurespotApplication.getStateController().saveActiveChats(null);

							// last chat and user we had open
							Utils.putSharedPrefsString(SettingsActivity.this, SurespotConstants.PrefNames.LAST_CHAT, null);
							Utils.putSharedPrefsString(SettingsActivity.this, SurespotConstants.PrefNames.LAST_USER, null);

							// network caches
							SurespotApplication.getNetworkController().clearCache();

							// captured image dir
							FileUtils.wipeImageCaptureDir(SettingsActivity.this);

							return null;

						}

						protected void onPostExecute(Void result) {
							Utils.makeToast(SettingsActivity.this, "local cache cleared");
						};

					}.execute();
					return true;

				}
			});

			Preference createIdentityPref = findPreference(getString(R.string.pref_create_identity));
			createIdentityPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {

					// logout and start signup activity
					IdentityController.logout();
					Intent intent = new Intent(SettingsActivity.this, SignupActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					finish();
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
};
