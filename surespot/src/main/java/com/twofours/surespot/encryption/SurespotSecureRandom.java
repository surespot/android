package com.twofours.surespot.encryption;

import java.security.SecureRandom;

@SuppressWarnings("serial")
public class SurespotSecureRandom extends SecureRandom {	
	public SurespotSecureRandom() {
		super(new SurespotSecureRandomSpi(),null);
	}
}
