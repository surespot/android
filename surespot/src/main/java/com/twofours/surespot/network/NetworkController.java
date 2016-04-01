package com.twofours.surespot.network;

import android.content.Context;
import android.text.TextUtils;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.Tuple;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class NetworkController {
    protected static final String TAG = "NetworkController";
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static String mBaseUrl;

    private Context mContext;
    private OkHttpClient mClient;
    private SurespotCookieJar mCookieStore;

    private String mUsername;

    public void get(String url, Callback responseHandler) {
        SurespotLog.d(TAG, "get  " + url);
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .build();
        mClient.newCall(request).enqueue(responseHandler);
    }

    public Response getSync(String url) throws IOException {
        SurespotLog.d(TAG, "get  " + url);
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .build();

        return mClient.newCall(request).execute();

    }

    public void post(String url, Callback responseHandler) {
        SurespotLog.d(TAG, "post: " + url);
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .post(RequestBody.create(null, new byte[0]))
                .build();
        mClient.newCall(request).enqueue(responseHandler);
    }

    public Call postJSON(String url, JSONObject jsonParams, Callback responseHandler) {
        SurespotLog.d(TAG, "JSON post to " + url);

        RequestBody body = RequestBody.create(JSON, jsonParams.toString());
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .post(body)
                .build();

        Call call = mClient.newCall(request);
        call.enqueue(responseHandler);
        return call;
    }

    public void putJSON(String url, JSONObject jsonParams, Callback responseHandler) {
        SurespotLog.d(TAG, "put JSON to: " + url);
        RequestBody body = RequestBody.create(JSON, jsonParams.toString());
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .put(body)
                .build();

        mClient.newCall(request).enqueue(responseHandler);
    }

    public void delete(String url, Callback responseHandler) {
        SurespotLog.d(TAG, "delete to " + url);

        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .delete()
                .build();

        mClient.newCall(request).enqueue(responseHandler);
    }

    public CookieJar getCookieStore() {
        return mCookieStore;
    }

    private boolean mUnauthorized;

    public synchronized boolean isUnauthorized() {
        return mUnauthorized;
    }

    public synchronized void setUnauthorized(boolean unauthorized, boolean clearCookies) {

        mUnauthorized = unauthorized;
        if (unauthorized && clearCookies) {
            mCookieStore.clear();
        }
    }

    public NetworkController(Context context, String username, final IAsyncCallbackTuple<String, Boolean> m401Handler) throws Exception {
        SurespotLog.d(TAG, "constructor username: %s", username);
        mContext = context;
        mUsername = username;
        mBaseUrl = SurespotConfiguration.getBaseUrl();
        mCookieStore = new SurespotCookieJar();

        mClient = new OkHttpClient.Builder()
                .cookieJar(mCookieStore)
                .build();

        if (mClient == null) {
            Utils.makeLongToast(context, context.getString(R.string.error_surespot_could_not_create_http_clients));
            throw new Exception("Fatal error, could not create http clients..is storage space available?");
        }


        if (username != null) {
            Cookie cookie = IdentityController.getCookieForUser(username);
            if (cookie != null) {
                mCookieStore.setCookie(cookie);
            }
        }

//        HttpResponseInterceptor httpResponseInterceptor = new HttpResponseInterceptor() {
//
//            @Override
//            public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
//
//                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
//                    String origin = context.getAttribute("http.cookie-origin").toString();
//
//                    if (origin != null) {
//
//                        if (!isUnauthorized()) {
//
//                            Uri uri = Uri.parse(mBaseUrl);
//                            if (!(origin.contains(uri.getHost()) && origin.contains("/login"))) {
//                                // setUnauthorized(true);
//
//                                mClient.cancelRequests(mContext, true);
//                                mSyncClient.cancelRequests(mContext, true);
//
//                                if (m401Handler != null) {
//                                    m401Handler.handleResponse(mContext.getString(R.string.unauthorized), false);
//                                }
//
//                            }
//                        }
//                    }
//                }
//            }
//        };
//
//        if (mClient != null && mSyncClient != null && mCachingHttpClient != null) {
//
//            mClient.setCookieStore(mCookieStore);
//            mSyncClient.setCookieStore(mCookieStore);
//            mCachingHttpClient.setCookieStore(mCookieStore);
//
//            // handle 401s
//            mClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
//            mSyncClient.getAbstractHttpClient().addResponseInterceptor(httpResponseInterceptor);
//            mCachingHttpClient.addResponseInterceptor(httpResponseInterceptor);
//
//            mClient.setUserAgent(SurespotApplication.getUserAgent());
//            mSyncClient.setUserAgent(SurespotApplication.getUserAgent());
//            mCachingHttpClient.setUserAgent(SurespotApplication.getUserAgent());
//        }
    }

    public void createUser2(final String username, String password, String publicKeyDH, String publicKeyECDSA, String authSig, String clientSig, String referrers, final CookieResponseHandler responseHandler) {
        JSONObject json = new JSONObject();
        Map<String, String> params = new HashMap<String, String>();
        params.put("username", username);
        params.put("password", password);
        params.put("dhPub", publicKeyDH);
        params.put("dsaPub", publicKeyECDSA);
        params.put("clientSig", clientSig);
        params.put("authSig", authSig);
        if (!TextUtils.isEmpty(referrers)) {
            params.put("referrers", referrers);
        }
        params.put("version", SurespotApplication.getVersion());
        params.put("platform", "android");
        //addVoiceMessagingPurchaseTokens(params);

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

        for (Cookie c : mCookieStore.getCookies()) {

            if (c.name().equals("connect.sid")) {
                SurespotLog.d(TAG, "signup, clearing cookie: %s", c);
            }
        }

        mCookieStore.clear();

        postJSON("/users2", json, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                responseHandler.onFailure(e, "Error creating user.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Cookie cookie = null;
                if (response.isSuccessful()) {
                    cookie = extractConnectCookie(mCookieStore);
                }

                if (cookie == null) {
                    SurespotLog.w(TAG, "did not get cookie from signup");
                    responseHandler.onFailure(new IOException("Did not get cookie."), "Did not get cookie.");
                }
                else {
                    setUnauthorized(false, false);
                    // update shared prefs
                    if (gcmUpdated) {
                        Utils.putUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
                    }

                    responseHandler.onSuccess(response.code(), response.body().string(), cookie);
                }
            }


        });
    }

    public void getKeyToken(String username, String password, String authSignature, Callback jsonHttpResponseHandler) {
        JSONObject json = new JSONObject();
        try {

            json.put("username", username);
            json.put("password", password);
            json.put("authSig", authSignature);
        }
        catch (JSONException e) {
            jsonHttpResponseHandler.onFailure(null, new IOException(e));
            return;
        }

        postJSON("/keytoken", json, jsonHttpResponseHandler);

    }

    public void getDeleteToken(final String username, String password, String authSignature, Callback asyncHttpResponseHandler) {
        JSONObject json = new JSONObject();
        try {

            json.put("username", username);
            json.put("password", password);
            json.put("authSig", authSignature);
        }
        catch (JSONException e) {
            asyncHttpResponseHandler.onFailure(null, new IOException(e));
            return;
        }

        postJSON("/deletetoken", json, asyncHttpResponseHandler);
    }

    public void getPasswordToken(final String username, String password, String authSignature, Callback responseHandler) {
        JSONObject json = new JSONObject();
        try {

            json.put("username", username);
            json.put("password", password);
            json.put("authSig", authSignature);
        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
            return;
        }

        postJSON("/passwordtoken", json, responseHandler);
    }

    public void getShortUrl(String longUrl, Callback responseHandler) {

        try {
            JSONObject params = new JSONObject();
            params.put("longUrl", longUrl);

            postJSON("https://www.googleapis.com/urlshortener/v1/url?key=" + SurespotConfiguration.getGoogleApiKey(), params, responseHandler);
        }
        catch (JSONException e) {
            SurespotLog.v(TAG, "getShortUrl", e);
            responseHandler.onFailure(null, new IOException(e));
        }

    }

    public void updateKeys(final String username, String password, String publicKeyDH, String publicKeyECDSA, String authSignature, String tokenSignature,
                           String keyVersion, String clientSig, Callback asyncHttpResponseHandler) {
        JSONObject params = new JSONObject();
        try {
            params.put("username", username);
            params.put("password", password);
            params.put("dhPub", publicKeyDH);
            params.put("dsaPub", publicKeyECDSA);
            params.put("authSig", authSignature);
            params.put("tokenSig", tokenSignature);
            params.put("keyVersion", keyVersion);
            params.put("clientSig", clientSig);
            params.put("version", SurespotApplication.getVersion());
            params.put("platform", "android");

            String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);

            if (gcmIdReceived != null) {
                params.put("gcmId", gcmIdReceived);
            }
        }
        catch (JSONException e) {
            asyncHttpResponseHandler.onFailure(null, new IOException(e));
            return;
        }

        postJSON("/keys2", params, asyncHttpResponseHandler);
    }

    private static Cookie extractConnectCookie(SurespotCookieJar cookieStore) {
        for (Cookie c : cookieStore.getCookies()) {

            if (c.name().equals("connect.sid")) {
                SurespotLog.d(TAG, "extracted cookie: %s", c);
                return c;
            }
        }
        return null;

    }

    public void login(final String username, String password, String signature, final CookieResponseHandler responseHandler) {
        SurespotLog.d(TAG, "login username: %s", username);
        JSONObject json = new JSONObject();
        final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);

        boolean gcmUpdatedTemp = false;
        try {
            json.put("username", username);
            json.put("password", password);
            json.put("authSig", signature);
            json.put("version", SurespotApplication.getVersion());
            json.put("platform", "android");


            // get the gcm id
            String gcmIdSent = Utils.getUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT);
            SurespotLog.v(TAG, "gcm id received: %s, gcmId sent: %s", gcmIdReceived, gcmIdSent);


            // update the gcmid if it false
            if (gcmIdReceived != null) {

                json.put("gcmId", gcmIdReceived);

                if (!gcmIdReceived.equals(gcmIdSent)) {
                    gcmUpdatedTemp = true;
                }
            }
        }
        catch (Exception e) {
            responseHandler.onFailure(e, "JSON Error");
            return;
        }

        for (Cookie c : mCookieStore.getCookies()) {

            if (c.name().equals("connect.sid")) {
                SurespotLog.d(TAG, "login, clearing cookie: %s", c);
            }
        }

        mCookieStore.clear();

        // just be javascript already
        final boolean gcmUpdated = gcmUpdatedTemp;

        postJSON("/login", json, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                responseHandler.onFailure(e, "Error logging in.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Cookie cookie = extractConnectCookie(mCookieStore);
                    if (cookie == null) {
                        SurespotLog.w(TAG, "Did not get cookie from login.");
                        responseHandler.onFailure(new Exception("Did not get cookie."), "Did not get cookie.");
                    }

                    else {
                        setUnauthorized(false, false);
                        // update shared prefs
                        if (gcmUpdated) {
                            Utils.putUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
                        }

                        responseHandler.onSuccess(response.code(), response.body().string(), cookie);
                    }
                }
            }


        });

    }

    public void getFriends(Callback responseHandler) {
        get("/friends", responseHandler);
    }

    public void getMessageData(String user, Integer messageId, Integer controlId, Callback responseHandler) {
        int mId = messageId;
        int cId = controlId;

        get("/messagedataopt/" + user + "/" + mId + "/" + cId, responseHandler);

    }

    public void getLatestData(int userControlId, JSONArray spotIds, Callback responseHandler) {
        SurespotLog.d(TAG, "getLatestData userControlId: %d", userControlId);
        JSONObject params = new JSONObject();
        try {
            params.put("spotIds", spotIds.toString());
        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
        }

        postJSON("/optdata/" + userControlId, params, responseHandler);
    }

    // if we have an id get the messages since the id, otherwise get the last x
    public void getEarlierMessages(String username, Integer id, Callback responseHandler) {
        get("/messagesopt/" + username + "/before/" + id, responseHandler);
    }


    public String getPublicKeysSync(String username, String version) {
        try {
            return getSync("/publickeys/" + username + "/since/" + version).body().string();
        }
        catch (IOException e) {
            return null;
        }

    }


    public String getKeyVersionSync(String username) {
        SurespotLog.i(TAG, "getKeyVersionSync, username: %s", username);
        try {
            return getSync("/keyversion/" + username).body().string();
        }
        catch (IOException e) {
            return null;
        }

    }

    public void invite(String friendname, Callback responseHandler) {
        post("/invite/" + friendname, responseHandler);
    }

    public void invite(String friendname, String source, Callback responseHandler) {
        post("/invite/" + friendname + "/" + source, responseHandler);
    }

    public void postMessages(List<SurespotMessage> messages, Callback responseHandler) {
        JSONObject params = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            jsonArray.put(messages.get(i).toJSONObjectSocket());
        }
        try {
            params.put("messages", jsonArray);
        }
        catch (JSONException e) {
            SurespotLog.e(TAG, e, "postMessages");
        }
        postJSON("/messages", params, responseHandler);
    }

    public void respondToInvite(String friendname, String action, Callback responseHandler) {
        post("/invites/" + friendname + "/" + action, responseHandler);
    }

