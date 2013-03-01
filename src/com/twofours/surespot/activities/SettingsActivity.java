package com.twofours.surespot.activities;

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
							//MainActivity.getStateController().saveLastViewedMessageIds(null);

							// wipe active chats
							//MainActivity.getStateController().saveActiveChats(null);

							// last chat and user we had open
							Utils.putSharedPrefsString(SettingsActivity.this, SurespotConstants.PrefNames.LAST_CHAT, null);
							Utils.putSharedPrefsString(SettingsActivity.this, SurespotConstants.PrefNames.LAST_USER, null);

							// network caches
							MainActivity.getNetworkController().clearCache();

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
