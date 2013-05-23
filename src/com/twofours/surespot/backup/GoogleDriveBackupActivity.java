package com.twofours.surespot.backup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.UIUtils;

public class GoogleDriveBackupActivity extends SherlockActivity {
	public static final String[] ACCOUNT_TYPE = new String[] { GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE };
	private TextView mAccountNameDisplay;
	private GoogleAccountManager mAccountManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_google_drive_backup);

		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.auto_backup_action_bar_right), true);

		String user = IdentityController.getLoggedInUser();

		TextView t1 = (TextView) findViewById(R.id.helpAutoBackup1);
		Spanned pre = Html.fromHtml(getString(R.string.help_auto_backup_warning_pre));
		Spannable warning = UIUtils.createColoredSpannable(getString(R.string.help_auto_backup_warning), Color.RED);

		t1.setText(TextUtils.concat(pre, " ", warning));
		t1.setMovementMethod(LinkMovementMethod.getInstance());

		TextView t2 = (TextView) findViewById(R.id.helpAutoBackup2);
		UIUtils.setHtml(this, t2, R.string.help_auto_backup2);

		final SharedPreferences sp = getSharedPreferences(user, Context.MODE_PRIVATE);
		boolean abEnabled = sp.getBoolean("pref_google_drive_backup_enabled", false);

		CheckBox cb = (CheckBox) findViewById(R.id.cbAutoBackup);
		cb.setChecked(abEnabled);
		cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Editor editor = sp.edit();
				editor.putBoolean("pref_google_drive_backup_enabled", isChecked);
				editor.commit();

				if (isChecked) {
					chooseAccount();
				} else {
					removeAccount();
				}

			}
		});

		mAccountNameDisplay = (TextView) findViewById(R.id.driveAccount);
		String account = getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE).getString("pref_google_drive_account", null);
		mAccountNameDisplay.setText(account);
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

	private void chooseAccount() {
		if (mAccountManager == null) {
			mAccountManager = new GoogleAccountManager(GoogleDriveBackupActivity.this);
		}
		Intent accountPickerIntent = AccountPicker.newChooseAccountIntent(null, null, ACCOUNT_TYPE, false, null, null, null, null);
		startActivityForResult(accountPickerIntent, SurespotConstants.IntentRequestCodes.CHOOSE_GOOGLE_ACCOUNT);

	}

	private void removeAccount() {
		SharedPreferences.Editor editor = getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE).edit();
		editor.remove("pref_google_drive_account");
		editor.commit();

		mAccountNameDisplay.setText("");

	}

	private void setAccount(Account account) {
		if (account != null) {

			SharedPreferences.Editor editor = getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE).edit();
			editor.putString("pref_google_drive_account", account.name);
			editor.commit();

			mAccountNameDisplay.setText(account.name);

		}
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.CHOOSE_GOOGLE_ACCOUNT:
			if (data != null) {

				SurespotLog.w("Preferences", "SELECTED ACCOUNT WITH EXTRA: %s", data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
				Bundle b = data.getExtras();

				String accountName = b.getString(AccountManager.KEY_ACCOUNT_NAME);

				SurespotLog.d("Preferences", "Selected account: " + accountName);
				if (accountName != null && accountName.length() > 0) {

					setAccount(mAccountManager.getAccountByName(accountName));
				}
			}
			break;
		}
	}
}
