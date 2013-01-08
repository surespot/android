package com.twofours.surespot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.Toast;

import com.twofours.surespot.encryption.EncryptionController;

public class Utils {
	private static Toast mToast;

	// Fast Implementation
	public static String inputStreamToString(InputStream is) throws IOException {
		String line = "";
		StringBuilder total = new StringBuilder();

		// Wrap a BufferedReader around the InputStream
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));

		// Read response until the end
		while ((line = rd.readLine()) != null) {
			total.append(line);
		}

		// Return full string
		return total.toString();
	}

	public static String getOtherUser(String from, String to) {
		return to.equals(EncryptionController.getIdentityUsername()) ? from : to;
	}

	public static String makePagerFragmentName(int viewId, long id) {
		return "android:switcher:" + viewId + ":" + id;
	}

	public static void makeToast(String toast) {
		if (mToast == null) {
			mToast = Toast.makeText(SurespotApplication.getAppContext(), toast, Toast.LENGTH_SHORT);
		}

		mToast.setText(toast);
		mToast.show();
	}

	public static String getSharedPrefsString(String key) {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		return settings.getString(key, null);
	}

	public static void putSharedPrefsString(String key, String value) {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();
		editor.putString(key, value);
		editor.commit();

	}
}
