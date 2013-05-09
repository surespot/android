package com.twofours.surespot.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.acra.ACRA;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpException;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.entity.StringEntity;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.InputStreamBody;
import ch.boye.httpclientandroidlib.impl.client.BasicCookieStore;
import ch.boye.httpclientandroidlib.message.BasicHeader;
import ch.boye.httpclientandroidlib.protocol.HTTP;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.SurespotCachingHttpClient;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

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

	public void put(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		mClient.put(mBaseUrl + url, params, responseHandler);
	}

	public void delete(String url, AsyncHttpResponseHandler responseHandler) {
		mClient.delete(mBaseUrl + url, responseHandler);
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

	public NetworkController(Context context, final IAsyncCallback<String> m401Handler) {
		SurespotLog.v(TAG, "constructor");
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
			SharedPreferences pm = context.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
			if (pm.getBoolean("pref_crash_reporting", false)) {
				ACRA.getErrorReporter().handleSilentException(e);
				throw new RuntimeException(e);
			}
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
								// setUnauthorized(true);

								mClient.cancelRequests(mContext, true);
								mSyncClient.cancelRequests(mContext, true);

								if (m401Handler != null) {
									m401Handler.handleResponse("unauthorized");
								}

							}
						}
					}
				}
			}
		};

		// HttpRequestInterceptor gzipRequestInterceptor = new HttpRequestInterceptor() {
		//
		// public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
		// if (!request.containsHeader("Accept-Encoding")) {
		// request.addHeader("Accept-Encoding", "gzip");
		// }
		// }
		// };
		//
		// HttpResponseInterceptor gzipResponseInterceptor = new HttpResponseInterceptor() {
		//
		// public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
		// HttpEntity entity = response.getEntity();
		// if (entity != null) {
		// Header ceheader = entity.getContentEncoding();
		// if (ceheader != null) {
		// HeaderElement[] codecs = ceheader.getElements();
		// for (int i = 0; i < codecs.length; i++) {
		// if (codecs[i].getName().equalsIgnoreCase("gzip")) {
		// response.setEntity(new GzipDecompressingEntity(response.getEntity()));
		// return;
		// }
		// }
		// }
		// }
		// }
		// };

		if (mClient != null && mSyncClient != null && mCachingHttpClient != null) {

			mClient.setCookieStore(mCookieStore);
			mSyncClient.setCookieStore(mCookieStore);
			mCachingHttpClient.setCookieStore(mCookieStore);

			// handle 401s
			mClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
			mSyncClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
			mCachingHttpClient.addResponseInterceptor(httpResponseInterceptor);

			// gzip
			// mClient.getAbstractHttpClient().addResponseInterceptor(gzipResponseInterceptor);
			// mSyncClient.getAbstractHttpClient().addResponseInterceptor(gzipResponseInterceptor);
			// mCachingHttpClient.addResponseInterceptor(gzipResponseInterceptor);
			//
			// mClient.getAbstractHttpClient().addRequestInterceptor(gzipRequestInterceptor);
			// mSyncClient.getAbstractHttpClient().addRequestInterceptor(gzipRequestInterceptor);
			// mCachingHttpClient.addRequestInterceptor(gzipRequestInterceptor);

		}
	}

	public void addUser(final String username, String password, String publicKeyDH, String publicKeyECDSA, String signature,
			String referrers, final CookieResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("dhPub", publicKeyDH);
		params.put("dsaPub", publicKeyECDSA);
		params.put("authSig", signature);
		if (!TextUtils.isEmpty(referrers)) {
			params.put("referrers", referrers);
		}
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

	public void getKeyToken(final String username, String password, String authSignature, JsonHttpResponseHandler jsonHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", authSignature);
		post("/keytoken", new RequestParams(params), jsonHttpResponseHandler);
	}

	public void getDeleteToken(final String username, String password, String authSignature,
			AsyncHttpResponseHandler asyncHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", authSignature);
		post("/deletetoken", new RequestParams(params), asyncHttpResponseHandler);
	}

	public void getPasswordToken(final String username, String password, String authSignature,
			AsyncHttpResponseHandler asyncHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", authSignature);
		post("/passwordtoken", new RequestParams(params), asyncHttpResponseHandler);
	}

	public void getShortUrl(String longUrl, JsonHttpResponseHandler responseHandler) {

		try {
			JSONObject params = new JSONObject();
			params.put("longUrl", longUrl);
			StringEntity entity = new StringEntity(params.toString());
			entity.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
			mClient.post(null, "https://www.googleapis.com/urlshortener/v1/url", entity, "application/json", responseHandler);
		}
		catch (UnsupportedEncodingException e) {
			SurespotLog.v(TAG, "getShortUrl", e);
			responseHandler.onFailure(e, new JSONObject());
		}
		catch (JSONException e) {
			SurespotLog.v(TAG, "getShortUrl", e);
			responseHandler.onFailure(e, new JSONObject());
		}

	}

	public void getAutoInviteUrl(String medium, AsyncHttpResponseHandler asyncHttpResponseHandler) {
		get("/autoinviteurl/" + medium, null, asyncHttpResponseHandler);
	}

	public void updateKeys(final String username, String password, String publicKeyDH, String publicKeyECDSA, String authSignature,
			String tokenSignature, String keyVersion, AsyncHttpResponseHandler asyncHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("dhPub", publicKeyDH);
		params.put("dsaPub", publicKeyECDSA);
		params.put("authSig", authSignature);
		params.put("tokenSig", tokenSignature);
		params.put("keyVersion", keyVersion);

		post("/keys", new RequestParams(params), asyncHttpResponseHandler);
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

	public void login(String username, String password, String signature, String referrers, final CookieResponseHandler responseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", signature);
		if (!TextUtils.isEmpty(referrers)) {
			params.put("referrers", referrers);
		}

		// get the gcm id
		final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);
		String gcmIdSent = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT);

		boolean gcmUpdatedTemp = false;
		// update the gcmid if it differs
		if (gcmIdReceived != null) {

			params.put("gcmId", gcmIdReceived);

			if (!gcmIdReceived.equals(gcmIdSent)) {
				gcmUpdatedTemp = true;
			}
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

	public void getFriends(AsyncHttpResponseHandler responseHandler) {
		get("/friends", null, responseHandler);
	}

	// if we have an id get the messages since the id, otherwise get the last x
	// public void getMessages(String room, Integer id, AsyncHttpResponseHandler responseHandler) {
	//
	// if (id == null) {
	// get("/messages/" + room, null, responseHandler);
	// }
	// else {
	// get("/messages/" + room + "/after/" + id, null, responseHandler);
	// }
	// }

	public void getMessageData(String user, Integer messageId, Integer controlId, AsyncHttpResponseHandler responseHandler) {
		int mId = messageId;
		int cId = controlId;

		get("/messagedata/" + user + "/" + mId + "/" + cId, null, responseHandler);

	}

	// public String getMessageDataSync(String user, Integer messageId, Integer controlId) {
	// int mId = messageId;
	// int cId = controlId;
	//
	// return mSyncClient.get(mBaseUrl + "/messagedata/" + user + "/" + mId + "/" + cId);
	//
	// }

	// public void getUserControlData(String user, Integer controlId, AsyncHttpResponseHandler responseHandler) {
	// int cId = controlId;
	// get("/usercontrol/" + cId, null, responseHandler);
	// }

	// public void getLatestMessages(JSONObject messageIds, JsonHttpResponseHandler responseHandler) {
	// // using a post because async http client sends the params url encoded which leaks too much data imo
	//
	// String smessageIds = null;
	//
	// if (messageIds.length() > 0) {
	// smessageIds = messageIds.toString();
	//
	// SurespotLog.v(TAG, "asking server for messages after message ids: " + smessageIds);
	//
	// RequestParams params = new RequestParams();
	// params.put("messageIds", smessageIds);
	// post("/messages", params, responseHandler);
	// }
	// else {
	// post("/messages", null, responseHandler);
	// }
	//
	// }

	public void getLatestIds(int userControlId, JsonHttpResponseHandler responseHandler) {
		get("/latestids/" + userControlId, null, responseHandler);
	}

	// public void getLatestMessages(JSONArray messageIds, JsonHttpResponseHandler responseHandler) {
	// //TODO
	// //could use a post here and not send the message id's in the url but
	// //using a POST when nothing is changing server side doesn't feel right
	// //but neither does exposing the chat names in the url hm
	// URIBuilder builder;
	// try {
	// builder = new URIBuilder(mBaseUrl);
	// builder.setPath("/messages").setParameter("messageIds", messageIds.toString());
	// mClient.get(builder.build().toString(), null, responseHandler);
	// }
	// catch (URISyntaxException e) {
	// SurespotLog.w(TAG, "getLatestMessages",e);
	// responseHandler.onFailure(e, "could not build uri");
	// }
	//
	// }

	// if we have an id get the messages since the id, otherwise get the last x
	public void getEarlierMessages(String room, Integer id, AsyncHttpResponseHandler responseHandler) {
		get("/messages/" + room + "/before/" + id, null, responseHandler);
	}

	public void getLastMessageIds(JsonHttpResponseHandler responseHandler) {
		get("/conversations/ids", null, responseHandler);
	}

	// public void getPublicKey(String username, AsyncHttpResponseHandler responseHandler) {
	// get("/publickey/" + username, null, responseHandler);
	//
	// }

	public String getPublicKeysSync(String username, String version) {

		return mSyncClient.get(mBaseUrl + "/publickeys/" + username + "/" + version);

	}

	public String getKeyVersionSync(String username) {

		return mSyncClient.get(mBaseUrl + "/keyversion/" + username);

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

	public void validate(String username, String password, String signature, AsyncHttpResponseHandler responseHandler) {
		RequestParams params = new RequestParams();

		params.put("username", username);
		params.put("password", password);
		params.put("authSig", signature);

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

	public void postFileStream(Context context, final String ourVersion, final String user, final String theirVersion, final String id,
			final InputStream fileInputStream, final String mimeType, final IAsyncCallback<Boolean> callback) {
		new AsyncTask<Void, Void, HttpResponse>() {

			@Override
			protected HttpResponse doInBackground(Void... params) {

				SurespotLog.v(TAG, "posting file stream");

				HttpPost httppost = new HttpPost(mBaseUrl + "/images/" + ourVersion + "/" + user + "/" + theirVersion);

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
				if (response != null && response.getStatusLine().getStatusCode() == 200) {

					callback.handleResponse(true);
				}
				else {
					callback.handleResponse(false);
				}

			};
		}.execute();
	}

	public void postFriendImageStream(Context context, final String user, final String ourVersion, final String iv,
			final InputStream fileInputStream, final IAsyncCallback<String> callback) {
		new AsyncTask<Void, Void, HttpResponse>() {

			@Override
			protected HttpResponse doInBackground(Void... params) {

				SurespotLog.v(TAG, "posting file stream");

				HttpPost httppost = new HttpPost(mBaseUrl + "/images/" + user + "/" + ourVersion);

				InputStreamBody isBody = new InputStreamBody(fileInputStream, SurespotConstants.MimeTypes.IMAGE, iv);

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
				if (response != null && response.getStatusLine().getStatusCode() == 200) {
					try {
						String url = Utils.inputStreamToString(response.getEntity().getContent());
						callback.handleResponse(url);
					}
					catch (IllegalStateException e) {
						SurespotLog.w(TAG, e, "postFriendImageStream");
						callback.handleResponse(null);
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "postFriendImageStream");
						callback.handleResponse(null);
					}

				}
				else {
					callback.handleResponse(null);
				}

			};
		}.execute();
	}

	public InputStream getFileStream(Context context, final String url) {

		// SurespotLog.v(TAG, "getting file stream");

		HttpGet httpGet = new HttpGet(url);
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

	public void logout() {
		if (!isUnauthorized()) {
			post("/logout", null, new AsyncHttpResponseHandler() {
			});
		}
	}

	public void clearCache() {
		// all the clients share a cache
		mClient.clearCache();
	}

	public void purgeCacheUrl(String url) {
		mCachingHttpClient.removeEntry(mBaseUrl + url);
	}

	public void deleteMessage(String username, Integer id, AsyncHttpResponseHandler responseHandler) {
		delete("/messages/" + username + "/" + id, responseHandler);

	}

	public void deleteMessages(String username, int utaiId, AsyncHttpResponseHandler responseHandler) {
		delete("/messagesutai/" + username + "/" + utaiId, responseHandler);

	}

	public void setMessageShareable(String username, Integer id, boolean shareable, AsyncHttpResponseHandler responseHandler) {
		RequestParams params = new RequestParams("shareable", shareable);
		put("/messages/" + username + "/" + id + "/shareable", params, responseHandler);

	}

	public void deleteFriend(String username, AsyncHttpResponseHandler asyncHttpResponseHandler) {
		delete("/friends/" + username, asyncHttpResponseHandler);
	}

	public void blockUser(String username, boolean blocked, AsyncHttpResponseHandler asyncHttpResponseHandler) {
		put("/users/" + username + "/block/" + blocked, null, asyncHttpResponseHandler);
	}

	public void deleteUser(String username, String password, String authSig, String tokenSig, String keyVersion,
			AsyncHttpResponseHandler asyncHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", authSig);
		params.put("tokenSig", tokenSig);
		params.put("keyVersion", keyVersion);
		post("/users/delete", new RequestParams(params), asyncHttpResponseHandler);

	}

	public void changePassword(String username, String password, String newPassword, String authSig, String tokenSig, String keyVersion,
			AsyncHttpResponseHandler asyncHttpResponseHandler) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("authSig", authSig);
		params.put("tokenSig", tokenSig);
		params.put("keyVersion", keyVersion);
		params.put("newPassword", newPassword);
		put("/users/password", new RequestParams(params), asyncHttpResponseHandler);

	}

	public void addCacheEntry(String key, HttpCacheEntry httpCacheEntry) {
		mCachingHttpClient.addCacheEntry(key, httpCacheEntry);

	}

	public HttpCacheEntry getCacheEntry(String key) {
		return mCachingHttpClient.getCacheEntry(key);
	}

	public void removeCacheEntry(String key) {
		mCachingHttpClient.removeEntry(key);

	}
}
