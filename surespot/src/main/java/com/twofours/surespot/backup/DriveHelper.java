package com.twofours.surespot.backup;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class DriveHelper {

	private static final String TAG = "DriveHelper";
	private GoogleAccountManager mAccountManager;
	private Drive mService;
	private Context mContext;
	private Account mAccount;
	private boolean mUseStoredAccount;

	public DriveHelper(Context context, boolean useStoredAccount) {
		mContext = context;
		mUseStoredAccount = useStoredAccount;
	}

	public Drive getDriveService() {
		SurespotLog.d(TAG, "getDriveService");
		if (mService == null) {

			if (getDriveAccount() != null) {
				ArrayList<String> scopes = new ArrayList<String>(2);
				scopes.add(DriveScopes.DRIVE);
				scopes.add("https://www.googleapis.com/auth/drive.install");
				GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(mContext, scopes);
				credential.setSelectedAccountName(mAccount.name);
				mService = new Drive.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential).build();

			}

		}
		return mService;

	}

	public Account getDriveAccount() {
		SurespotLog.d(TAG, "getDriveAccount");
		if (mAccount == null && mUseStoredAccount) {
			SharedPreferences sp = mContext.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);

			String accountName = sp.getString("pref_google_drive_account", null);
			if (accountName == null) {

				return null;
			}
			mAccount = getAccountManager().getAccountByName(accountName);
		}
		return mAccount;
	}

	public void setDriveAccount(String name) {
		if (name != null) {
			String username = IdentityController.getLoggedInUser();
			if (!TextUtils.isEmpty(username)) {

				SharedPreferences.Editor editor = mContext.getSharedPreferences(username, Context.MODE_PRIVATE).edit();
				editor.putString("pref_google_drive_account", name);
				editor.commit();
			}
			else {
				// TODO save for when account is created and set for the created user
			}

		}
		mAccount = getAccountManager().getAccountByName(name);
		mService = null;
	}

	public GoogleAccountManager getAccountManager() {
		if (mAccountManager == null) {
			mAccountManager = new GoogleAccountManager(mContext);
		}
		return mAccountManager;

	}

	public byte[] getFileContent(String id) {
		if (id != null && id.length() > 0) {
			try {
				HttpResponse resp = mService.files().get(id).executeMedia();
				InputStream inputStream = resp.getContent();
				if (inputStream != null) {
					return Utils.inputStreamToBytes(inputStream);
				}
			}
			catch (IOException e) {
				SurespotLog.w(TAG, e, "getFileContent");
				return null;
			}
		}

		return null;
	}
}
