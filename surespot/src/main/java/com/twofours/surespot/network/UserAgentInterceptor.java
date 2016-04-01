package com.twofours.surespot.network;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by adam on 4/1/16.
 */
public class UserAgentInterceptor implements Interceptor {

    private final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        Request requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .build();
        return chain.proceed(requestWithUserAgent);
    }
}
