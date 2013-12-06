package com.twofours.surespot.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.UIUtils;

public class SettingsActivity extends SherlockPreferenceActivity {
	private static final String TAG = "SettingsActivity";
	private Preference mBgImagePref;

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
		final PreferenceManager prefMgr = getPreferenceManager();
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

			mBgImagePref = prefMgr.findPreference("pref_background_image");

			final String bgImageUri = prefMgr.getSharedPreferences().getString("pref_background_image", null);
			if (TextUtils.isEmpty(bgImageUri)) {
				mBgImagePref.setTitle(R.string.pref_title_background_image_select);
			}
			else {
				mBgImagePref.setTitle(R.string.pref_title_background_image_remove);
			}

			mBgImagePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					if (TextUtils.isEmpty(bgImageUri)) {
						Intent intent = new Intent();
						intent.setType("image/*");
						intent.setAction(Intent.ACTION_GET_CONTENT);
						startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
								SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
					}
					else {
						mBgImagePref.setTitle("select background image");
						Editor editor = prefMgr.getSharedPreferences().edit();
						editor.remove("pref_background_image");
						editor.commit();
						SurespotConfiguration.setBackgroundImageSet(false);
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

	@SuppressWarnings("deprecation")
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		super.onPreferenceTreeClick(preferenceScreen, preference);
		// work around black background on gingerbread: https://code.google.com/p/android/issues/detail?id=4611
		if (preference != null) {
			if (preference instanceof PreferenceScreen) {
				// work around non clickable home
				// button:http://stackoverflow.com/questions/16374820/action-bar-home-button-not-functional-with-nested-preferencescreen
				initializeActionBar((PreferenceScreen) preference);
				{
					if (((PreferenceScreen) preference).getDialog() != null) {
						((PreferenceScreen) preference).getDialog().getWindow().getDecorView()
								.setBackgroundDrawable(this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
					}
				}
			}
		}

		return false;
	}

	/** Sets up the action bar for an {@link PreferenceScreen} */
	@SuppressLint("NewApi")
	public static void initializeActionBar(PreferenceScreen preferenceScreen) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return;
		}

		final Dialog dialog = preferenceScreen.getDialog();
		if (dialog != null && dialog.getActionBar() != null) {
			// Inialize the action bar
			dialog.getActionBar().setDisplayHomeAsUpEnabled(true);

			// Apply custom home button area click listener to close the PreferenceScreen because PreferenceScreens are dialogs which swallow
			// events instead of passing to the activity
			// Related Issue: https://code.google.com/p/android/issues/detail?id=4611

			View homeBtn = dialog.findViewById(android.R.id.home);
			if (homeBtn == null) {
				homeBtn = dialog.findViewById(R.id.abs__home);
			}

			if (homeBtn != null) {
				OnClickListener dismissDialogClickListener = new OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
					}
				};

				// Prepare yourselves for some hacky programming
				ViewParent homeBtnContainer = homeBtn.getParent();

				// The home button is an ImageView inside a FrameLayout
				if (homeBtnContainer instanceof FrameLayout) {
					ViewGroup containerParent = (ViewGroup) homeBtnContainer.getParent();

					if (containerParent instanceof LinearLayout) {
						// This view also contains the title text, set the whole view as clickable
						((LinearLayout) containerParent).setOnClickListener(dismissDialogClickListener);
					}
					else {
						// Just set it on the home button
						((FrameLayout) homeBtnContainer).setOnClickListener(dismissDialogClickListener);
					}
				}
				else {
					// The 'If all else fails' default case
					homeBtn.setOnClickListener(dismissDialogClickListener);
				}
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			Uri uri = data.getData();

			SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
			SharedPreferences.Editor editor = preferences.edit();
			String uriString = uri.toString();
			SurespotLog.v(TAG, "selected image url: %s", uriString);

			editor.putString("pref_background_image", uriString);
			editor.commit();

			mBgImagePref.setTitle(R.string.pref_title_background_image_remove);
			SurespotConfiguration.setBackgroundImageSet(true);
		}
	}
}
