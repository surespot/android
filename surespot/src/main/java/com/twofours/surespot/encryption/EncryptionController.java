package com.twofours.surespot.encryption;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;
import org.spongycastle.jce.spec.ECParameterSpec;

import android.os.AsyncTask;

import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

public class EncryptionController {
	private static final int PBKDF_ROUNDS_LEGACY = 1000;
	private static final int PBKDF_ROUNDS = 5000;
	private static final int BUFFER_SIZE = 1024;
	private static final String TAG = "EncryptionController";
	private static final int AES_KEY_LENGTH = 32;
	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 16;

	private static ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private static SecureRandom mSecureRandom = new SurespotSecureRandom();

	public static final PublicKey ServerPublicKey = recreatePublicKey("ecdsa", SurespotConstants.SERVER_PUBLIC_KEY);

	public static ECPublicKey recreatePublicKey(String algorithm, String encodedKey) {

		try {
			if (encodedKey != null) {
				X509EncodedKeySpec spec = new X509EncodedKeySpec(decodePublicKey(encodedKey));
				KeyFactory fact = KeyFactory.getInstance(algorithm, "SC");
				ECPublicKey pubKey = (ECPublicKey) fact.generatePublic(spec);
				return pubKey;
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "recreatePublicKey");
		}

		return null;

	}

	public static ECPrivateKey recreatePrivateKey(String algorithm, String encodedKey) {
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(ChatUtils.base64DecodeNowrap(encodedKey));
		try {
			KeyFactory fact = KeyFactory.getInstance(algorithm, "SC");
			ECPrivateKey privKey = (ECPrivateKey) fact.generatePrivate(spec);
			return privKey;
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "recreatePrivateKey");
		}

