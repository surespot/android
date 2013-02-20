package com.twofours.surespot;

import java.security.PublicKey;

public class SurespotPublicIdentity {
	private String mUsername;
	private PublicKey mDHPubKey;
	private PublicKey mDSAPubKey;
	private byte[] mDHSignature;
	private byte[] mDSASignature;

	public SurespotPublicIdentity(String username, PublicKey dhPub, PublicKey dsaPub, String dhSig, String dsaSig) {
		this.mUsername = username;
		mDHPubKey = dhPub;
		mDSAPubKey = dsaPub;
		mDHSignature = dhSig.getBytes();
		mDSASignature = dsaSig.getBytes();
	}

	public PublicKey getDHPubKey() {
		return mDHPubKey;
	}

	public void setDHPubKey(PublicKey dHPubKey) {
		mDHPubKey = dHPubKey;
	}

	public PublicKey getDSAPubKey() {
		return mDSAPubKey;
	}

	public void setDSAPubKey(PublicKey dSAPubKey) {
		mDSAPubKey = dSAPubKey;
	}

	public byte[] getDHSignature() {
		return mDHSignature;
	}

	public void setDHSignature(byte[] dHSignature) {
		mDHSignature = dHSignature;
	}

	public byte[] getDSASignature() {
		return mDSASignature;
	}

	public void setDSASignature(byte[] dSASignature) {
		mDSASignature = dSASignature;
	}

	public void setUsername(String username) {
		mUsername = username;
	}

	public String getUsername() {
		return mUsername;
	}

}
