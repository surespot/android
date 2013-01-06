package com.twofours.surespot.network;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;

import android.content.Context;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.PersistentCookieStore;
import com.loopj.android.http.RequestParams;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;

public class NetworkController {
	protected static final String TAG = "NetworkController";
	private static Cookie mConnectCookie;

	private static AsyncHttpClient mClient;
	private static CookieStore mCookieStore;

	public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		mClient.get(SurespotConstants.BASE_URL + url, params, responseHandler);
	}

	public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		mClient.post(SurespotConstants.BASE_URL + url, params, responseHandler);
	}

	public static Cookie getConnectCookie() {
		return mConnectCookie;
	}

	public static boolean hasSession() {
		return mConnectCookie != null;
	}

	static {
		mCookieStore = new PersistentCookieStore(SurespotApplication.getAppContext());
		if (mCookieStore.getCookies().size() > 0) {
			Log.v(TAG, "mmm cookies in the jar: " + mCookieStore.getCookies().size());
			mConnectCookie = getConnectCookie(mCookieStore);
		}
		
		mClient = new AsyncHttpClient();
		mClient.setCookieStore(mCookieStore);
	}

	public static void addUser(String username, String password, String publicKey, String gcmId,
			final AsyncHttpResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("publickey", publicKey);
		if (gcmId != null) {
			params.put("device_gcm_id", gcmId);
		}

		post("/users", new RequestParams(params), new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(String result) {
				mConnectCookie = getConnectCookie(mCookieStore);
				if (mConnectCookie == null) {
					Log.e(TAG, "did not get cookie from signup");
				}

				responseHandler.onFailure(new Exception("Did not get cookie."), null);
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				responseHandler.onFailure(arg0, content);
			}

		});

	}

	private static Cookie getConnectCookie(CookieStore cookieStore) {
		for (Cookie c : mCookieStore.getCookies()) {
			// System.out.println("Cookie name: " + c.getName() + " value: " +
			// c.getValue());
			if (c.getName().equals("connect.sid")) { return c; }
		}
		return null;

	}

	public static void login(String username, String password, final AsyncHttpResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);

		post("/login", new RequestParams(params), new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String arg0) {

				for (Cookie c : mCookieStore.getCookies()) {
					System.out.println("Cookie name: " + c.getName() + " value: " + c.getValue());
					if (c.getName().equals("connect.sid")) {
						mConnectCookie = c;

						responseHandler.onSuccess(arg0);
						return;
					}
				}

				if (mConnectCookie == null) {
					Log.e(TAG, "did not get cookie from login.");
				}

				responseHandler.onFailure(new Exception("Did not get cookie."), null);
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				responseHandler.onFailure(arg0, content);
			}
		});

	}

	public static void getFriends(AsyncHttpResponseHandler responseHandler) {
		get("/friends", null, responseHandler);
	}

	public static void getNotifications(AsyncHttpResponseHandler responseHandler) {
		get("/notifications", null, responseHandler);

	}

	public static void getMessages(String room, AsyncHttpResponseHandler responseHandler) {
		get("/conversations/" + room + "/messages", null, responseHandler);
	}

	public static void getPublicKey(String username, AsyncHttpResponseHandler responseHandler) {
		get("/publickey/" + username, null, responseHandler);

	}

	public static void invite(String friendname, AsyncHttpResponseHandler responseHandler) {

		post("/invite/" + friendname, null, responseHandler);

	}

	public static void respondToInvite(String friendname, String action, AsyncHttpResponseHandler responseHandler) {
		post("/invites/" + friendname + "/" + action, null, responseHandler);
	}

	public static void registerGcmId(String id, AsyncHttpResponseHandler responseHandler) {

		Map<String, String> params = new HashMap<String, String>();
		params.put("device_gcm_id", id);

		post("/registergcm/", new RequestParams(params), responseHandler);

	}

	/**
	 * Unregister this account/device pair within the server.
	 */
	public static void unregister(final Context context, final String regId) {
		Log.i(TAG, "unregistering device (regId = " + regId + ")");
		GCMRegistrar.setRegisteredOnServer(context, false);
	}
}
