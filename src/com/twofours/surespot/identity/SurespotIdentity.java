package com.twofours.surespot.identity;

import java.security.KeyPair;
import java.util.Collection;
import java.util.HashMap;

import com.twofours.surespot.encryption.PrivateKeyPairs;

public class SurespotIdentity {

	private String mUsername;
	private String mLatestVersion;
	private String mSalt;

	private HashMap<String, PrivateKeyPairs> mKeyPairs;

	public SurespotIdentity(String username, String salt) {
		this.mUsername = username;
		mSalt = salt;
		mKeyPairs = new HashMap<String, PrivateKeyPairs>();
	}

	public void addKeyPairs(String version, KeyPair keyPairDH, KeyPair keyPairDSA) {
		if (mLatestVersion == null || (Integer.parseInt(version) >  Integer.parseInt(mLatestVersion))) {
			mLatestVersion = version;
		}

		mKeyPairs.put(version, new PrivateKeyPairs(version, keyPairDH, keyPairDSA));

	}
	
	public String getUsername() {
		return mUsername;
	}
	
	public String getSalt() {
		return mSalt;
	}
	
	public void setSalt(String newSalt) {
		mSalt = newSalt;		
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
