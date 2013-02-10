package com.twofours.surespot;

import java.security.KeyPair;

import ch.boye.httpclientandroidlib.cookie.Cookie;

public class SurespotIdentity {
	private String mUsername;
	private KeyPair mKeyPair;
	private Cookie mCookie;

	public SurespotIdentity(String username, KeyPair keyPair, Cookie cookie) {
		this.mUsername = username;
		this.mKeyPair = keyPair;
		this.mCookie = cookie;
	}

	public String getUsername() {
		return mUsername;
	}

	public KeyPair getKeyPair() {
		return mKeyPair;
	}

	public Cookie getCookie() {
		return mCookie;
	}

	public void setCookie(Cookie cookie) {
		this.mCookie = cookie;
	}

}
