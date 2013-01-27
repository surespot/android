package com.twofours.surespot.network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.PersistentCookieStore;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotCachingHttpClient;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.ui.activities.LoginActivity;

public class NetworkController {
	protected static final String TAG = "NetworkController";
	private static Cookie mConnectCookie;

	private static synchronized void setConnectCookie(Cookie connectCookie) {
		// we be authorized
		NetworkController.mConnectCookie = connectCookie;
		setUnauthorized(false);
	}

	private static AsyncHttpClient mClient;
	private static CookieStore mCookieStore;
	private static SyncHttpClient mSyncClient;

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

	public static CookieStore getCookieStore() {
		return mCookieStore;
	}

	private static boolean mUnauthorized;

	private static boolean isUnauthorized() {
		return mUnauthorized;
	}

	public static synchronized void setUnauthorized(boolean unauthorized) {

		NetworkController.mUnauthorized = unauthorized;
	}

	static {
		mCookieStore = new PersistentCookieStore(SurespotApplication.getAppContext());
		if (mCookieStore.getCookies().size() > 0) {
			Log.v(TAG, "mmm cookies in the jar: " + mCookieStore.getCookies().size());
			mConnectCookie = extractConnectCookie(mCookieStore);
		}

		mClient = new AsyncHttpClient(SurespotApplication.getAppContext());
		
		mSyncClient = new SyncHttpClient(SurespotApplication.getAppContext()) {

			@Override
			public String onRequestFailed(Throwable arg0, String arg1) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		HttpResponseInterceptor httpResponseInterceptor = new HttpResponseInterceptor() {

			@Override
			public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {

				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
					String origin = context.getAttribute("http.cookie-origin").toString();

					if (origin != null) {

						if (!NetworkController.isUnauthorized()) {

							if (!(origin.contains(SurespotConstants.BASE_URL.substring(7)) && origin.contains("/login"))) {

								mClient.cancelRequests(SurespotApplication.getAppContext(), true);

								Log.v(TAG, "Got 401, launching login intent.");
								Intent intent = new Intent(SurespotApplication.getAppContext(), LoginActivity.class);
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								SurespotApplication.getAppContext().startActivity(intent);

								setUnauthorized(true);
							}

						}
					}
				}
			}
		};

		mClient.setCookieStore(mCookieStore);
		mSyncClient.setCookieStore(mCookieStore);
		
		// handle 401s
		((SurespotCachingHttpClient) mClient.getHttpClient()).addResponseInterceptor(httpResponseInterceptor);
		((SurespotCachingHttpClient) mSyncClient.getHttpClient()).addResponseInterceptor(httpResponseInterceptor);

	}


	public static void addUser(String username, String password, String publicKey, final AsyncHttpResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("publickey", publicKey);
		// get the gcm id
		final String gcmIdReceived = Utils.getSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_RECEIVED);

		boolean gcmUpdatedTemp = false;
		// update the gcmid if it differs
		if (gcmIdReceived != null) {

			params.put("gcmId", gcmIdReceived);
			gcmUpdatedTemp = true;
		}

		// just be javascript already
		final boolean gcmUpdated = gcmUpdatedTemp;

