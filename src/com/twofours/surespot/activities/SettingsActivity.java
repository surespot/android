package com.twofours.surespot.activities;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.backup.ImportIdentityActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
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
				//SurespotApplication.mBackupManager.dataChanged();
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
			prefMgr.findPreference("pref_notifications_vibration")
					.setOnPreferenceClickListener(onPreferenceClickListener);
			prefMgr.findPreference("pref_notifications_led").setOnPreferenceClickListener(onPreferenceClickListener);

			// prefMgr.findPreference("pref_logging").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			//
			// @Override
			// public boolean onPreferenceClick(Preference preference) {
			// SurespotLog.setLogging(((CheckBoxPreference) preference).isChecked());
			// return true;
			// }
			// });

			prefMgr.findPreference("pref_help").setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {
					// show help dialog
					AlertDialog.Builder b = new Builder(SettingsActivity.this);
					b.setIcon(R.drawable.surespot_logo).setTitle(getString(R.string.surespot_help));
					b.setPositiveButton(R.string.ok, new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					});

					AlertDialog ad = b.create();
					View view = LayoutInflater.from(SettingsActivity.this).inflate(R.layout.dialog_help, null);
					UIUtils.setHelpLinks(SettingsActivity.this, view);
					ad.setView(view, 0, 0, 0, 0);

					ad.show();

					return true;
				}
			});

			prefMgr.findPreference("pref_crash_reporting").setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {
					SurespotLog.setCrashReporting(((CheckBoxPreference) preference).isChecked());
					return true;
				}
			});

			prefMgr.findPreference("pref_import_identity").setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference) {
					if (IdentityController.getIdentityCount(SettingsActivity.this) < SurespotConstants.MAX_IDENTITIES) {
						Intent intent = new Intent(SettingsActivity.this, ImportIdentityActivity.class);
						startActivity(intent);
					}
					else {
						Utils.makeLongToast(SettingsActivity.this, getString(R.string.login_max_identities_reached, SurespotConstants.MAX_IDENTITIES));
					}					
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
