package com.twofours.surespot.encryption;

import java.security.PublicKey;

public class PublicKeys {
	private String mVersion;
	private PublicKey mDHKey;
	private PublicKey mDSAKey;

	public PublicKeys(String version, PublicKey dHKey, PublicKey dSAKey) {
		mVersion = version;
		mDHKey = dHKey;
		mDSAKey = dSAKey;
	}

	public String getVersion() {
		return mVersion;
	}

	public PublicKey getDHKey() {
		return mDHKey;
	}

	public PublicKey getDSAKey() {
		return mDSAKey;
	}

}
