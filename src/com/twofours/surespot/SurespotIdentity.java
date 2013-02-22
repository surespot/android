package com.twofours.surespot;

import java.security.KeyPair;
import java.util.Collection;
import java.util.HashMap;

public class SurespotIdentity {

	private String mUsername;
	private String mLatestVersion;

	private HashMap<String, PrivateKeyPairs> mKeyPairs;

	public SurespotIdentity(String username) {
		this.mUsername = username;
		mKeyPairs = new HashMap<String, PrivateKeyPairs>();
	}

	public void addKeyPairs(String version, KeyPair keyPairDH, KeyPair keyPairDSA) {
		if (mLatestVersion == null || version.compareTo(mLatestVersion) > 0) {
			mLatestVersion = version;
		}

		mKeyPairs.put(version, new PrivateKeyPairs(version, keyPairDH, keyPairDSA));

	}

	public String getUsername() {
		return mUsername;
	}

	public KeyPair getKeyPairDH() {
		return mKeyPairs.get(mLatestVersion).getKeyPairDH();
	}

	public KeyPair getKeyPairDSA() {
		return mKeyPairs.get(mLatestVersion).getKeyPairDSA();
	}

	public KeyPair getKeyPairDH(String version) {
		return mKeyPairs.get(version).getKeyPairDH();
	}

	public KeyPair getKeyPairDSA(String version) {
		return mKeyPairs.get(version).getKeyPairDSA();
	}

	public Collection<PrivateKeyPairs> getKeyPairs() {
		return mKeyPairs.values();
	}

	public String getLatestVersion() {
		return mLatestVersion;
	}
}
