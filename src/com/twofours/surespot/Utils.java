package com.twofours.surespot;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Base64;
import android.widget.Toast;

import com.twofours.surespot.chat.SurespotMessage;
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

	public static byte[] inputStreamToBytes(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		return byteBuffer.toByteArray();
	}
	
	public static String inputStreamToBase64(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];

		int len = 0;
		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}
		return new String(Base64.encode(byteBuffer.toByteArray(),Base64.DEFAULT));
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
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		return settings.getString(key, null);
	}

	public static void putSharedPrefsString(String key, String value) {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences(SurespotConstants.PrefNames.PREFS_FILE,
				android.content.Context.MODE_PRIVATE);
		Editor editor = settings.edit();
		if (value == null) {
			editor.remove(key);
		} else {
			editor.putString(key, value);
		}
		editor.commit();

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

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}

	public static HashMap<String, Integer> jsonStringToMap(String jsonString) {

		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return jsonToMap(jsonObject);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	public static String mapToJsonString(Map<String, Integer> map) {
		JSONObject jsonObject = new JSONObject(map);
		return jsonObject.toString();
	}

	public static JSONArray chatMessagesToJson(Collection<SurespotMessage> messages) {

		JSONArray jsonMessages = new JSONArray();
		Iterator<SurespotMessage> iterator = messages.iterator();
		while (iterator.hasNext()) {
			jsonMessages.put(iterator.next().toJSONObject());

		}
		return jsonMessages;

	}

	public static ArrayList<SurespotMessage> jsonStringToChatMessages(String jsonMessageString) {

		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		try {
			JSONArray jsonUM = new JSONArray(jsonMessageString);
			for (int i = 0; i < jsonUM.length(); i++) {
				messages.add(SurespotMessage.toChatMessage(jsonUM.getJSONObject(i)));
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return messages;

	}
}
