package com.twofours.surespot.encryption;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;
import org.spongycastle.jce.spec.ECParameterSpec;

import android.os.AsyncTask;
import android.util.Base64;

import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class EncryptionController {
	private static final int BUFFER_SIZE = 1024;
	private static final String TAG = "EncryptionController";
	private static final int AES_KEY_LENGTH = 32;
	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 16;

	private static ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private static SecureRandom mSecureRandom = new SecureRandom();

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
			SurespotLog.w(TAG, "recreatePublicKey", e);
		}

		return null;

	}

	public static ECPrivateKey recreatePrivateKey(String algorithm, String encodedKey) {
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Utils.base64Decode(encodedKey));
		try {
			KeyFactory fact = KeyFactory.getInstance(algorithm, "SC");
			ECPrivateKey privKey = (ECPrivateKey) fact.generatePrivate(spec);
			return privKey;
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "recreatePrivateKey", e);
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
					g.initialize(curve, new SecureRandom());
					KeyPair pair = g.generateKeyPair();
					KeyPair[] pairs = new KeyPair[2];
					pairs[0] = pair;

					// generate ECDSA keys
					KeyPairGenerator gECDSA = KeyPairGenerator.getInstance("ECDSA", "SC");
					gECDSA.initialize(curve, new SecureRandom());
					pair = gECDSA.generateKeyPair();

					pairs[1] = pair;
					return pairs;

				}
				catch (Exception e) {
					SurespotLog.w(TAG, "generateKeyPair", e);
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
			g.initialize(curve, new SecureRandom());
			KeyPair pair = g.generateKeyPair();

			pairs[0] = pair;

			// generate ECDSA keys
			KeyPairGenerator gECDSA = KeyPairGenerator.getInstance("ECDSA", "SC");
			gECDSA.initialize(curve, new SecureRandom());
			pair = gECDSA.generateKeyPair();
			pairs[1] = pair;
		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, "generateKeyPairsSync", e);
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, "generateKeyPairsSync", e);
		}
		catch (InvalidAlgorithmParameterException e) {
			SurespotLog.e(TAG, "generateKeyPairsSync", e);
		}
		return pairs;

	}

	public static String encodePublicKey(PublicKey publicKey) {
		byte[] encoded = publicKey.getEncoded();

		// SSL doesn't like any other encoding but DEFAULT
		String unpem = new String(Base64.encode(encoded, Base64.DEFAULT));
		return "-----BEGIN PUBLIC KEY-----\n" + unpem + "-----END PUBLIC KEY-----";
	}

	public static byte[] decodePublicKey(String publicKey) {
		String afterHeader = publicKey.substring(publicKey.indexOf('\n') + 1);
		String beforeHeader = afterHeader.substring(0, afterHeader.lastIndexOf('\n'));
		return Base64.decode(beforeHeader.getBytes(), Base64.DEFAULT);
	}

	public static String sign(PrivateKey privateKey, String sign1, String sign2) {
		return sign(privateKey, sign1.getBytes(), sign2.getBytes());
	}

	public static String sign(PrivateKey privateKey, byte[] sign1, byte[] sign2) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");

			// throw some random data in there so the signature is different every time
			byte[] random = new byte[16];
			mSecureRandom.nextBytes(random);

			dsa.initSign(privateKey);
			dsa.update(sign1);
			dsa.update(sign2);
			dsa.update(random);

			byte[] sig = dsa.sign();
			byte[] signature = new byte[random.length + sig.length];
			System.arraycopy(random, 0, signature, 0, 16);
			System.arraycopy(sig, 0, signature, 16, sig.length);
			return new String(Base64.encode(signature, Base64.DEFAULT));

		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		return null;

	}

	public static boolean verifyPublicKey(String signature, String data) {
		try {
			Signature dsa = Signature.getInstance("SHA256withECDSA", "SC");
			dsa.initVerify(ServerPublicKey);
			dsa.update(data.getBytes());
			return dsa.verify(Base64.decode(signature.getBytes(), Base64.DEFAULT));
		}
		catch (SignatureException e) {
			SurespotLog.e(TAG, "sign", e);

		}
		catch (NoSuchAlgorithmException e) {
			SurespotLog.e(TAG, "sign", e);

		}
		catch (InvalidKeyException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		catch (NoSuchProviderException e) {
			SurespotLog.e(TAG, "sign", e);
		}
		return false;

	}

	public static byte[] generateSharedSecretSync(PrivateKey privateKey, PublicKey publicKey) {

		try {
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
			ka.init(privateKey);
			ka.doPhase(publicKey, true);
			byte[] sharedSecret = ka.generateSecret();

			// SurespotLog.d(TAG, username + " shared Key: " + new String(Utils.base64Encode(new BigInteger(sharedSecret).toByteArray())));
			return sharedSecret;

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, "generateSharedSecretSync", icle);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "generateSharedSecretSync", e);
		}
		return null;
	}

	public static String runEncryptTask(final String ourVersion, final String theirUsername, final String theirVersion,
			final InputStream in, final OutputStream out) {
		final byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		Runnable runnable = new Runnable() {
			@Override
			public void run() {

				byte[] buf = new byte[BUFFER_SIZE]; // input buffer
				try {
					final IvParameterSpec ivParams = new IvParameterSpec(iv);
					Cipher ccm = Cipher.getInstance("AES/GCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(ourVersion, theirUsername,
							theirVersion), 0, AES_KEY_LENGTH, "AES");
					ccm.init(Cipher.ENCRYPT_MODE, key, ivParams);

					CipherOutputStream cos = new CipherOutputStream(out, ccm);
					BufferedOutputStream bos = new BufferedOutputStream(cos);

					int i = 0;

					while ((i = in.read(buf)) != -1) {
						if (Thread.interrupted()) {
							break;
						}
						bos.write(buf, 0, i);
					}

					// cos.close();
					bos.close();

					SurespotLog.v(TAG, "read/write " + i + " bytes");

				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, "encryptTask", icle);
				}

				catch (Exception e) {
					SurespotLog.w(TAG, "encryptTask", e);
				}
				finally {
					try {
						in.close();
						out.close();
					}
					catch (IOException e) {
						SurespotLog.w(TAG, "encryptTask", e);
					}
				}
			}
		};

		SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
		return new String(Utils.base64Encode(iv));
	}

	public static void runDecryptTask(final String ourVersion, final String username, final String theirVersion, final String ivs,
			final InputStream in, final OutputStream out) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {

				byte[] buf = new byte[BUFFER_SIZE]; // input buffer
				try {
					final byte[] iv = Utils.base64Decode(ivs);
					BufferedInputStream bis = new BufferedInputStream(in);

					final IvParameterSpec ivParams = new IvParameterSpec(iv);
					Cipher ccm = Cipher.getInstance("AES/GCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(ourVersion, username,
							theirVersion), 0, AES_KEY_LENGTH, "AES");
					ccm.init(Cipher.DECRYPT_MODE, key, ivParams);

					CipherInputStream cis = new CipherInputStream(bis, ccm);
					BufferedOutputStream bos = new BufferedOutputStream(out);

					int i = 0;

					while ((i = cis.read(buf)) != -1) {
						bos.write(buf, 0, i);
					}

					bis.close();
					cis.close();
					bos.close();

					SurespotLog.v(TAG, "read/write " + i + " bytes");

				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, "decryptTask", icle);
				}

				catch (Exception e) {
					SurespotLog.w(TAG, "decryptTask exception", e);
				}
				finally {
					try {
						in.close();
						out.close();
					}
					catch (IOException e) {
						SurespotLog.w(TAG, "decryptTask finally", e);
					}

				}
			}
		};

		SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
	}

	public static String symmetricDecrypt(final String ourVersion, final String username, final String theirVersion, final String ivs,
			final String cipherData) {
		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = null;
		byte[] iv = null;
		ParametersWithIV ivParams = null;
		try {

			cipherBytes = Utils.base64Decode(cipherData);
			iv = Utils.base64Decode(ivs);
			byte[] secret = SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion);
			if (secret == null) {
				return null;
			}
			ivParams = new ParametersWithIV(new KeyParameter(secret, 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(false, ivParams);

			byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

			int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);

			len += ccm.doFinal(buf, len);
			return new String(buf);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricDecrypt", e);
		}
		return null;

	}

	public static byte[] getIv() {
		byte[] iv = new byte[IV_LENGTH];
		mSecureRandom.nextBytes(iv);
		return iv;
	}

	public static String symmetricEncrypt(final String ourVersion, final String username, final String theirVersion,
			final String plaintext, byte[] iv) {

		GCMBlockCipher ccm = new GCMBlockCipher(new AESLightEngine());
		// byte[] iv = new byte[IV_LENGTH];
		// mSecureRandom.nextBytes(iv);
		ParametersWithIV ivParams;
		try {
			ivParams = new ParametersWithIV(new KeyParameter(SurespotApplication.getCachingService().getSharedSecret(ourVersion, username,
					theirVersion), 0, AES_KEY_LENGTH), iv);

			ccm.reset();
			ccm.init(true, ivParams);

			byte[] enc = plaintext.getBytes();
			byte[] buf = new byte[ccm.getOutputSize(enc.length)];

			int len = ccm.processBytes(enc, 0, enc.length, buf, 0);

			len += ccm.doFinal(buf, len);
			// String[] returns = new String[2];

			// returns[0] = new String(Utils.base64Encode(iv));
			// returns[1] = new String(Utils.base64Encode(buf));

			return new String(Utils.base64Encode(buf));

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, "symmetricEncrypt", icle);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricEncrypt", e);
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
			SurespotLog.v(TAG, "symmetricEncryptSyncPK", icle);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricEncryptSyncPK", e);
		}
		return null;

	}

	/**
	 * Derive key from password
	 * 
	 * @return
	 */
	public static String symmetricDecryptSyncPK(final String password, final byte[] cipherData) {

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
		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricDecryptSyncPK", e);
		}
		return null;

	}

	private static byte[][] derive(String password) {
		int iterationCount = 1000;
		int saltLength = SALT_LENGTH;
		int keyLength = AES_KEY_LENGTH;

		byte[][] derived = new byte[2][];
		byte[] keyBytes = null;
		SecureRandom random = new SecureRandom();
		byte[] salt = new byte[saltLength];
		random.nextBytes(salt);
		KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
		SecretKeyFactory keyFactory;
		try {
			keyFactory = SecretKeyFactory.getInstance("PBEWITHSHA-256AND256BITAES-CBC-BC", "SC");
			keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
		}
		catch (Exception e) {
			SurespotLog.e(TAG, "deriveKey", e);
		}

		derived[0] = salt;
		derived[1] = keyBytes;
		return derived;
	}

	private static byte[] derive(String password, byte[] salt) {
		int iterationCount = 1000;
		int keyLength = AES_KEY_LENGTH;

		byte[] keyBytes = null;

		KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
		SecretKeyFactory keyFactory;
		try {
			keyFactory = SecretKeyFactory.getInstance("PBEWITHSHA-256AND256BITAES-CBC-BC", "SC");
			keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
		}
		catch (Exception e) {
			SurespotLog.e(TAG, "derive", e);
		}

		return keyBytes;
	}

}
