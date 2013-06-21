package com.twofours.surespot.encryption;

import java.security.PublicKey;

public class PublicKeys {
	private String mVersion;
	private PublicKey mDHKey;
	private PublicKey mDSAKey;
	private long mLastModified;

	public PublicKeys(String version, PublicKey dHKey, PublicKey dSAKey, long lastModified) {
		mVersion = version;
		mDHKey = dHKey;
		mDSAKey = dSAKey;
		mLastModified = lastModified;	}

	public String getVersion() {
		return mVersion;
	}

	public PublicKey getDHKey() {
		return mDHKey;
	}

	public PublicKey getDSAKey() {
		return mDSAKey;
	}

	public long getLastModified() {
		return mLastModified;
	}

	

}
