package com.twofours.surespot;

import java.security.KeyPair;

public class SurespotIdentity {
	private String mUsername;
	private KeyPair mKeyPair;

	public SurespotIdentity(String username, KeyPair keyPair) {
		this.mUsername = username;
		this.mKeyPair = keyPair;
	}

	public String getUsername() {
		return mUsername;
	}

	public KeyPair getKeyPair() {
		return mKeyPair;
	}
}