//	public void registerGcmId(final AsyncHttpResponseHandler responseHandler) {
//		// make sure the gcm is set
//		// use case:
//		// user signs-up without google account (unlikely)
//		// user creates google account
//		// user opens app again, we have session so neither login or add user is called (which wolud set the gcm)
//		// so we need to upload the gcm here if we haven't already
//		// get the gcm id
//
//		final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);
//		String gcmIdSent = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT);
//
//		Map<String, String> params = new HashMap<String, String>();
//
//		boolean gcmUpdatedTemp = false;
//		// update the gcmid if it differs
//		if (gcmIdReceived != null && !gcmIdReceived.equals(gcmIdSent)) {
//
//			params.put("gcmId", gcmIdReceived);
//			gcmUpdatedTemp = true;
//		}
//		else {
//			SurespotLog.v(TAG, "GCM does not need updating on server.");
//			return;
//		}
//
//		// just be javascript already
//		final boolean gcmUpdated = gcmUpdatedTemp;
//
//		post("/registergcm", new RequestParams(params), new AsyncHttpResponseHandler() {
//
//			@Override
//			public void onSuccess(int responseCode, String result) {
//
//				// update shared prefs
//				if (gcmUpdated) {
//					Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
//				}
//
//				responseHandler.onSuccess(responseCode, result);
//			}
//
//			@Override
//			public void onFailure(Throwable arg0, String arg1) {
//				responseHandler.onFailure(arg0, arg1);
//			}
//
//		});
//
//	}

    public void validate(String username, String password, String signature, Callback responseHandler) {
        JSONObject json = new JSONObject();


        try {
            json.put("username", username);
            json.put("password", password);
            json.put("authSig", signature);

        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
        }

        // ideally would use a get here but putting body in a get request is frowned upon apparently:
        // http://stackoverflow.com/questions/978061/http-get-with-request-body
        // It's also not a good idea to put passwords in the url
        postJSON("/validate", json, responseHandler);
    }

    public void userExists(String username, Callback responseHandler) {
        get("/users/" + username + "/exists", responseHandler);
    }


    public Tuple<Integer, JSONObject> postFileStreamSync(final String ourVersion, final String user, final String theirVersion, final String id,
                                                         final InputStream fileInputStream, final String mimeType) throws JSONException {




        if (fileInputStream == null) {
            SurespotLog.d(TAG, "not uploading anything because the file upload stream is null");
            return new Tuple<>(500, null);
        }

        HttpUrl baseUrl = HttpUrl.parse(mBaseUrl);
        HttpUrl url = new HttpUrl.Builder()
                .scheme(baseUrl.scheme())
                .host((baseUrl.host()))
                .port(baseUrl.port())
                .addPathSegment("files")
                .addPathSegment(ourVersion)
                .addPathSegment(user)
                .addPathSegment(theirVersion)
                .addPathSegment(id)
                .addPathSegment((mimeType.equals(SurespotConstants.MimeTypes.M4A) ? "mp4" : "image"))
                .build();


                //HttpUrl.parse(mBaseUrl + "/files/" + ourVersion + "/" + user + "/" + theirVersion + "/" + id + "/" + (mimeType.equals(SurespotConstants.MimeTypes.M4A) ? "mp4" : "image"));

        SurespotLog.d(TAG, "posting file stream to %s", url);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBodyUtil.create(MediaType.parse("application/octet-stream"), fileInputStream))
                .build();

        Response response;
        try {
            response = mClient.newCall(request).execute();
            int statusCode = response.code();
            if (statusCode == 200) {

                String responseBody = response.body().string();
                JSONObject jsonBody = new JSONObject(responseBody);
                return new Tuple<>(200, jsonBody);
            }
            else {
                SurespotLog.w(TAG, "error uploading file, response code: %d", statusCode);
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "error uploading file");
            return new Tuple<>(500, null);
        }


        return new Tuple<>(500, null);
    }


    public void postFriendImageStream(Context context, final String user, final String ourVersion, final String iv, final InputStream fileInputStream,
                                      final IAsyncCallback<String> callback) {
//        new AsyncTask<Void, Void, String>() {
//
//            @Override
//            protected String doInBackground(Void... params) {
//
//                SurespotLog.v(TAG, "posting file stream");
//
//                HttpPost httppost = new HttpPost(mBaseUrl + "/images2/" + user + "/" + ourVersion);
//
//                InputStreamBody isBody = new InputStreamBody(fileInputStream, SurespotConstants.MimeTypes.IMAGE, iv);
//                MultipartEntity reqEntity = new MultipartEntity();
//                reqEntity.addPart("image", isBody);
//                httppost.setEntity(reqEntity);
//                HttpResponse response = null;
//
//                try {
//                    response = mCachingHttpClient.execute(httppost, new BasicHttpContext());
//                    if (response != null && response.getStatusLine().getStatusCode() == 200) {
//                        String url = Utils.inputStreamToString(response.getEntity().getContent());
//                        return url;
//                    }
//                }
//                catch (IllegalStateException e) {
//                    SurespotLog.w(TAG, e, "postFriendImageStream");
//
//                }
//                catch (IOException e) {
//                    SurespotLog.w(TAG, e, "postFriendImageStream");
//
//                }
//                catch (Exception e) {
//                    SurespotLog.w(TAG, e, "createPostFile");
//                }
//                finally {
//                    httppost.releaseConnection();
//                    if (response != null) {
//                        try {
//                            EntityUtils.consume(response.getEntity());
//                        }
//                        catch (IOException e) {
//                            SurespotLog.w(TAG, e, "postFileStream");
//                        }
//                    }
//                }
//                return null;
//
//            }
//
//            protected void onPostExecute(String url) {
//                callback.handleResponse(url);
//
//            }
//
//            ;
//        }.execute();
    }

    public InputStream getFileStream(Context context, final String url) {

        Response response;
        try {
            Request request = new Request.Builder().url(url).build();
            response = mClient.newCall(request).execute();
        }
        catch (Exception e) {
            return null;
        }

        if (response.code() == 200) {
            return response.body().byteStream();
        }

        return null;
    }

    public void logout() {
        if (!isUnauthorized()) {
            post("/logout", null);
        }
    }

    public void clearCache() {
        // all the clients share a cache
        //  mClient.clearCache();
    }

    public void purgeCacheUrl(String url) {

        //mCachingHttpClient.removeEntry(mBaseUrl + url);

    }

    public void deleteMessage(String username, Integer id, Callback responseHandler) {
        delete("/messages/" + username + "/" + id, responseHandler);
    }

    public void deleteMessages(String username, int utaiId, Callback responseHandler) {
        delete("/messagesutai/" + username + "/" + utaiId, responseHandler);

    }

    public void setMessageShareable(String username, Integer id, boolean shareable, Callback responseHandler) {
        SurespotLog.v(TAG, "setMessageSharable %b", shareable);
        JSONObject params = new JSONObject();
        try {
            params.put("shareable", shareable);

        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
        }

        putJSON("/messages/" + username + "/" + id + "/shareable", params, responseHandler);

    }

    public void deleteFriend(String username, Callback asyncHttpResponseHandler) {
        delete("/friends/" + username, asyncHttpResponseHandler);
    }

    //public void blockUser(String username, boolean blocked, Callback asyncHttpResponseHandler) {
    // put("/users/" + username + "/block/" + blocked, asyncHttpResponseHandler);
    //}

    public void deleteUser(String username, String password, String authSig, String tokenSig, String keyVersion,
                           Callback asyncHttpResponseHandler) {
        JSONObject params = new JSONObject();
        try {
            params.put("username", username);
            params.put("password", password);
            params.put("authSig", authSig);
            params.put("tokenSig", tokenSig);
            params.put("keyVersion", keyVersion);
        }
        catch (JSONException e) {
            asyncHttpResponseHandler.onFailure(null, new IOException(e));
        }

        postJSON("/users/delete", params, asyncHttpResponseHandler);

    }

    public void changePassword(String username, String password, String newPassword, String authSig, String tokenSig, String keyVersion,
                               Callback asyncHttpResponseHandler) {
        JSONObject params = new JSONObject();
        try {
            params.put("username", username);
            params.put("password", password);
            params.put("authSig", authSig);
            params.put("tokenSig", tokenSig);
            params.put("keyVersion", keyVersion);
            params.put("newPassword", newPassword);
        }
        catch (JSONException e) {
            asyncHttpResponseHandler.onFailure(null, new IOException(e));
        }
        putJSON("/users/password", params, asyncHttpResponseHandler);

    }