		post("/users", new RequestParams(params), new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {
				setConnectCookie(extractConnectCookie(mCookieStore));
				if (mConnectCookie == null) {
					Log.e(TAG, "did not get cookie from signup");
					responseHandler.onFailure(new Exception("Did not get cookie."), "Did not get cookie.");
				} else {
					// update shared prefs
					if (gcmUpdated) {
						Utils.putSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
					}

					responseHandler.onSuccess(responseCode, result);
				}

			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				responseHandler.onFailure(arg0, content);
			}

			@Override
			public void onFinish() {
				responseHandler.onFinish();
			}

		});

	}

	private static Cookie extractConnectCookie(CookieStore cookieStore) {
		for (Cookie c : cookieStore.getCookies()) {
			// System.out.println("Cookie name: " + c.getName() + " value: " +
			// c.getValue());
			if (c.getName().equals("connect.sid")) {
				return c;
			}
		}
		return null;

	}

	public static void login(String username, String password, final AsyncHttpResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);

		// get the gcm id
		final String gcmIdReceived = Utils.getSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_RECEIVED);
		String gcmIdSent = Utils.getSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT);

		boolean gcmUpdatedTemp = false;
		// update the gcmid if it differs
		if (gcmIdReceived != null && !gcmIdReceived.equals(gcmIdSent)) {

			params.put("gcmId", gcmIdReceived);
			gcmUpdatedTemp = true;
		}

		// just be javascript already
		final boolean gcmUpdated = gcmUpdatedTemp;

		post("/login", new RequestParams(params), new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {
				setConnectCookie(extractConnectCookie(mCookieStore));
				if (mConnectCookie == null) {
					Log.e(TAG, "Did not get cookie from login.");
					responseHandler.onFailure(new Exception("Did not get cookie."), null);
				} else {
					// update shared prefs
					if (gcmUpdated) {
						Utils.putSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
					}

					responseHandler.onSuccess(responseCode, result);
				}

			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				responseHandler.onFailure(arg0, content);
			}

			@Override
			public void onFinish() {
				responseHandler.onFinish();
			}
		});

	}

	public static void getFriends(AsyncHttpResponseHandler responseHandler) {
		get("/friends", null, responseHandler);
	}

	// if we have an id get the messages since the id, otherwise get the last x
	public static void getMessages(String room, String id, AsyncHttpResponseHandler responseHandler) {

		if (id == null) {
			get("/messages/" + room, null, responseHandler);
		} else {
			get("/messages/" + room + "/after/" + id, null, responseHandler);
		}
	}

	// if we have an id get the messages since the id, otherwise get the last x
	public static void getEarlierMessages(String room, String id, AsyncHttpResponseHandler responseHandler) {
		get("/messages/" + room + "/before/" + id, null, responseHandler);
	}

	public static void getLastMessageIds(JsonHttpResponseHandler responseHandler) {
		get("/conversations/ids", null, responseHandler);
	}

	public static void getPublicKey(String username, AsyncHttpResponseHandler responseHandler) {
		get("/publickey/" + username, null, responseHandler);

	}

	public static String getPublicKeySync(String username) {
		return mSyncClient.get(SurespotConstants.BASE_URL + "/publickey/" + username);
	}

	public static void invite(String friendname, AsyncHttpResponseHandler responseHandler) {

		post("/invite/" + friendname, null, responseHandler);

	}

	public static void respondToInvite(String friendname, String action, AsyncHttpResponseHandler responseHandler) {
		post("/invites/" + friendname + "/" + action, null, responseHandler);
	}

	public static void registerGcmId(final AsyncHttpResponseHandler responseHandler) {
		// make sure the gcm is set
		// use case:
		// user signs-up without google account (unlikely)
		// user creates google account
		// user opens app again, we have session so neither login or add user is called (which wolud set the gcm)
		// so we need to upload the gcm here if we haven't already
		// get the gcm id

		final String gcmIdReceived = Utils.getSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_RECEIVED);
		String gcmIdSent = Utils.getSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT);

		Map<String, String> params = new HashMap<String, String>();

		boolean gcmUpdatedTemp = false;
		// update the gcmid if it differs
		if (gcmIdReceived != null && !gcmIdReceived.equals(gcmIdSent)) {

			params.put("gcmId", gcmIdReceived);
			gcmUpdatedTemp = true;
		} else {
			Log.v(TAG, "GCM does not need updating on server.");
			return;
		}

		// just be javascript already
		final boolean gcmUpdated = gcmUpdatedTemp;

		post("/registergcm", new RequestParams(params), new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {

				// update shared prefs
				if (gcmUpdated) {
					Utils.putSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
				}

				responseHandler.onSuccess(responseCode, result);
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				responseHandler.onFailure(arg0, arg1);
			}

		});

	}

	public static void userExists(String username, AsyncHttpResponseHandler responseHandler) {
		get("/users/" + username + "/exists", null, responseHandler);
	}

	/**
	 * Unregister this account/device pair within the server.
	 */
	public static void unregister(final Context context, final String regId) {
		Log.i(TAG, "unregistering device (regId = " + regId + ")");
		try {
			//this will puke on phone with no google account
			GCMRegistrar.setRegisteredOnServer(context, false);
		}
		finally{}
	}

	public static void postFile(Context context, String user, String id, byte[] data, String mimeType,
			AsyncHttpResponseHandler responseHandler) {

		RequestParams params = new RequestParams();		
		params.put("image", new ByteArrayInputStream(data), id, mimeType);

		post("/images/" + user, params, responseHandler);

	}

	public static void getFile(String relativeUrl, AsyncHttpResponseHandler responseHandler) {
		get(relativeUrl, null, responseHandler);
	}
	
	public static String getFileSync(String relativeUrl) {
		return mSyncClient.get(SurespotConstants.BASE_URL + relativeUrl);
	}

}
