package com.twofours.surespot.network;

import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotLog;

import java.io.IOException;
import java.net.URL;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by adam on 4/1/16.
 */
public class UserAgentInterceptor implements Interceptor {

    private static final String TAG = "UserAgentInteceptor";
    private final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        SurespotLog.v(TAG,"user agent for request host: %s", originalRequest.url().host());
       //if request is going to external URL don't rewrite user agent
        if (originalRequest.url().host().equals(new URL(SurespotConfiguration.getBaseUrl()).getHost())) {

            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", userAgent)
                    .build();
            SurespotLog.v(TAG,"setting user agent: %s, for request: %s", userAgent, originalRequest.url());
            return chain.proceed(requestWithUserAgent);
        }
        else {
            SurespotLog.v(TAG,"not setting user agent for request: %s", originalRequest.url());
            return chain.proceed(originalRequest);
        }
    }
}
