package com.twofours.surespot.network;

import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.AsyncHttpResponseHandler;

public abstract class CookieResponseHandler extends AsyncHttpResponseHandler {

	public abstract void onSuccess(int responseCode, String result, Cookie cookie);

}
