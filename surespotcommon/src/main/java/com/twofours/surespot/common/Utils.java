package com.twofours.surespot.common;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockPreferenceActivity;

public class Utils {
	private static Toast mToast;
	private static final String TAG = "Utils";

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
		rd.close();
		is.close();
		return total.toString();
	}

	public static byte[] inputStreamToBytes(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		byteBuffer.close();
		inputStream.close();
		return byteBuffer.toByteArray();
	}

	// public static byte[] base64Encode(byte[] buf) {
	// return Base64.encode(buf, Base64.NO_WRAP | Base64.URL_SAFE);
	// }
	//
	// public static byte[] base64Decode(String buf) {
	// return ChatUtils.base64decode(buf, Base64.NO_WRAP | Base64.URL_SAFE);
	// }

	public static String makePagerFragmentName(int viewId, long id) {
		return "android:switcher:" + viewId + ":" + id;
	}

	public static void makeToast(Context context, String toast) {
		if (context == null)
			return;

		if (mToast == null) {
			mToast = Toast.makeText(context, toast, Toast.LENGTH_SHORT);
		}

		mToast.setText(toast);
		mToast.setGravity(android.view.Gravity.CENTER, 0, 0);
		mToast.show();
	}

	public static void makeLongToast(Context context, String toast) {
		if (context == null)
			return;

		if (mToast == null) {
			mToast = Toast.makeText(context, toast, Toast.LENGTH_LONG);
		}
		mToast.setGravity(android.view.Gravity.CENTER, 0, 0);
		mToast.setText(toast);
		mToast.show();
	}

	public static String getSharedPrefsString(Context context, String key) {
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		return settings.getString(key, null);
	}

	public static void putSharedPrefsString(Context context, String key, String value) {
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();
		if (value == null) {
			editor.remove(key);
		}
		else {
			editor.putString(key, value);
		}
		editor.commit();

	}

	public static boolean getSharedPrefsBoolean(Context context, String key) {
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		return settings.getBoolean(key, false);
	}

	public static void putSharedPrefsBoolean(Context context, String key, boolean value) {
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();

		editor.putBoolean(key, value);
		editor.commit();
	}
	
	public static void removePref(Context context, String key) {
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();		
		editor.remove(key);		
		editor.commit();
	}
	
	public static SharedPreferences getGlobalSharedPrefs(Context context) {
		return context.getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE, android.content.Context.MODE_PRIVATE);
	}

	public static HashMap<String, Integer> jsonToMap(JSONObject jsonObject) {
		try {
			HashMap<String, Integer> outMap = new HashMap<String, Integer>();

			@SuppressWarnings("unchecked")
			Iterator<String> names = jsonObject.keys();
			while (names.hasNext()) {
				String name = names.next();
				outMap.put(name, jsonObject.getInt(name));
			}

			return outMap;

		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonToMap", e);
		}
		return null;

	}

	public static HashMap<String, Boolean> jsonBooleanToMap(JSONObject jsonObject) {
		try {
			HashMap<String, Boolean> outMap = new HashMap<String, Boolean>();

			@SuppressWarnings("unchecked")
			Iterator<String> names = jsonObject.keys();
			while (names.hasNext()) {
				String name = names.next();
				outMap.put(name, jsonObject.getBoolean(name));
			}

			return outMap;

		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonToMap", e);
		}
		return null;

	}

	public static HashMap<String, Boolean> jsonStringToBooleanMap(String jsonString) {

		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return jsonBooleanToMap(jsonObject);
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringToMap", e);
		}

		return null;

	}

	public static HashMap<String, Integer> jsonStringToMap(String jsonString) {

		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return jsonToMap(jsonObject);
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringToMap", e);
		}

		return null;

	}

	public static String md5(String s) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(s.getBytes());
			byte[] messageDigest = digest.digest();

			// Create Hex String
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < messageDigest.length; i++)
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			return hexString.toString();

		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * Configure the title bar the way we want it. Would be nice if sherlock would give us an interface.
	 * 
	 * @param activity
	 * @param leftText
	 * @param rightText
	 */
	public static void configureActionBar(SherlockFragmentActivity activity, String leftText, String rightText, boolean home) {
		final ActionBar actionBar = activity.getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(home);
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		View customNav = LayoutInflater.from(activity).inflate(R.layout.actionbar_title, null);
		actionBar.setCustomView(customNav);
		setActionBarTitles(activity, leftText, rightText);

	}

	public static void configureActionBar(SherlockActivity activity, String leftText, String rightText, boolean home) {
		final ActionBar actionBar = activity.getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(home);
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		View customNav = LayoutInflater.from(activity).inflate(R.layout.actionbar_title, null);
		actionBar.setCustomView(customNav);
		setActionBarTitles(activity, leftText, rightText);
	}

	public static void configureActionBar(SherlockPreferenceActivity activity, String leftText, String rightText, boolean home) {
		final ActionBar actionBar = activity.getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(home);
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		View customNav = LayoutInflater.from(activity).inflate(R.layout.actionbar_title, null);
		actionBar.setCustomView(customNav);
		setActionBarTitles(activity, leftText, rightText);
	}

	// code for these should be identical
	public static void setActionBarTitles(SherlockFragmentActivity activity, String leftText, String rightText) {
		TextView navView = (TextView) activity.findViewById(R.id.nav);
		TextView separatorView = (TextView) activity.findViewById(R.id.separator);
		TextView userView = (TextView) activity.findViewById(R.id.user);

		if (leftText != null && leftText.length() > 0) {
			navView.setVisibility(View.VISIBLE);
			separatorView.setVisibility(View.VISIBLE);
			navView.setText(leftText);
			userView.setGravity(Gravity.CENTER_VERTICAL);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(0, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		else {
			navView.setVisibility(View.GONE);
			separatorView.setVisibility(View.GONE);
			navView.setText("");
			userView.setGravity(Gravity.CENTER);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(7, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		userView.setText(rightText);
	}

	public static void setActionBarTitles(SherlockActivity activity, String leftText, String rightText) {
		TextView navView = (TextView) activity.findViewById(R.id.nav);
		TextView separatorView = (TextView) activity.findViewById(R.id.separator);
		TextView userView = (TextView) activity.findViewById(R.id.user);

		if (leftText != null && leftText.length() > 0) {
			navView.setVisibility(View.VISIBLE);
			separatorView.setVisibility(View.VISIBLE);
			navView.setText(leftText);
			userView.setGravity(Gravity.CENTER_VERTICAL);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(0, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		else {
			navView.setVisibility(View.GONE);
			separatorView.setVisibility(View.GONE);
			navView.setText("");
			userView.setGravity(Gravity.CENTER);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(7, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		userView.setText(rightText);
	}

	public static void setActionBarTitles(SherlockPreferenceActivity activity, String leftText, String rightText) {
		TextView navView = (TextView) activity.findViewById(R.id.nav);
		TextView separatorView = (TextView) activity.findViewById(R.id.separator);
		TextView userView = (TextView) activity.findViewById(R.id.user);

		if (leftText != null && leftText.length() > 0) {
			navView.setVisibility(View.VISIBLE);
			separatorView.setVisibility(View.VISIBLE);
			navView.setText(leftText);
			userView.setGravity(Gravity.CENTER_VERTICAL);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(0, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		else {
			navView.setVisibility(View.GONE);
			separatorView.setVisibility(View.GONE);
			navView.setText("");
			userView.setGravity(Gravity.CENTER);
			LayoutParams params = (LayoutParams) userView.getLayoutParams();
			params.setMargins(7, 0, 0, 0);
			userView.setLayoutParams(params);
		}
		userView.setText(rightText);
	}

	public static void logIntent(String tag, Intent intent) {
		if (intent != null && SurespotLog.isLogging()) {			
			Uri uri = intent.getData();
			String action = intent.getAction();
			String type = intent.getType();
			Bundle extras = intent.getExtras();
			Set<String> categories = intent.getCategories();

			SurespotLog.v(tag, "Intent uri: %s", uri);
			SurespotLog.v(tag, "Intent action: %s", action);
			SurespotLog.v(tag, "Intent type: %s", type);

			SurespotLog.v(tag, "Intent categories: " + (categories == null ? "null" : categories.toString()));

			if (extras != null) {
				for (String extra : extras.keySet()) {
					SurespotLog.v(tag, "Intent extra, key: %s, value: %s", extra, extras.get(extra));
				}
			}
		}
	}

	public static void clearIntent(Intent intent) {
		if (intent != null) {
			intent.setData(null);
			intent.setAction(null);
			intent.setType(null);
			if (intent.getExtras() != null) {
				for (String extra : intent.getExtras().keySet()) {
					intent.removeExtra(extra);
				}
			}
		}
	}

	public static String getResourceString(Context context, String name) {
		int nameResourceID = context.getResources().getIdentifier(name, "string", context.getApplicationInfo().packageName);
		if (nameResourceID == 0) {
			throw new IllegalArgumentException("No resource string found with name " + name);
		}
		else {
			return context.getString(nameResourceID);
		}
	}

	public static ArrayList<String> getToEmails(Context context) {
		Pattern emailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
		Account[] accounts = AccountManager.get(context).getAccounts();
		ArrayList<String> emailAddresses = new ArrayList(accounts.length);
		for (android.accounts.Account account : accounts) {
			if (emailPattern.matcher(account.name).matches()) {
				if (!emailAddresses.contains(account.name.toLowerCase())) {
					emailAddresses.add(account.name);
				}

			}
		}

		Collections.sort(emailAddresses);

		return emailAddresses;
	}
}
