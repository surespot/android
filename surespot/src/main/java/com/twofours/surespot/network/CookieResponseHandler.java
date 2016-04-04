package com.twofours.surespot.network;
import okhttp3.Cookie;

public abstract class CookieResponseHandler  {

	public abstract void onSuccess(int responseCode, String result, Cookie cookie);
	public abstract void onFailure(Throwable arg0, int code, String content);



}
