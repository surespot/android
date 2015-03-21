/*
    Android Asynchronous Http Client
    Copyright (c) 2011 James Smith <james@loopj.com>
    http://loopj.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.loopj.android.http;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.zip.GZIPInputStream;

import android.content.Context;
import ch.boye.httpclientandroidlib.Header;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.auth.AuthScope;
import ch.boye.httpclientandroidlib.auth.UsernamePasswordCredentials;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.client.methods.HttpDelete;
import ch.boye.httpclientandroidlib.client.methods.HttpEntityEnclosingRequestBase;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.client.methods.HttpPut;
import ch.boye.httpclientandroidlib.client.methods.HttpUriRequest;
import ch.boye.httpclientandroidlib.conn.params.ConnManagerParams;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.entity.HttpEntityWrapper;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.params.HttpProtocolParams;
import ch.boye.httpclientandroidlib.protocol.BasicHttpContext;
import ch.boye.httpclientandroidlib.protocol.HttpContext;
import ch.boye.httpclientandroidlib.protocol.SyncBasicHttpContext;

import com.twofours.surespot.SurespotCachingHttpClient;


/**
 * The AsyncHttpClient can be used to make asynchronous GET, POST, PUT and 
 * DELETE HTTP requests in your Android applications. Requests can be made
 * with additional parameters by passing a {@link RequestParams} instance,
 * and responses can be handled by passing an anonymously overridden 
 * {@link AsyncHttpResponseHandler} instance.
 * <p>
 * For example:
 * <p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get("http://www.google.com", new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onSuccess(String response) {
 *         System.out.println(response);
 *     }
 * });
 * </pre>
 */
public class AsyncHttpClient {
    private final SurespotCachingHttpClient cachingHttpClient;    
    private final HttpContext httpContext;
    private ThreadPoolExecutor threadPool;
    private final Map<Context, List<WeakReference<Future<?>>>> requestMap;
    private final Map<String, String> clientHeaderMap;


    /**
     * Creates a new AsyncHttpClient.
     * @throws IOException 
     */
    public AsyncHttpClient(Context context) throws IOException {
        cachingHttpClient = SurespotCachingHttpClient.createSurespotDiskCachingHttpClient(context);      
        httpContext = new SyncBasicHttpContext(new BasicHttpContext());
        threadPool = (ThreadPoolExecutor)Executors.newCachedThreadPool();        
        requestMap = new WeakHashMap<Context, List<WeakReference<Future<?>>>>();
        clientHeaderMap = new HashMap<String, String>();
    }

    /**
     * Get the underlying HttpClient instance. This is useful for setting
     * additional fine-grained settings for requests by accessing the
     * client's ConnectionManager, HttpParams and SchemeRegistry.
     */
    public SurespotCachingHttpClient getCachingHttpClient() {
        return this.cachingHttpClient;
    }
    
    public AbstractHttpClient getAbstractHttpClient() {
    	return cachingHttpClient.getAbstractHttpClient();
    }

    /**
     * Get the underlying HttpContext instance. This is useful for getting 
     * and setting fine-grained settings for requests by accessing the
     * context's attributes such as the CookieStore.
     */
    public HttpContext getHttpContext() {
        return this.httpContext;
    }

    /**
     * Sets an optional CookieStore to use when making requests
     * @param cookieStore The CookieStore implementation to use, usually an instance of {@link PersistentCookieStore}
     */
    public void setCookieStore(CookieStore cookieStore) {      
        cachingHttpClient.getAbstractHttpClient().setCookieStore(cookieStore);
    }

    /**
     * Overrides the threadpool implementation used when queuing/pooling
     * requests. By default, Executors.newCachedThreadPool() is used.
     * @param threadPool an instance of {@link ThreadPoolExecutor} to use for queuing/pooling requests.
     */
    public void setThreadPool(ThreadPoolExecutor threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * Sets the User-Agent header to be sent with each request. By default,
     * "Android Asynchronous Http Client/VERSION (http://loopj.com/android-async-http/)" is used.
     * @param userAgent the string to use in the User-Agent header.
     */
    public void setUserAgent(String userAgent) {
        HttpProtocolParams.setUserAgent(this.cachingHttpClient.getParams(), userAgent);
    }

    /**
     * Sets the connection time oout. By default, 10 seconds
     * @param timeout the connect/socket timeout in milliseconds
     */
    public void setTimeout(int timeout){
        final HttpParams httpParams = this.cachingHttpClient.getParams();
        ConnManagerParams.setTimeout(httpParams, timeout);
        HttpConnectionParams.setSoTimeout(httpParams, timeout);
        HttpConnectionParams.setConnectionTimeout(httpParams, timeout);
    }

    /**
     * Sets the SSLSocketFactory to user when making requests. By default,
     * a new, default SSLSocketFactory is used.
     * @param sslSocketFactory the socket factory to use for https requests.
     */
    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.cachingHttpClient.getConnectionManager().getSchemeRegistry().register(new Scheme("https", sslSocketFactory, 443));
    }
    
