package com.twofours.surespot.network;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.acra.ACRA;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.InputStreamBody;
import ch.boye.httpclientandroidlib.impl.client.BasicCookieStore;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.CookieResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotCachingHttpClient;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class NetworkController {
	protected static final String TAG = "NetworkController";
	private static String mBaseUrl;

	private Context mContext;
	private AsyncHttpClient mClient;
	private CookieStore mCookieStore;
	private SyncHttpClient mSyncClient;
	private SurespotCachingHttpClient mCachingHttpClient;

	public void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		mClient.get(mBaseUrl + url, params, responseHandler);
	}

	public void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		mClient.post(mBaseUrl + url, params, responseHandler);
	}

	public CookieStore getCookieStore() {
		return mCookieStore;
	}

	private boolean mUnauthorized;

	public synchronized boolean isUnauthorized() {
		return mUnauthorized;
	}

	public synchronized void setUnauthorized(boolean unauthorized) {

		mUnauthorized = unauthorized;
		if (unauthorized) {
			mCookieStore.clear();
		}
	}

	public NetworkController(Context context) {
		mContext = context;

		mBaseUrl = SurespotConfiguration.getBaseUrl();
		mCookieStore = new BasicCookieStore();
		Cookie cookie = IdentityController.getCookie();
		if (cookie != null) {
			mCookieStore.addCookie(cookie);
		}

		try {

			mCachingHttpClient = SurespotCachingHttpClient.createSurespotDiskCachingHttpClient(context);
			mClient = new AsyncHttpClient(mContext);
			mSyncClient = new SyncHttpClient(mContext) {

				@Override
				public String onRequestFailed(Throwable arg0, String arg1) {
					return null;
				}
			};
		}
		catch (IOException e) {
			// TODO tell user shit is fucked
			ACRA.getErrorReporter().handleException(e);
			throw new RuntimeException(e);
		}

		HttpResponseInterceptor httpResponseInterceptor = new HttpResponseInterceptor() {

			@Override
			public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {

				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
					String origin = context.getAttribute("http.cookie-origin").toString();

					if (origin != null) {

						if (!isUnauthorized()) {

							Uri uri = Uri.parse(mBaseUrl);
							if (!(origin.contains(uri.getHost()) && origin.contains("/login"))) {
								setUnauthorized(true);

								mClient.cancelRequests(mContext, true);
								mSyncClient.cancelRequests(mContext, true);

								SurespotLog.v(TAG, "Got 401, launching login intent.");
								Intent intent = new Intent(mContext, LoginActivity.class);
								intent.putExtra("401", true);
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
								mContext.startActivity(intent);

							}
						}
					}
				}
			}
		};

		if (mClient != null && mSyncClient != null && mCachingHttpClient != null) {

			mClient.setCookieStore(mCookieStore);
			mSyncClient.setCookieStore(mCookieStore);
			mCachingHttpClient.setCookieStore(mCookieStore);

			// handle 401s
			mClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
			mSyncClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
			mCachingHttpClient.addResponseInterceptor(httpResponseInterceptor);
		}
	}

	public void addUser(final String username, String password, String publicKeyDH, String publicKeyECDSA, String signature,
			final CookieResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("pkdh", publicKeyDH);
		params.put("pkecdsa", publicKeyECDSA);
		params.put("signature", signature);
		// get the gcm id
		final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);

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
				Cookie cookie = extractConnectCookie(mCookieStore);

				if (cookie == null) {
					SurespotLog.w(TAG, "did not get cookie from signup");
					responseHandler.onFailure(new Exception("Did not get cookie."), "Did not get cookie.");
				}
				else {
					setUnauthorized(false);
					// update shared prefs
					if (gcmUpdated) {
						Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
					}

					responseHandler.onSuccess(responseCode, result, cookie);
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

	public void login(String username, String password, String signature, final CookieResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("signature", signature);

		// get the gcm id
		final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);
		String gcmIdSent = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT);

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
				Cookie cookie = extractConnectCookie(mCookieStore);
				if (cookie == null) {
					SurespotLog.w(TAG, "Did not get cookie from login.");
					responseHandler.onFailure(new Exception("Did not get cookie."), null);
				}
				else {
					// update shared prefs
					if (gcmUpdated) {
						Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
					}

					responseHandler.onSuccess(responseCode, result, cookie);
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

	public void getFriends(AsyncHttpResponseHandler responseHandler) {
		get("/friends", null, responseHandler);
	}

	// if we have an id get the messages since the id, otherwise get the last x
	public void getMessages(String room, String id, AsyncHttpResponseHandler responseHandler) {

		if (id == null) {
			get("/messages/" + room, null, responseHandler);
		}
		else {
			get("/messages/" + room + "/after/" + id, null, responseHandler);
		}
	}

	// if we have an id get the messages since the id, otherwise get the last x
	public void getEarlierMessages(String room, String id, AsyncHttpResponseHandler responseHandler) {
		get("/messages/" + room + "/before/" + id, null, responseHandler);
	}

	public void getLastMessageIds(JsonHttpResponseHandler responseHandler) {
		get("/conversations/ids", null, responseHandler);
	}

	public void getPublicKey(String username, AsyncHttpResponseHandler responseHandler) {
		get("/publickey/" + username, null, responseHandler);

	}

	public String getPublicKeySync(String username) {
		return mSyncClient.get(mBaseUrl + "/publickey/" + username);
	}

	public void invite(String friendname, AsyncHttpResponseHandler responseHandler) {

		post("/invite/" + friendname, null, responseHandler);

	}

	public void respondToInvite(String friendname, String action, AsyncHttpResponseHandler responseHandler) {
		post("/invites/" + friendname + "/" + action, null, responseHandler);
	}

	public void registerGcmId(final AsyncHttpResponseHandler responseHandler) {
		// make sure the gcm is set
		// use case:
		// user signs-up without google account (unlikely)
		// user creates google account
		// user opens app again, we have session so neither login or add user is called (which wolud set the gcm)
		// so we need to upload the gcm here if we haven't already
		// get the gcm id

		final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);
		String gcmIdSent = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT);

		Map<String, String> params = new HashMap<String, String>();

		boolean gcmUpdatedTemp = false;
		// update the gcmid if it differs
		if (gcmIdReceived != null && !gcmIdReceived.equals(gcmIdSent)) {

			params.put("gcmId", gcmIdReceived);
			gcmUpdatedTemp = true;
		}
		else {
			SurespotLog.v(TAG, "GCM does not need updating on server.");
			return;
		}

		// just be javascript already
		final boolean gcmUpdated = gcmUpdatedTemp;

		post("/registergcm", new RequestParams(params), new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {

				// update shared prefs
				if (gcmUpdated) {
					Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
				}

				responseHandler.onSuccess(responseCode, result);
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				responseHandler.onFailure(arg0, arg1);
			}

		});

	}

	public void validate(String username, String password, String publickey, AsyncHttpResponseHandler responseHandler) {
		RequestParams params = new RequestParams();

		params.put("username", username);
		params.put("password", password);
		if (publickey != null) {
			params.put("publickey", publickey);
		}

		// ideally would use a get here but putting body in a get request is frowned upon apparently:
		// http://stackoverflow.com/questions/978061/http-get-with-request-body
		// It's also not a good idea to put passwords in the url
		post("/validate", params, responseHandler);
	}

	public void userExists(String username, AsyncHttpResponseHandler responseHandler) {
		get("/users/" + username + "/exists", null, responseHandler);
	}

	/**
	 * Unregister this account/device pair within the server.
	 */
	public static void unregister(final Context context, final String regId) {
		SurespotLog.i(TAG, "unregistering device (regId = " + regId + ")");
		try {
			// this will puke on phone with no google account
			GCMRegistrar.setRegisteredOnServer(context, false);
		}
		finally {
		}
	}

	public void postFileStream(Context context, final String user, final String id, final InputStream fileInputStream,
			final String mimeType, final IAsyncCallback<Boolean> callback) {
		new AsyncTask<Void, Void, HttpResponse>() {

			@Override
			protected HttpResponse doInBackground(Void... params) {

				SurespotLog.v(TAG, "posting file stream");

				HttpPost httppost = new HttpPost(mBaseUrl + "/images/" + user);

				InputStreamBody isBody = new InputStreamBody(fileInputStream, mimeType, id);

				MultipartEntity reqEntity = new MultipartEntity();
				reqEntity.addPart("image", isBody);
				httppost.setEntity(reqEntity);
				HttpResponse response = null;

				try {
					response = mCachingHttpClient.execute(httppost);

				}
				catch (Exception e) {
					SurespotLog.w(TAG, "createPostFile", e);
				}
				return response;

			}

			protected void onPostExecute(HttpResponse response) {
				if (response != null && response.getStatusLine().getStatusCode() == 202) {

					callback.handleResponse(true);
				}
				else {
					callback.handleResponse(false);
				}

			};
		}.execute();

	}

	public InputStream getFileStream(Context context, final String url) {

		SurespotLog.v(TAG, "getting file stream");

		HttpGet httpGet = new HttpGet(mBaseUrl + url);
		HttpResponse response;
		try {
			response = mCachingHttpClient.execute(httpGet);
			HttpEntity resEntity = response.getEntity();
			if (response.getStatusLine().getStatusCode() == 200) {
				return resEntity.getContent();
			}

		}

		catch (Exception e) {
			SurespotLog.w(TAG, "getFileStream", e);

		}

		return null;

	}

	public void logout(final AsyncHttpResponseHandler responseHandler) {
		post("/logout", null, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, String content) {
				setUnauthorized(true);

				responseHandler.onSuccess(statusCode, content);
			}

			@Override
			public void onFailure(Throwable error, String content) {
				responseHandler.onFailure(error, content);
			}

		});

	}

	public void clearCache() {
		// all the clients share a cache
		mClient.clearCache();
	}

}