		return null;
	}

	public static void generateKeyPairs(final IAsyncCallback<KeyPair[]> callback) {
		new AsyncTask<Void, Void, KeyPair[]>() {

			@Override
			protected KeyPair[] doInBackground(Void... arg0) {

				try {
					// generate ECDH keys
					KeyPairGenerator g = KeyPairGenerator.getInstance("ECDH", "SC");
					g.initialize(curve, mSecureRandom);
					KeyPair pair = g.generateKeyPair();
					KeyPair[] pairs = new KeyPair[2];
					pairs[0] = pair;

					// generate ECDSA keys
					KeyPairGenerator gECDSA = KeyPairGenerator.getInstance("ECDSA", "SC");
					gECDSA.initialize(curve, mSecureRandom);
					pair = gECDSA.generateKeyPair();

					pairs[1] = pair;
					return pairs;

				}
				catch (Exception e) {
					SurespotLog.w(TAG, e, "generateKeyPair");
				}

				return null;
			}

			protected void onPostExecute(KeyPair[] result) {
				callback.handleResponse(result);
			}
		}.execute();
	}

	public static KeyPair[] generateKeyPairsSync() {
		KeyPair[] pairs = new KeyPair[2];
		// generate ECDH keys
		KeyPairGenerator g;
		try {
			g = KeyPairGenerator.getInstance("ECDH", "SC");
			g.initialize(curve, mSecureRandom);
			KeyPair pair = g.generateKeyPair();

			pairs[0] = pair;

			// generate ECDSA keys
			KeyPairGenerator gECDSA = KeyPairGenerator.getInstance("ECDSA", "SC");
			gECDSA.initialize(curve, mSecureRandom);
			pair = gECDSA.generateKeyPair();
			pairs[1] = pair;
		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, e, "generateKeyPairsSync");
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, e, "generateKeyPairsSync");
		}
		catch (InvalidAlgorithmParameterException e) {
			SurespotLog.e(TAG, e, "generateKeyPairsSync");
		}
		return pairs;

	}

	public static String encodePublicKey(PublicKey publicKey) {
		byte[] encoded = publicKey.getEncoded();

		// SSL doesn't like any other encoding but DEFAULT
		String unpem = new String(ChatUtils.base64Encode(encoded));
		return "-----BEGIN PUBLIC KEY-----\n" + unpem + "-----END PUBLIC KEY-----";
	}

	public static byte[] decodePublicKey(String publicKey) {
		String afterHeader = publicKey.substring(publicKey.indexOf('\n') + 1);
		String beforeHeader = afterHeader.substring(0, afterHeader.lastIndexOf('\n'));
		return ChatUtils.base64Decode(beforeHeader);
	}

	public static String sign(PrivateKey privateKey, String data, String derivedPassword) {
		return sign(privateKey, data.getBytes(), derivedPassword.getBytes());
	}

	public static String sign(PrivateKey privateKey, byte[] data, byte[] derivedPassword) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");

			// throw some random data in there so the signature is different every time
			byte[] random = new byte[16];
			mSecureRandom.nextBytes(random);

			dsa.initSign(privateKey);
			dsa.update(data);
			dsa.update(derivedPassword);
			dsa.update(random);

			byte[] sig = dsa.sign();
			byte[] signature = new byte[random.length + sig.length];
			System.arraycopy(random, 0, signature, 0, 16);
			System.arraycopy(sig, 0, signature, 16, sig.length);
			return new String(ChatUtils.base64Encode(signature));

		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		return null;

	}
	

	//sign the username, version, and pub keys with the dsa signing key
	public static String sign(PrivateKey privateKey, String username, int version, String dhPubKey, String dsaPubKey) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");

			
			dsa.initSign(privateKey);

			ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
			byte[] vbuffer =  bb.putInt(version).array();
			
			dsa.update(username.getBytes());
			dsa.update(vbuffer);
			dsa.update(dhPubKey.getBytes());
			dsa.update(dsaPubKey.getBytes());

			byte[] sig = dsa.sign();
			
			return new String(ChatUtils.base64EncodeNowrap(sig));

		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		return null;

	}


	public static boolean verifyPublicKey(String signature, String data) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");
			dsa.initVerify(ServerPublicKey);
			dsa.update(data.getBytes());
			return dsa.verify(ChatUtils.base64Decode(signature));
		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, e, "sign");

		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, e, "sign");

		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		return false;
	}


	public static boolean verifySig(PublicKey sigPublicKey, String signature, String username, int version, String data1, String data2) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");
			dsa.initVerify(sigPublicKey);
			byte[] vbuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(version).array();
			dsa.update(username.getBytes());
			dsa.update(vbuffer);
			dsa.update(data1.getBytes());
			dsa.update(data2.getBytes());
			return dsa.verify(ChatUtils.base64DecodeNowrap(signature));
		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, e, "sign");

		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, e, "sign");

		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, e, "sign");
		}
		return false;
	}

	public static byte[] generateSharedSecretSync(PrivateKey privateKey, PublicKey publicKey, boolean hashed) {

		try {
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
			ka.init(privateKey);
			ka.doPhase(publicKey, true);
			byte[] sharedSecret = ka.generateSecret();

			SurespotLog.i(TAG, "generated shared Key hashed: %b", hashed);

			if (!hashed) {
				return sharedSecret;
			}

			SurespotLog.i(TAG, "hashing shared key");

			//hash it
			SHA256Digest digest = new SHA256Digest();
			byte[] digested = new byte[AES_KEY_LENGTH];
			digest.update(sharedSecret, 0, sharedSecret.length);
			digest.doFinal(digested, 0);
			return digested;
		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.i(TAG, icle, "generateSharedSecretSync");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "generateSharedSecretSync");
		}
		return null;
	}

	public static String runEncryptTask(final String ourVersion, final String theirUsername, final String theirVersion, final InputStream in,
			final OutputStream out) {
		final byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		Runnable runnable = new Runnable() {
			@Override
			public void run() {

				byte[] buf = new byte[BUFFER_SIZE]; // input buffer
				try {
					final IvParameterSpec ivParams = new IvParameterSpec(iv);
					Cipher ccm = Cipher.getInstance("AES/GCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(ourVersion, theirUsername, theirVersion, true), 0,
							AES_KEY_LENGTH, "AES");
					ccm.init(Cipher.ENCRYPT_MODE, key, ivParams);

					CipherOutputStream cos = new CipherOutputStream(out, ccm);
					BufferedOutputStream bos = new BufferedOutputStream(cos);

					int i = 0;

					while ((i = in.read(buf)) != -1) {
						if (Thread.interrupted()) {
							break;
						}
						bos.write(buf, 0, i);
						// SurespotLog.v(TAG, "encrypted " + i + " bytes");
					}

					// cos.close();
					bos.close();

					//

				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, icle, "encryptTask");
				}

				catch (Exception e) {
					SurespotLog.w(TAG, e, "encryptTask");
				}
				finally {
					try {
						in.close();
						out.close();
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "encryptTask");
					}
				}
			}
		};

		SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
		return new String(ChatUtils.base64EncodeNowrap(iv));
	}

	public static void runDecryptTask(final String ourVersion, final String username, final String theirVersion, final String ivs, final boolean hashed, final InputStream in,
			final OutputStream out) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {

				byte[] buf = new byte[BUFFER_SIZE]; // input buffer
				try {
					final byte[] iv = ChatUtils.base64DecodeNowrap(ivs);
					BufferedInputStream bis = new BufferedInputStream(in);

					final IvParameterSpec ivParams = new IvParameterSpec(iv);
					Cipher ccm = Cipher.getInstance("AES/GCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion, hashed), 0,
							AES_KEY_LENGTH, "AES");
					ccm.init(Cipher.DECRYPT_MODE, key, ivParams);

					CipherInputStream cis = new CipherInputStream(bis, ccm);
					BufferedOutputStream bos = new BufferedOutputStream(out);

					int i = 0;

					while ((i = cis.read(buf)) != -1) {
						// SurespotLog.v(TAG, "decrypted " + i + " bytes");
						bos.write(buf, 0, i);
					}

					bis.close();
					cis.close();
					bos.close();

				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, icle, "decryptTask");
				}
				catch (IOException e) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, e, "decryptTask");
				}

				catch (Exception e) {
					SurespotLog.w(TAG, e, "decryptTask exception");
				}
				finally {
					try {
						in.close();
						out.close();
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "decryptTask finally");
					}

				}
			}
		};

		SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
	}

	public static String symmetricDecrypt(final String ourVersion, final String username, final String theirVersion, final String ivs, final boolean hashed, final String cipherData) {

		byte[] decrypted = symmetricDecryptBytes(ourVersion, username, theirVersion, ivs, hashed, cipherData);
		if (decrypted == null) {
			return null;
		}
		return new String(decrypted);

	}

	public static byte[] symmetricDecryptBytes(final String ourVersion, final String username, final String theirVersion, final String ivs, final boolean hashed,
			final String cipherData) {
		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = null;
		byte[] iv = null;
		ParametersWithIV ivParams = null;
		try {

			cipherBytes = ChatUtils.base64DecodeNowrap(cipherData);
			iv = ChatUtils.base64DecodeNowrap(ivs);
			byte[] secret = SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion, hashed);
			if (secret == null) {
				return null;
			}
			ivParams = new ParametersWithIV(new KeyParameter(secret, 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(false, ivParams);

			byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

			int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return buf;
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "symmetricDecrypt");
		}
		return null;

	}

	public static byte[] getIv() {
		byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		return iv;
	}

	public static String symmetricEncrypt(final String ourVersion, final String username, final String theirVersion, final String plaintext, byte[] iv) {

		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());
		// byte[] iv = new byte[IV_LENGTH];
		// mSecureRandom.nextBytes(iv);
		ParametersWithIV ivParams;
		try {
			ivParams = new ParametersWithIV(new KeyParameter(SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion, true), 0,
					AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(true, ivParams);

			byte[] enc = plaintext.getBytes();
			byte[] buf = new byte[ccm.getOutputSize(enc.length)];

			int len = ccm.processBytes(enc, 0, enc.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return new String(ChatUtils.base64EncodeNowrap(buf));

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, icle, "symmetricEncrypt");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "symmetricEncrypt");
		}
		return null;
	}

	public static byte[] symmetricEncrypt(final String ourVersion, final String username, final String theirVersion, final byte[] plainBytes, byte[] iv) {

		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());
		// byte[] iv = new byte[IV_LENGTH];
		// mSecureRandom.nextBytes(iv);
		ParametersWithIV ivParams;
		try {
			ivParams = new ParametersWithIV(new KeyParameter(SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion, true), 0,
					AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(true, ivParams);

			byte[] enc = plainBytes;
			byte[] buf = new byte[ccm.getOutputSize(enc.length)];

			int len = ccm.processBytes(enc, 0, enc.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return buf;

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, icle, "symmetricEncrypt");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "symmetricEncrypt");
		}
		return null;
	}

	/**
	 * Derive key from password.
	 * 
	 * @param password
	 * @param plaintext
	 * @return
	 */
	public static byte[] symmetricEncryptSyncPK(final String password, final String plaintext) {

		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());
		byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		ParametersWithIV ivParams;
		try {
			byte[][] derived = EncryptionController.derive(password);
			ivParams = new ParametersWithIV(new KeyParameter(derived[1], 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(true, ivParams);

			byte[] enc = plaintext.getBytes();
			byte[] buf = new byte[ccm.getOutputSize(enc.length)];

			int len = ccm.processBytes(enc, 0, enc.length, buf, 0);

			len += ccm.doFinal(buf, len);

			byte[] returnBuffer = new byte[IV_LENGTH + SALT_LENGTH + buf.length];

			System.arraycopy(iv, 0, returnBuffer, 0, IV_LENGTH);
			System.arraycopy(derived[0], 0, returnBuffer, IV_LENGTH, SALT_LENGTH);
			System.arraycopy(buf, 0, returnBuffer, IV_LENGTH + SALT_LENGTH, buf.length);

			return returnBuffer;

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, icle, "symmetricEncryptSyncPK");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "symmetricEncryptSyncPK");
		}
		return null;

	}

	/**
	 * Derive key from password
	 * 
	 * @return
	 */
	public static String symmetricDecryptSyncPK(final String password, final byte[] cipherData) {
		if (cipherData == null) {
			return null;
		}
		
		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = new byte[cipherData.length - IV_LENGTH - SALT_LENGTH];
		byte[] iv = new byte[IV_LENGTH];
		byte[] salt = new byte[SALT_LENGTH];
		ParametersWithIV ivParams = null;
		try {
			System.arraycopy(cipherData, 0, iv, 0, IV_LENGTH);
			System.arraycopy(cipherData, IV_LENGTH, salt, 0, SALT_LENGTH);
			System.arraycopy(cipherData, IV_LENGTH + SALT_LENGTH, cipherBytes, 0, cipherData.length - IV_LENGTH - SALT_LENGTH);

			byte[] derived = derive(password, salt);
			if (derived == null) {
				return null;
			}
			ivParams = new ParametersWithIV(new KeyParameter(derived, 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(false, ivParams);

			byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

			int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return new String(buf);
		}
		catch (InvalidCipherTextException e) {
			SurespotLog.i(TAG, e, "symmetricDecryptSyncPK");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "symmetricDecryptSyncPK");
		}
		return null;

	}
	
	public static byte[][] derive(String password) {
		return derive(password, PBKDF_ROUNDS_LEGACY);
	
	}

	public static byte[][] derive(String password, int iterationCount) {		
		int saltLength = SALT_LENGTH;
		int keyLength = AES_KEY_LENGTH * 8;

		byte[][] derived = new byte[2][];
		byte[] keyBytes = null;
		SecureRandom random = new SurespotSecureRandom();
		byte[] salt = new byte[saltLength];
		random.nextBytes(salt);

		PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
		gen.init(password.getBytes(), salt, iterationCount);
		keyBytes = ((KeyParameter) gen.generateDerivedParameters(keyLength)).getKey();

		derived[0] = salt;
		derived[1] = keyBytes;
		return derived;
	}
	
	public static byte[] derive(String password, byte[] salt) {
		return derive(password, salt, PBKDF_ROUNDS_LEGACY);
	}
	
	public static byte[] derive(String password, byte[] salt, int iterationCount) {		
		int keyLength = AES_KEY_LENGTH * 8;

		byte[] keyBytes = null;

		PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
		gen.init(password.getBytes(), salt, iterationCount);
		keyBytes = ((KeyParameter) gen.generateDerivedParameters(keyLength)).getKey();

		return keyBytes;
	}
	
	public static byte[] encryptData(final String password, final byte[] plaindata) {

		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());
		byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		ParametersWithIV ivParams;
		try {
			byte[][] derived = EncryptionController.derive(password, PBKDF_ROUNDS);
			ivParams = new ParametersWithIV(new KeyParameter(derived[1], 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(true, ivParams);

			
			byte[] buf = new byte[ccm.getOutputSize(plaindata.length)];

			int len = ccm.processBytes(plaindata, 0, plaindata.length, buf, 0);

			len += ccm.doFinal(buf, len);

			byte[] returnBuffer = new byte[IV_LENGTH + SALT_LENGTH + buf.length];

			System.arraycopy(iv, 0, returnBuffer, 0, IV_LENGTH);
			System.arraycopy(derived[0], 0, returnBuffer, IV_LENGTH, SALT_LENGTH);
			System.arraycopy(buf, 0, returnBuffer, IV_LENGTH + SALT_LENGTH, buf.length);

			return returnBuffer;

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, icle, "encryptData");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "encryptData");
		}
		return null;

	}

	/**
	 * Derive key from password
	 * 
	 * @return
	 */
	public static byte[] decryptData(final String password, final byte[] cipherData) {
		if (cipherData == null) {
			return null;
		}
		
		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = new byte[cipherData.length - IV_LENGTH - SALT_LENGTH];
		byte[] iv = new byte[IV_LENGTH];
		byte[] salt = new byte[SALT_LENGTH];
		ParametersWithIV ivParams = null;
		try {
			System.arraycopy(cipherData, 0, iv, 0, IV_LENGTH);
			System.arraycopy(cipherData, IV_LENGTH, salt, 0, SALT_LENGTH);
			System.arraycopy(cipherData, IV_LENGTH + SALT_LENGTH, cipherBytes, 0, cipherData.length - IV_LENGTH - SALT_LENGTH);

			byte[] derived = derive(password, salt, PBKDF_ROUNDS);
			if (derived == null) {
				return null;
			}
			ivParams = new ParametersWithIV(new KeyParameter(derived, 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(false, ivParams);

			byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

			int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return buf;
		}
		catch (InvalidCipherTextException e) {
			SurespotLog.i(TAG, e, "decryptData");
		}
		catch (Exception e) {
			SurespotLog.w(TAG, e, "decryptData");
		}
		return null;

	}

}
