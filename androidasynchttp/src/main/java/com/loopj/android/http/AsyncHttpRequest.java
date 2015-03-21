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
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.acra.ACRA;

import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.CacheResponseStatus;
import ch.boye.httpclientandroidlib.client.methods.HttpUriRequest;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;
import ch.boye.httpclientandroidlib.protocol.HttpContext;

import com.twofours.surespot.SurespotCachingHttpClient;

class AsyncHttpRequest implements Runnable {
    private static final String TAG = "AsyncHttpRequest";
	private final SurespotCachingHttpClient client;
    private final HttpContext context;
    private final HttpUriRequest request;
    private final AsyncHttpResponseHandler responseHandler;
    private boolean isBinaryRequest;
    private int executionCount;

    public AsyncHttpRequest(SurespotCachingHttpClient client, HttpContext context, HttpUriRequest request, AsyncHttpResponseHandler responseHandler) {
        this.client = client;
        this.context = context;
        this.request = request;
        this.responseHandler = responseHandler;
        if(responseHandler instanceof BinaryHttpResponseHandler) {
            this.isBinaryRequest = true;
        }
    }

    public void run() {
        try {
            if(responseHandler != null){
                responseHandler.sendStartMessage();
            }

            makeRequestWithRetries();

            if(responseHandler != null) {
                responseHandler.sendFinishMessage();
            }
        } catch (IOException e) {
            if(responseHandler != null) {
                responseHandler.sendFinishMessage();
                if(this.isBinaryRequest) {
                    responseHandler.sendFailureMessage(e, (byte[]) null);
                } else {
                    responseHandler.sendFailureMessage(e, (String) null);
                }
            }
        }
    }

    private void makeRequest() throws IOException {
        if(!Thread.currentThread().isInterrupted()) {
            HttpResponse response = client.execute(request, context);
            if(!Thread.currentThread().isInterrupted()) {
                if(responseHandler != null) {
                	CacheResponseStatus responseStatus = (CacheResponseStatus) context.getAttribute(
                	        CachingHttpClient.CACHE_RESPONSE_STATUS);
                	switch (responseStatus) {
                	case CACHE_HIT:
                	    System.out.println("A response was generated from the cache with no requests " +
                	            "sent upstream");
                	    break;
                	case CACHE_MODULE_RESPONSE:
                	    System.out.println("The response was generated directly by the caching module");
                	    break;
                	case CACHE_MISS:
                	    System.out.println("The response came from an upstream server");
                	    break;
                	case VALIDATED:
                	    System.out.println("The response was generated from the cache after validating " +
                	            "the entry with the origin server");
                	    break;
                	}
                    responseHandler.sendResponseMessage(response);
                }
            } else{
                //TODO: should raise InterruptedException? this block is reached whenever the request is cancelled before its response is received
            	Log.v(TAG,"makeRequest interrupted");
            }
        }
    }

    private void makeRequestWithRetries() throws ConnectException {
        // This is an additional layer of retry logic lifted from droid-fu
        // See: https://github.com/kaeppler/droid-fu/blob/master/src/main/java/com/github/droidfu/http/BetterHttpRequestBase.java
        boolean retry = true;
        IOException cause = null;
        HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();
        while (retry) {
            try {
                makeRequest();
                return;
            } catch (UnknownHostException e) {
		        if(responseHandler != null) {
		            responseHandler.sendFailureMessage(e, "can't resolve host");
		        }
	        	return;
            }catch (SocketException e){
                // Added to detect host unreachable
                if(responseHandler != null) {
                    responseHandler.sendFailureMessage(e, "can't resolve host");
                }
                return;
            }catch (SocketTimeoutException e){
                if(responseHandler != null) {
                    responseHandler.sendFailureMessage(e, "socket time out");
                }
                return;
            } catch (IOException e) {
            	Log.w(TAG,"makeRequestWithRetries",e);
                cause = e;
                retry = retryHandler.retryRequest(cause, ++executionCount, context);
            } catch (NullPointerException e) {
                // there's a bug in HttpClient 4.0.x that on some occasions causes
                // DefaultRequestExecutor to throw an NPE, see
                // http://code.google.com/p/android/issues/detail?id=5255
                cause = new IOException("NPE in HttpClient" + e.getMessage());
                retry = retryHandler.retryRequest(cause, ++executionCount, context);
            }
        }

        // no retries left, crap out with exception
        ConnectException ex = new ConnectException();
        ex.initCause(cause);       
        //ACRA.getErrorReporter().handleSilentException(cause);
        throw ex;
    }
}
