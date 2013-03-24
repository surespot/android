package com.twofours.surespot.encryption;

import java.security.SecureRandomSpi;

import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.prng.DigestRandomGenerator;
import org.spongycastle.crypto.prng.RandomGenerator;

import com.twofours.surespot.common.SurespotLog;


//work around this massive problem by using bouncy castle PRNG and linux random device
//http://www.scribd.com/doc/131955288/Randomly-Failed-The-State-of-Randomness-in-Current-Java-Implementations
@SuppressWarnings("serial")
public class SurespotSecureRandomSpi extends SecureRandomSpi {
	private static final String TAG = "SurespotSecureRandomSpi";
	private RandomGenerator mGenerator;

	public SurespotSecureRandomSpi() {
		mGenerator = new DigestRandomGenerator(new SHA256Digest());
		mGenerator.addSeedMaterial(RandomBitsSupplier.getRandomBits(32));
	}

	@Override
	protected void engineSetSeed(byte[] seed) {
		mGenerator.addSeedMaterial(seed);
	}

	@Override
	protected void engineNextBytes(byte[] bytes) {
		SurespotLog.v(TAG, "engineNextBytes");
		mGenerator.nextBytes(bytes);
	}

	@Override
	protected synchronized byte[] engineGenerateSeed(int numBytes) {

		byte[] myBytes; // byte[] for bytes returned by "nextBytes()"

		if (numBytes < 0) {
			throw new NegativeArraySizeException(Integer.toString(numBytes));
		}
		if (numBytes == 0) {
			return new byte[0];
		}

		myBytes = new byte[numBytes];
		mGenerator.nextBytes(myBytes);

		return myBytes;
	}

}
