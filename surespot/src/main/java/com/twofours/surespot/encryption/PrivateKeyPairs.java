package com.twofours.surespot.encryption;

import java.security.KeyPair;

public class PrivateKeyPairs {
	private String mVersion;
	private KeyPair mKeyPairDH;
	private KeyPair mKeyPairDSA;

	public PrivateKeyPairs(String version, KeyPair keyPairDH, KeyPair keyPairDSA) {
		mVersion = version;
		mKeyPairDH = keyPairDH;
		mKeyPairDSA = keyPairDSA;
	}

	public KeyPair getKeyPairDH() {
		return mKeyPairDH;
	}

	public KeyPair getKeyPairDSA() {
		return mKeyPairDSA;
	}

	public String getVersion() {
		return mVersion;
	}

}