//    public void addCacheEntry(String key, HttpCacheEntry httpCacheEntry) {
//        // mCachingHttpClient.addCacheEntry(key, httpCacheEntry);
//
//    }

//    public HttpCacheEntry getCacheEntry(String key) {
//        //    return mCachingHttpClient.getCacheEntry(key);
//        return null;
//    }

    public void removeCacheEntry(String key) {
        //   mCachingHttpClient.removeEntry(key);

    }

    public void assignFriendAlias(String username, String version, String data, String iv, Callback responseHandler) {
        SurespotLog.d(TAG, "assignFriendAlias, username: %s, version: %s", username, version);
        JSONObject params = new JSONObject();
        try {
            params.put("data", data);
            params.put("iv", iv);
            params.put("version", version);

        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
            return;
        }
        putJSON("/users/" + username + "/alias2", params, responseHandler);
    }

    public void deleteFriendAlias(String username, Callback responseHandler) {
        SurespotLog.d(TAG, "deleteFriendAlias, username: %s", username);
        delete("/users/" + username + "/alias", responseHandler);
    }

    public void deleteFriendImage(String username, Callback responseHandler) {
        SurespotLog.d(TAG, "deleteFriendImage, username: %s", username);
        delete("/users/" + username + "/image", responseHandler);
    }

    public void updateSigs(JSONObject sigs, Callback responseHandler) {
        JSONObject params = new JSONObject();
        try {
            params.put("sigs", sigs);
        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
            return;
        }
        postJSON("/sigs", params, responseHandler);
    }

    public String getUsername() {
        return mUsername;
    }
}
