package com.twofours.surespot.backup;

import java.io.IOException;
import java.io.InputStream;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class DriveHelper {

	private static final String TAG = "DriveHelper";
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

				SharedPreferences.Editor editor = mContext.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE).edit();
				editor.putString("pref_google_drive_account", name);
				editor.commit();
			} else {
				// TODO save for when account is created and set for the created user
			}

		}
		mAccount = getAccountManager().getAccountByName(name);
	}

	public GoogleAccountManager getAccountManager() {
		if (mAccountManager == null) {
			mAccountManager = new GoogleAccountManager(mContext);
		}
		return mAccountManager;

	}

	public byte[] getFileContent(String url) {
		if (url != null && url.length() > 0) {
			try {
				GenericUrl downloadUrl = new GenericUrl(url);

				HttpResponse resp = mService.getRequestFactory().buildGetRequest(downloadUrl).execute();
				InputStream inputStream = resp.getContent();
				if (inputStream != null) {
					return Utils.inputStreamToBytes(inputStream);
				}

			} catch (IOException e) {
				SurespotLog.w(TAG, e, "getFileContent");
				return null;
			}
		}

		return null;
	}
}
