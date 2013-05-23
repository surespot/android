package com.twofours.surespot.backup;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.twofours.surespot.R;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class DriveHelper {

	private GoogleAccountManager mAccountManager;
	private Drive mService;
	private Context mContext;
	private Account mAccount;

	public DriveHelper(Context context) {
		mContext = context;
	}

	public Drive getDriveService() {

		if (mService == null) {

			if (getDriveAccount() != null) {
				GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(mContext, DriveScopes.DRIVE);
				credential.setSelectedAccountName(mAccount.name);
				mService = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
			}

		}
		return mService;

	}

	public Account getDriveAccount() {
		if (mAccount == null) {
			SharedPreferences sp = mContext.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
			boolean enabled = sp.getBoolean("pref_google_drive_backup_enabled", false);

			if (!enabled) {
				Utils.makeLongToast(mContext, mContext.getString(R.string.need_google_drive_enabled));
				return null;
			}

			String accountName = sp.getString("pref_google_drive_account", null);
			if (accountName == null) {
				Utils.makeLongToast(mContext, mContext.getString(R.string.need_google_drive_account));
				return null;
			}
			mAccount = getAccountManager().getAccountByName(accountName);
		}
		return mAccount;
	}

	public GoogleAccountManager getAccountManager() {
		if (mAccountManager == null) {
			mAccountManager = new GoogleAccountManager(mContext);
		}
		return mAccountManager;

	}
}
