package com.twofours.surespot.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.Tuple;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;

public class NetworkController {
    protected String TAG = "NetworkController";
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static String mBaseUrl;

    private OkHttpClient mClient;
    private OkHttpClient mNonRetryingClient;
    private SurespotCookieJar mCookieStore;

    private String mUsername;
    private int m401RetryCount = 0;

    private SSLContext mSSLContext;
    private HostnameVerifier mHostnameVerifier;

    private IAsyncCallback<Object> m401Handler;
    private Context mContext;

    public NetworkController(Context context, String username) {
        SurespotLog.d(TAG, "constructor");
        mUsername = username;
        mContext = context;
        TAG = TAG + " " + username;
        mBaseUrl = SurespotConfiguration.getBaseUrl();
        mCookieStore = new SurespotCookieJar();

        if (mUsername != null) {
            Cookie cookie = IdentityController.getCookieForUser(context, mUsername);
            if (cookie != null) {
                SurespotLog.d(TAG, "got cookie for username: %s", mUsername);
                mCookieStore.setCookie(cookie);
            }
        }

        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        Cache cache = new Cache(FileUtils.getHttpCacheDir(mContext), cacheSize);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                SurespotLog.v("okhttp", message);
            }
        });
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        //handle 401
        okhttp3.Authenticator authenticator = new okhttp3.Authenticator() {
            @Override
            public Request authenticate(Route route, Response response) throws IOException {
                //if login fails abort
                if (response.request().url().pathSegments().contains("login")) {
                    SurespotLog.i(TAG, "authenticate re-login failed, giving up");
                    m401RetryCount = 0;
                    setUnauthorized(true, true);
                    if (m401Handler != null) {
                        m401Handler.handleResponse(null);
                    }

                    return null;
                }

                if (m401RetryCount++ >= 5) {
                    SurespotLog.i(TAG, "authenticate giving up");
                    m401RetryCount = 0;
                    setUnauthorized(true, true);
                    if (m401Handler != null) {
                        m401Handler.handleResponse(null);
                    }

                    return null;
                }

                SurespotLog.i(TAG, "authenticate");
                //TODO exponential backoff
                if (NetworkHelper.reLoginSync(mContext, NetworkController.this, mUsername)) {
                    return response.request().newBuilder().build();
                }
                else {
                    m401RetryCount = 0;
                    setUnauthorized(true, true);
                    if (m401Handler != null) {
                        m401Handler.handleResponse(null);
                    }

                    return null;
                }
            }


        };

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cache(cache)
                .cookieJar(mCookieStore)
                .addInterceptor(logging)
                .addInterceptor(new UserAgentInterceptor(SurespotApplication.getUserAgent()))
                .authenticator(authenticator);


        if (SurespotConfiguration.isSslCheckingStrict()) {
            mClient = enableTls12OnPreLollipop(builder).build();
            builder.retryOnConnectionFailure(false);
            mNonRetryingClient = enableTls12OnPreLollipop(builder).build();
        }
        else {
            try {
                // Create a trust manager that does not validate certificate chains
                final TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };



                // Install the all-trusting trust manager
                mSSLContext = SSLContext.getInstance("SSL");
                mSSLContext.init(null, trustAllCerts, new java.security.SecureRandom());
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = mSSLContext.getSocketFactory();

                mHostnameVerifier = new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                };

                builder.sslSocketFactory(sslSocketFactory).hostnameVerifier(mHostnameVerifier);
                mClient = builder.build();
                builder.retryOnConnectionFailure(false);
                mNonRetryingClient = builder.build();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        mClient.dispatcher().setMaxRequestsPerHost(16);
    }

    //Enable TLS 1.2 on older devices
    // https://github.com/square/okhttp/issues/2372
    private OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                ProviderInstaller.installIfNeeded(mContext);
            } catch (GooglePlayServicesRepairableException e) {

                // Indicates that Google Play services is out of date, disabled, etc.

                // Prompt the user to install/update/enable Google Play services.
                GoogleApiAvailability.getInstance()
                        .showErrorNotification(mContext, e.getConnectionStatusCode());

                return client;

            } catch (GooglePlayServicesNotAvailableException e) {
                // Indicates a non-recoverable error; the ProviderInstaller is not able
                // to install an up-to-date Provider.
                SurespotLog.e(TAG, e, "Could not install providers");
                return client;
            }
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException("Unexpected default trust managers:"
                            + Arrays.toString(trustManagers));
                }
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()), trustManager);

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            } catch (Exception exc) {
                Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc);
            }
        }

        return client;
    }

    public void set401Handler(IAsyncCallback<Object> the401Handler) {
        SurespotLog.d(TAG, "set401Handler, username: %s", mUsername);

        m401Handler = the401Handler;


    }

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
        mNonRetryingClient.newCall(request).enqueue(responseHandler);
    }

    public Call postJSON(String url, JSONObject jsonParams, Callback responseHandler) {
        SurespotLog.d(TAG, "JSON post to " + url);

        RequestBody body = RequestBody.create(JSON, jsonParams.toString());
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .post(body)
                .build();

        Call call = mNonRetryingClient.newCall(request);
        call.enqueue(responseHandler);
        return call;
    }

    public Response postJSONSync(String url, JSONObject jsonParams) throws IOException {
        SurespotLog.d(TAG, "JSON post to " + url);

        RequestBody body = RequestBody.create(JSON, jsonParams.toString());
        Request request = new Request.Builder()
                .url(mBaseUrl + url)
                .post(body)
                .build();

        Call call = mNonRetryingClient.newCall(request);

        return call.execute();

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

    private boolean mUnauthorized;

    public synchronized boolean isUnauthorized() {
        return mUnauthorized;
    }

    synchronized void setUnauthorized(boolean unauthorized, boolean clearCookies) {

        mUnauthorized = unauthorized;
        if (unauthorized && clearCookies) {
            mCookieStore.clear();
        }
    }


    public void createUser3(final String username, String password, String publicKeyDH, String publicKeyECDSA, String authSig, String clientSig, String referrers, final CookieResponseHandler responseHandler) {
        JSONObject params = new JSONObject();
        boolean gcmUpdatedTemp = false;
        final String gcmIdReceived = Utils.getSharedPrefsString(mContext, SurespotConstants.PrefNames.GCM_ID_RECEIVED);
        try {
            params.put("username", username);
            params.put("password", password);
            params.put("dhPub", publicKeyDH);
            params.put("dsaPub", publicKeyECDSA);
            params.put("clientSig2", clientSig);
            params.put("authSig", authSig);
            if (!TextUtils.isEmpty(referrers)) {
                params.put("referrers", referrers);
            }
            params.put("version", SurespotApplication.getVersion());
            params.put("platform", "android");

            //addVoiceMessagingPurchaseTokens(params);

            // get the gcm id


            // update the gcmid if it differs
            if (gcmIdReceived != null) {

                params.put("gcmId", gcmIdReceived);
                gcmUpdatedTemp = true;
            }
        }
        catch (JSONException e) {
            responseHandler.onFailure(e, 500, "error creating user");
            return;
        }

        // just be javascript already
        final boolean gcmUpdated = gcmUpdatedTemp;
        mCookieStore.clear();

        postJSON("/users3", params, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                responseHandler.onFailure(e, 500, "Error creating user.");
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    Cookie cookie = extractConnectCookie(mCookieStore);

                    if (cookie == null) {
                        SurespotLog.w(TAG, "did not get cookie from signup");
                        responseHandler.onFailure(new IOException("Did not get cookie."), 401, "Did not get cookie.");
                    }
                    else {
                        setUnauthorized(false, false);
                        // update shared prefs
                        if (gcmUpdated) {
                            Utils.putUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
                        }

                        responseHandler.onSuccess(response.code(), responseString, cookie);
                    }
                }
                else {
                    responseHandler.onFailure(new Exception("Error creating user."), response.code(), String.format("Error creating user, code: %d", response.code()));
                }
            }
        }));
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
        Request request = new Request.Builder()
                .url(String.format("https://api-ssl.bitly.com/v3/shorten?access_token=%s&longUrl=%s", SurespotConfiguration.getBitlyToken(), longUrl))
                .build();

        Call call = mClient.newCall(request);
        call.enqueue(responseHandler);
    }

    public void updateKeys3(final String username, String password, String publicKeyDH, String publicKeyECDSA, String authSignature, String tokenSignature,
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
            params.put("clientSig2", clientSig);
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

        postJSON("/keys3", params, asyncHttpResponseHandler);
    }

    private Cookie extractConnectCookie(SurespotCookieJar cookieStore) {
        List<Cookie> cookies = cookieStore.getCookies();
        synchronized (cookies) {
            for (Cookie c : cookieStore.getCookies()) {

                if (c.name().equals("connect.sid")) {
                    SurespotLog.d(TAG, "extracted cookie: %s", c);
                    return c;
                }
            }

            return null;
        }
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
            responseHandler.onFailure(e, 500, "JSON Error");
            return;
        }

        mCookieStore.clear();

        // just be javascript already
        final boolean gcmUpdated = gcmUpdatedTemp;

        postJSON("/login", json, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                responseHandler.onFailure(e, 500, "Error logging in.");
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    Cookie cookie = extractConnectCookie(mCookieStore);
                    if (cookie == null) {
                        SurespotLog.w(TAG, "Did not get cookie from login.");
                        responseHandler.onFailure(new Exception("Did not get cookie."), 401, "Did not get cookie.");
                    }
                    else {
                        setUnauthorized(false, false);
                        // update shared prefs
                        if (gcmUpdated) {
                            Utils.putUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
                        }

                        responseHandler.onSuccess(response.code(), responseString, cookie);
                    }
                }
                else {
                    responseHandler.onFailure(new Exception("Error logging in."), response.code(), String.format("Error logging in, code: %d", response.code()));
                }
            }
        }));
    }

    Cookie loginSync(final String username, String password, String signature) {
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
            return null;
        }

        mCookieStore.clear();

        // just be javascript already
        final boolean gcmUpdated = gcmUpdatedTemp;

        Response response = null;
        try {
            response = postJSONSync("/login", json);
        }
        catch (IOException e) {
            return null;
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }

        if (response.isSuccessful()) {
            Cookie cookie = extractConnectCookie(mCookieStore);
            if (cookie == null) {
                SurespotLog.w(TAG, "Did not get cookie from login.");
                return null;
            }
            else {
                // update shared prefs
                if (gcmUpdated) {
                    Utils.putUserSharedPrefsString(mContext, username, SurespotConstants.PrefNames.GCM_ID_SENT, gcmIdReceived);
                }

                return cookie;
            }
        }


        return null;


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
        Response response = null;
        try {
            response = getSync("/publickeys/" + username + "/since/" + version);
            if (response.code() == 200) {
                return response.body().string();
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "Error: getPublicKeysSync.");
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }
        return null;
    }


    public String getKeyVersionSync(String username) {
        SurespotLog.i(TAG, "getKeyVersionSync, username: %s", username);
        Response response = null;
        try {
            response = getSync("/keyversion/" + username);
            if (response.code() == 200) {
                return response.body().string();
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "Error: getKeyVersionSync.");
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }
        return null;

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

    public void registerGcmId(Context context, String id) {

        String username = mUsername;
        //see if it's different for this user
        String sentId = Utils.getUserSharedPrefsString(context, username, SurespotConstants.PrefNames.GCM_ID_SENT);

        if (id.equals(sentId)) {
            //if it's not different don't upload it
            SurespotLog.i(TAG, "GCM id already registered on surespot server.");
            return;
        }

        SurespotLog.i(TAG, "Attempting to register gcm id on surespot server.");


        JSONObject params = new JSONObject();
        try {
            params.put("gcmId", id);
        }
        catch (JSONException e) {
            SurespotLog.i(TAG, e, "Error saving gcmId on surespot server");
            return;
        }

        RequestBody body = RequestBody.create(NetworkController.JSON, params.toString());
        Request request = new Request.Builder()
                .url(SurespotConfiguration.getBaseUrl() + "/registergcm")
                .post(body)
                .build();

        Response response = null;
        try {
            response = mClient.newCall(request).execute();
        }
        catch (IOException e) {
            SurespotLog.i(TAG, e, "Error saving gcmId on surespot server");
            return;
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }

        // success returns 204
        if (response != null && response.code() == 204) {
            SurespotLog.i(TAG, "Successfully saved GCM id on surespot server.");

            // the server and client match, we're golden
            Utils.putUserSharedPrefsString(context, username, SurespotConstants.PrefNames.GCM_ID_SENT, id);
        }
    }

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

        SurespotLog.d(TAG, "posting file stream to %s", url);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBodyUtil.create(MediaType.parse("application/octet-stream"), fileInputStream))
                .build();

        Response response = null;
        try {
            response = mClient.newCall(request).execute();
            int statusCode = response.code();
            switch (statusCode) {
                case 200:
                    String responseBody = response.body().string();
                    JSONObject jsonBody = new JSONObject(responseBody);
                    return new Tuple<>(200, jsonBody);
                case 401:
                    return new Tuple<>(401, null);
                case 409:
                    return new Tuple<>(409, null);
                default:
                    SurespotLog.w(TAG, "error uploading file, response code: %d", statusCode);
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "error uploading file");
            return new Tuple<>(500, null);
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }


        return new Tuple<>(500, null);
    }


    public void postFriendImageStream(final String user, final String ourVersion, final String iv, final InputStream fileInputStream,
                                      final IAsyncCallback<String> callback) {

        if (fileInputStream == null) {
            SurespotLog.d(TAG, "not uploading anything because the file upload stream is null");
            callback.handleResponse(null);
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(mBaseUrl);
        HttpUrl url = new HttpUrl.Builder()
                .scheme(baseUrl.scheme())
                .host((baseUrl.host()))
                .port(baseUrl.port())
                .addPathSegment("files" +
                        "" +
                        "")
                .addPathSegment(user)
                .addPathSegment(ourVersion)
                .addPathSegment(iv)
                .build();


        SurespotLog.d(TAG, "posting friend image stream to %s", url);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBodyUtil.create(MediaType.parse("application/octet-stream"), fileInputStream))
                .build();

        Response response = null;
        String responseBody = null;

        try {
            response = mClient.newCall(request).execute();
            int statusCode = response.code();
            if (statusCode == 200) {
                responseBody = response.body().string();
            }
            else {
                SurespotLog.w(TAG, "error uploading friend image, response code: %d", statusCode);
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "error uploading friend image");
        }
        finally {
            if (response != null) {
                response.body().close();
            }
        }

        final String finalResponseBody = responseBody;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.handleResponse(finalResponseBody);
            }
        });
    }

    public InputStream getFileStream(final String url) {
        //check disk cache before going to network
        try {
            InputStream encryptedImageStream = SurespotApplication.getFileCacheController().getEntry(url);
            if (encryptedImageStream != null) {
                SurespotLog.v(TAG, "getFileStream: returning cached file entry for: %s,", url);
                return encryptedImageStream;
            }
        }
        catch (Exception e) {
            SurespotLog.w(TAG, e, "error getting cached file entry for: %s,", url);
        }

        SurespotLog.d(TAG, "getFileStream: no cached file entry for: %s, making network call", url);
        Response response = null;
        try {
            Request request = new Request.Builder().url(url).build();
            response = mClient.newCall(request).execute();
        }
        catch (Exception e) {
            return null;
        }

        if (response != null) {
            if (response.code() == 200) {
                return response.body().byteStream();
            }
            else {
                response.body().close();
            }
        }

        return null;
    }

    public void logout() {
        if (!isUnauthorized()) {
            post("/logout", new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                }
            });
        }
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

    public void removeCacheEntry(String url) {

        try {
            Iterator<String> it = mClient.cache().urls();

            while (it.hasNext()) {
                String next = it.next();

                if (next.contains(url)) {
                    it.remove();
                }
            }
        }
        catch (IOException e) {
            SurespotLog.i(TAG, e, "error removing cache entry");
        }
    }

    public void clearCache() {

        try {
            mClient.cache().evictAll();
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "could not delete okhttp cache");
        }
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
            params.put("sigs2", sigs);
        }
        catch (JSONException e) {
            responseHandler.onFailure(null, new IOException(e));
            return;
        }
        postJSON("/sigs2", params, responseHandler);
    }

    public String getUsername() {
        return mUsername;
    }

    public SSLContext getSSLContext() {
        return mSSLContext;
    }

    public HostnameVerifier getHostnameVerifier() {
        return mHostnameVerifier;
    }

    public void searchGiphy(String query, String language, Callback responseHandler) {

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.giphy.com")
                .addPathSegment("v1")
                .addPathSegment("gifs")
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("api_key", SurespotConfiguration.getGiphyApiKey())
                .addQueryParameter("rating", "r")
                .addQueryParameter("lang", language)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        Call call = mClient.newCall(request);
        call.enqueue(responseHandler);


    }

    public OkHttpClient getClient() {
        return mClient;
    }
}
