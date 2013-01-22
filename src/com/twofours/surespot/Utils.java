package com.twofours.surespot;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
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
import org.spongycastle.util.encoders.Hex;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.Toast;

import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;

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
		return new String(Base64.encode(byteBuffer.toByteArray(), Base64.DEFAULT));
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

	public static SurespotMessage buildMessage(String to, String mimeType, String plainData,String iv, String cipherData) {
		SurespotMessage chatMessage = new SurespotMessage();
		chatMessage.setFrom(EncryptionController.getIdentityUsername());
		chatMessage.setTo(to);
		chatMessage.setCipherData(cipherData);
		chatMessage.setPlainData(plainData);
		chatMessage.setIv(iv);
		// store the mime type outside teh encrypted envelope, this way we can offload resources
		// by mime type
		chatMessage.setMimeType(mimeType);
		return chatMessage;
	}

	public static void buildPictureMessage(Context context, Uri imageUri, final String to, final IAsyncCallback<SurespotMessage> callback) {

		//TODO thread
		Bitmap bitmap = decodeSampledBitmapFromUri(context, imageUri, 320, 240);

		//bitmap.
		
		//final String data = Utils.inputStreamToBase64(iStream);
		
		final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 75, jpeg);
		try {
			jpeg.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		// Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		EncryptionController.symmetricBase64Encrypt(to, new ByteArrayInputStream(jpeg.toByteArray()), new IAsyncCallback<String[]>() {

			@Override
			public void handleResponse(String[] results) {
				if (results != null) {
					SurespotMessage chatMessage = buildMessage(to, SurespotConstants.MimeTypes.IMAGE,  new String(Base64.encode(jpeg.toByteArray(),Base64.DEFAULT)),results[0], results[1]);
					callback.handleResponse(chatMessage);
				}

			}
		});
		callback.handleResponse(null);
	}

	public static Bitmap decodeSampledBitmapFromUri(Context context, Uri imageUri, int reqWidth, int reqHeight) {

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try {
			BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageUri), null, options);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		try {
			return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageUri), null, options);
		} catch (FileNotFoundException e) {
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
				messages.add(SurespotMessage.toSurespotMessage(jsonUM.getJSONObject(i)));
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return messages;

	}

	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			// Calculate ratios of height and width to requested height and width
			final int heightRatio = Math.round((float) height / (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);

			// Choose the smallest ratio as inSampleSize value, this will guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
		}

		return inSampleSize;
	}
}
