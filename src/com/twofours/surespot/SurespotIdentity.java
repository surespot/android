package com.twofours.surespot;

import java.security.KeyPair;

public class SurespotIdentity {
	private String mUsername;
	private KeyPair mKeyPairDH;
	private KeyPair mKeyPairECDSA;

	public SurespotIdentity(String username, KeyPair keyPairDH, KeyPair keyPairECDSA) {
		this.mUsername = username;
		this.mKeyPairDH = keyPairDH;
		this.mKeyPairECDSA = keyPairECDSA;
	}

	public String getUsername() {
		return mUsername;
	}

	public KeyPair getKeyPairDH() {
		return mKeyPairDH;
	}

	public KeyPair getKeyPairECDSA() {
		return mKeyPairECDSA;
	}
}
