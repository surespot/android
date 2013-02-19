package com.twofours.surespot;

import java.security.KeyPair;

public class SurespotIdentity {
	private String mUsername;
	private KeyPair mKeyPairDH;
	private KeyPair mKeyPairECDSA;
	private String mSignature;

	public SurespotIdentity(String username, KeyPair keyPairDH, KeyPair keyPairECDSA, String signature) {
		this.mUsername = username;
		this.mKeyPairDH = keyPairDH;
		this.mKeyPairECDSA = keyPairECDSA;
		this.mSignature = signature;
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

	public String getSignature() {
		return mSignature;
	}
}