    /**
     * Sets headers that will be added to all requests this client makes (before sending).
     * @param header the name of the header
     * @param value the contents of the header
     */
    public void addHeader(String header, String value) {
        clientHeaderMap.put(header, value);
    }

    /**
     * Sets basic authentication for the request. Uses AuthScope.ANY. This is the same as
     * setBasicAuth('username','password',AuthScope.ANY) 
     * @param username
     * @param password
     */
    public void setBasicAuth(String user, String pass){
        AuthScope scope = AuthScope.ANY;
        setBasicAuth(user, pass, scope);
    }
    
   /**
     * Sets basic authentication for the request. You should pass in your AuthScope for security. It should be like this
     * setBasicAuth("username","password", new AuthScope("host",port,AuthScope.ANY_REALM))
     * @param username
     * @param password
     * @param scope - an AuthScope object
     *
     */
    public void setBasicAuth( String user, String pass, AuthScope scope){
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user,pass);
        this.cachingHttpClient.getCredentialsProvider().setCredentials(scope, credentials);
    }

    /**
     * Cancels any pending (or potentially active) requests associated with the
     * passed Context.
     * <p>
     * <b>Note:</b> This will only affect requests which were created with a non-null
     * android Context. This method is intended to be used in the onDestroy
     * method of your android activities to destroy all requests which are no
     * longer required.
     *
     * @param context the android Context instance associated to the request.
     * @param mayInterruptIfRunning specifies if active requests should be cancelled along with pending requests.
     */
    public void cancelRequests(Context context, boolean mayInterruptIfRunning) {
        List<WeakReference<Future<?>>> requestList = requestMap.get(context);
        if(requestList != null) {
            for(WeakReference<Future<?>> requestRef : requestList) {
                Future<?> request = requestRef.get();
                if(request != null) {
                    request.cancel(mayInterruptIfRunning);
                }
            }
        }
        requestMap.remove(context);
    }


    //
    // HTTP GET Requests
    //

    /**
     * Perform a HTTP GET request, without any parameters.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void get(String url, AsyncHttpResponseHandler responseHandler) {
        get(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP GET request with parameters.
     * @param url the URL to send the request to.
     * @param params additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        get(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP GET request without any parameters and track the Android Context which initiated the request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void get(Context context, String url, AsyncHttpResponseHandler responseHandler) {
        get(context, url, null, responseHandler);
    }

    /**
     * Perform a HTTP GET request and track the Android Context which initiated the request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void get(Context context, String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        sendRequest(cachingHttpClient, httpContext, new HttpGet(getUrlWithQueryString(url, params)), null, responseHandler, context);
    }
    
    /**
     * Perform a HTTP GET request and track the Android Context which initiated
     * the request with customized headers
     * 
     * @param url the URL to send the request to.
     * @param headers set headers only for this request
     * @param params additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle
     *        the response.
     */
    public void get(Context context, String url, Header[] headers, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        HttpUriRequest request = new HttpGet(getUrlWithQueryString(url, params));
        if(headers != null) request.setHeaders(headers);
        sendRequest(cachingHttpClient, httpContext, request, null, responseHandler,
                context);
    }


    //
    // HTTP POST Requests
    //

    /**
     * Perform a HTTP POST request, without any parameters.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void post(String url, AsyncHttpResponseHandler responseHandler) {
        post(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP POST request with parameters.
     * @param url the URL to send the request to.
     * @param params additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        post(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void post(Context context, String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        post(context, url, paramsToEntity(params), null, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param entity a raw {@link HttpEntity} to send with the request, for example, use this to send string/json/xml payloads to a server by passing a {@link ch.boye.httpclientandroidlib.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for example application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void post(Context context, String url, HttpEntity entity, String contentType, AsyncHttpResponseHandler responseHandler) {
        sendRequest(cachingHttpClient, httpContext, addEntityToRequestBase(new HttpPost(url), entity), contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated
     * the request. Set headers only for this request
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param headers set headers only for this request
     * @param params additional POST parameters to send with the request.
     * @param contentType the content type of the payload you are sending, for
     *        example application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle
     *        the response.
     */
    public void post(Context context, String url, Header[] headers, RequestParams params, String contentType,
            AsyncHttpResponseHandler responseHandler) {
        HttpEntityEnclosingRequestBase request = new HttpPost(url);
        if(params != null) request.setEntity(paramsToEntity(params));
        if(headers != null) request.setHeaders(headers);
        sendRequest(cachingHttpClient, httpContext, request, contentType,
                responseHandler, context);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated
     * the request. Set headers only for this request
     *
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param headers set headers only for this request
     * @param entity a raw {@link HttpEntity} to send with the request, for
     *        example, use this to send string/json/xml payloads to a server by
     *        passing a {@link ch.boye.httpclientandroidlib.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for
     *        example application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle
     *        the response.
     */
    public void post(Context context, String url, Header[] headers, HttpEntity entity, String contentType,
            AsyncHttpResponseHandler responseHandler) {
        HttpEntityEnclosingRequestBase request = addEntityToRequestBase(new HttpPost(url), entity);
        if(headers != null) request.setHeaders(headers);
        sendRequest(cachingHttpClient, httpContext, request, contentType, responseHandler, context);
    }

    //
    // HTTP PUT Requests
    //

    /**
     * Perform a HTTP PUT request, without any parameters.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void put(String url, AsyncHttpResponseHandler responseHandler) {
        put(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request with parameters.
     * @param url the URL to send the request to.
     * @param params additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void put(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        put(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void put(Context context, String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        put(context, url, paramsToEntity(params), null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     * And set one-time headers for the request
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param entity a raw {@link HttpEntity} to send with the request, for example, use this to send string/json/xml payloads to a server by passing a {@link ch.boye.httpclientandroidlib.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for example application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void put(Context context, String url, HttpEntity entity, String contentType, AsyncHttpResponseHandler responseHandler) {
        sendRequest(cachingHttpClient, httpContext, addEntityToRequestBase(new HttpPut(url), entity), contentType, responseHandler, context);
    }
    
    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     * And set one-time headers for the request
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param headers set one-time headers for this request
     * @param entity a raw {@link HttpEntity} to send with the request, for example, use this to send string/json/xml payloads to a server by passing a {@link ch.boye.httpclientandroidlib.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for example application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void put(Context context, String url,Header[] headers, HttpEntity entity, String contentType, AsyncHttpResponseHandler responseHandler) {
        HttpEntityEnclosingRequestBase request = addEntityToRequestBase(new HttpPut(url), entity);
        if(headers != null) request.setHeaders(headers);
        sendRequest(cachingHttpClient, httpContext, request, contentType, responseHandler, context);
    }

    //
    // HTTP DELETE Requests
    //

    /**
     * Perform a HTTP DELETE request.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void delete(String url, AsyncHttpResponseHandler responseHandler) {
        delete(null, url, responseHandler);
    }

    /**
     * Perform a HTTP DELETE request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void delete(Context context, String url, AsyncHttpResponseHandler responseHandler) {
        final HttpDelete delete = new HttpDelete(url);
        sendRequest(cachingHttpClient, httpContext, delete, null, responseHandler, context);
    }
    
    /**
     * Perform a HTTP DELETE request.
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param headers set one-time headers for this request
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void delete(Context context, String url, Header[] headers, AsyncHttpResponseHandler responseHandler) {
        final HttpDelete delete = new HttpDelete(url);
        if(headers != null) delete.setHeaders(headers);
        sendRequest(cachingHttpClient, httpContext, delete, null, responseHandler, context);
    }


    // Private stuff
    protected void sendRequest(SurespotCachingHttpClient client, HttpContext httpContext, HttpUriRequest uriRequest, String contentType, AsyncHttpResponseHandler responseHandler, Context context) {
        if(contentType != null) {
            uriRequest.addHeader("Content-Type", contentType);
        }

        Future<?> request = threadPool.submit(new AsyncHttpRequest(client, httpContext, uriRequest, responseHandler));

        if(context != null) {
            // Add request to request map
            List<WeakReference<Future<?>>> requestList = requestMap.get(context);
            if(requestList == null) {
                requestList = new LinkedList<WeakReference<Future<?>>>();
                requestMap.put(context, requestList);
            }

            requestList.add(new WeakReference<Future<?>>(request));

            // TODO: Remove dead weakrefs from requestLists?
        }
    }

    public static String getUrlWithQueryString(String url, RequestParams params) {
        if(params != null) {
            String paramString = params.getParamString();
            if (url.indexOf("?") == -1) {
                url += "?" + paramString;
            } else {
                url += "&" + paramString;
            }
        }

        return url;
    }

    private HttpEntity paramsToEntity(RequestParams params) {
        HttpEntity entity = null;

        if(params != null) {
            entity = params.getEntity();
        }

        return entity;
    }

    private HttpEntityEnclosingRequestBase addEntityToRequestBase(HttpEntityEnclosingRequestBase requestBase, HttpEntity entity) {
        if(entity != null){
            requestBase.setEntity(entity);
        }

        return requestBase;
    }

    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }
    
    public void clearCache() {
    	cachingHttpClient.clearCache();
    }
}
