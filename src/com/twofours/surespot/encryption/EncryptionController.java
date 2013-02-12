package com.twofours.surespot.encryption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.modes.CCMBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;

import android.os.AsyncTask;

import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotIdentity;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class EncryptionController {
	private static final String TAG = "EncryptionController";
	private static final int AES_KEY_LENGTH = 32;
	private static final int SALT_LENGTH = 16;

	private static ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private static SecureRandom mSecureRandom = new SecureRandom();

	public static ECPublicKey recreatePublicKey(String encodedKey) {

		try {
			if (encodedKey != null) {
				ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(curve.getCurve().decodePoint(Utils.base64Decode(encodedKey)), curve);
				KeyFactory fact = KeyFactory.getInstance("ECDH", "SC");
				ECPublicKey pubKey = (ECPublicKey) fact.generatePublic(pubKeySpec);
				return pubKey;
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "recreatePublicKey", e);
		}

		return null;

	}

	public static ECPrivateKey recreatePrivateKey(String encodedKey) {
		// recreate key from hex string
		ECPrivateKeySpec priKeySpec = new ECPrivateKeySpec(new BigInteger(Utils.base64Decode(encodedKey)), curve);

		try {
			KeyFactory fact = KeyFactory.getInstance("ECDH", "SC");
			ECPrivateKey privKey = (ECPrivateKey) fact.generatePrivate(priKeySpec);
			return privKey;
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "recreatePrivateKey", e);
		}

		return null;
	}

	public static void generateKeyPair(final IAsyncCallback<KeyPair> callback) {
		new AsyncTask<Void, Void, KeyPair>() {

			@Override
			protected KeyPair doInBackground(Void... arg0) {
				// perform async

				try {
					KeyPairGenerator g = KeyPairGenerator.getInstance("ECDH", "SC");
					g.initialize(curve, new SecureRandom());
					KeyPair pair = g.generateKeyPair();
					return pair;

				}
				catch (Exception e) {
					SurespotLog.w(TAG, "generateKeyPair", e);
				}

				return null;
			}

			protected void onPostExecute(KeyPair result) {
				callback.handleResponse(result);
			}
		}.execute();
	}

	public static String encodePublicKey(ECPublicKey publicKey) {
		return new String(Utils.base64Encode(publicKey.getQ().getEncoded()));
	}

	//
	// private static void generateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
	// new AsyncGenerateSharedSecret(username, callback).execute();
	// }

	public static byte[] generateSharedSecretSync(String username) {
		SurespotIdentity identity = IdentityController.getIdentity(SurespotApplication.getContext());
		if (identity == null)
			return null;
		try {
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
			ka.init(identity.getKeyPair().getPrivate());
			ka.doPhase(SurespotApplication.getCachingService().getPublickey(username), true);
			byte[] sharedSecret = ka.generateSecret();

			SurespotLog.d(TAG, username + " shared Key: " + new String(Utils.base64Encode(new BigInteger(sharedSecret).toByteArray())));
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

	public static void symmetricBase64Decrypt(final String username, final String ivs, final String cipherData,
			final IAsyncCallback<byte[]> callback) {
		new AsyncTask<Void, Void, byte[]>() {

			@Override
			protected byte[] doInBackground(Void... params) {

				byte[] buf = new byte[1024]; // input buffer

				try {
					Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");
					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(username), 0, AES_KEY_LENGTH,
							"AES");
					byte[] cipherBytes = Utils.base64Decode(cipherData);
					byte[] iv = Utils.base64Decode(ivs);
					IvParameterSpec ivParams = new IvParameterSpec(iv);
					ByteArrayInputStream in = new ByteArrayInputStream(cipherBytes);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					CipherOutputStream cos = new CipherOutputStream(out, ccm);

					ccm.init(Cipher.DECRYPT_MODE, key, ivParams);
					int i = 0;
					while ((i = in.read(buf)) != -1) {
						cos.write(buf, 0, i);
					}

					in.close();
					cos.close();
					out.close();

					return out.toByteArray();
				}

				catch (Exception e) {
					SurespotLog.w(TAG, "symmetricBase64Decrypt", e);
				}
				return null;
			}

			@Override
			protected void onPostExecute(byte[] result) {
				callback.handleResponse(result);
			}
		}.execute();

	}

	public synchronized static byte[] symmetricBase64DecryptSync(final String username, final String ivs, final String cipherData) {

		byte[] buf = new byte[1024]; // input buffer

		try {
			Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");
			SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(username), 0, AES_KEY_LENGTH, "AES");
			byte[] cipherBytes = Utils.base64Decode(cipherData);
			byte[] iv = Utils.base64Decode(ivs);
			IvParameterSpec ivParams = new IvParameterSpec(iv);
			ByteArrayInputStream in = new ByteArrayInputStream(cipherBytes);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			CipherOutputStream cos = new CipherOutputStream(out, ccm);

			ccm.init(Cipher.DECRYPT_MODE, key, ivParams);
			int i = 0;
			while ((i = in.read(buf)) != -1) {
				cos.write(buf, 0, i);
			}

			in.close();
			cos.close();
			out.close();

			return out.toByteArray();
		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, "symmetricBase64DecryptSync", icle);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricBase64DecryptSync", e);
		}
		return null;

	}

	// public synchronized static OutputStream symmetricBase64DecryptSync(final String username, final String ivs,
	// final InputStream base64data, ByteArrayOutputStream outStream) {
	//
	// byte[] buf = new byte[1024]; // input buffer
	//
	// try {
	// Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");
	// SecretKey key = new SecretKeySpec(mSharedSecrets.get(username), 0, AES_KEY_LENGTH, "AES");
	// // data.r
	// // byte[] cipherBytes = Utils.base64Decode();
	// byte[] iv = Utils.base64Decode(ivs);
	// IvParameterSpec ivParams = new IvParameterSpec(iv);
	// // ByteArrayInputStream in = new ByteArrayInputStream(cipherBytes);
	// CipherOutputStream cos = new CipherOutputStream(outStream, ccm);
	// Base64InputStream in = new Base64InputStream(base64data, Base64.NO_WRAP | Base64.URL_SAFE);
	//
	// ccm.init(Cipher.DECRYPT_MODE, key, ivParams);
	// int i = 0;
	// while ((i = in.read(buf)) != -1) {
	// cos.write(buf, 0, i);
	// }
	//
	// in.close();
	// cos.close();
	// // out.close();
	// outStream.close();
	//
	// return cos;
	// }
	// catch (InvalidCacheLoadException icle) {
	// // will occur if couldn't load key
	// SurespotLog.v(TAG, "symmetricBase64DecryptSync", icle);
	// }
	// catch (Exception e) {
	// SurespotLog.w(TAG, "symmetricBase64DecryptSync", e);
	// }
	// return null;
	//
	// }

	public static void symmetricBase64Encrypt(final String username, final String base64data, final IAsyncCallback<String[]> callback) {
		new AsyncTask<Void, Void, String[]>() {
			@Override
			protected String[] doInBackground(Void... params) {
				byte[] iv = new byte[15];
				byte[] buf = new byte[1024]; // input buffer
				byte[] enc = Utils.base64Decode(base64data);

				ByteArrayInputStream in = new ByteArrayInputStream(enc);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				mSecureRandom.nextBytes(iv);
				IvParameterSpec ivParams = new IvParameterSpec(iv);

				try {
					Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(username), 0, AES_KEY_LENGTH,
							"AES");
					ccm.init(Cipher.ENCRYPT_MODE, key, ivParams);
					CipherOutputStream cos = new CipherOutputStream(out, ccm);

					int i = 0;

					while ((i = in.read(buf)) != -1) {
						cos.write(buf, 0, i);
					}

					in.close();
					cos.close();
					out.close();

					String[] returns = new String[2];

					returns[0] = new String(Utils.base64Encode(iv));
					returns[1] = new String(Utils.base64Encode(out.toByteArray()));

					return returns;
				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, "symmetricBase64Encrypt", icle);
				}
				catch (Exception e) {
					SurespotLog.w(TAG, "symmetricBase64Encrypt", e);
				}
				return null;

			}

			@Override
			protected void onPostExecute(String[] result) {
				callback.handleResponse(result);
			}
		}.execute();

	}

	public static void symmetricBase64Encrypt(final String username, final InputStream data, final IAsyncCallback<byte[][]> callback) {
		new AsyncTask<Void, Void, byte[][]>() {
			@Override
			protected byte[][] doInBackground(Void... params) {
				byte[] iv = new byte[15];
				byte[] buf = new byte[1024]; // input buffer

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				mSecureRandom.nextBytes(iv);
				IvParameterSpec ivParams = new IvParameterSpec(iv);
				InputStream in = data;

				try {
					Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");

					SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(username), 0, AES_KEY_LENGTH,
							"AES");
					ccm.init(Cipher.ENCRYPT_MODE, key, ivParams);
					CipherOutputStream cos = new CipherOutputStream(out, ccm);

					int i = 0;

					while ((i = in.read(buf)) != -1) {
						cos.write(buf, 0, i);
					}

					in.close();
					cos.close();
					out.close();
					byte[][] returns = new byte[2][];

					returns[0] = Utils.base64Encode(iv);
					returns[1] = Utils.base64Encode(out.toByteArray());

					return returns;

				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, "symmetricBase64Encrypt", icle);
				}

				catch (Exception e) {
					SurespotLog.w(TAG, "symmetricBase64Encrypt", e);
				}
				return null;

			}

			@Override
			protected void onPostExecute(byte[][] result) {
				callback.handleResponse(result);
			}
		}.execute();

	}

	public static byte[][] symmetricBase64EncryptSync(final String username, final InputStream data) {

		byte[] iv = new byte[15];
		byte[] buf = new byte[1024]; // input buffer

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		mSecureRandom.nextBytes(iv);
		IvParameterSpec ivParams = new IvParameterSpec(iv);
		InputStream in = data;

		try {
			Cipher ccm = Cipher.getInstance("AES/CCM/NoPadding", "SC");

			SecretKey key = new SecretKeySpec(SurespotApplication.getCachingService().getSharedSecret(username), 0, AES_KEY_LENGTH, "AES");
			ccm.init(Cipher.ENCRYPT_MODE, key, ivParams);
			CipherOutputStream cos = new CipherOutputStream(out, ccm);

			int i = 0;

			while ((i = in.read(buf)) != -1) {
				cos.write(buf, 0, i);
			}

			in.close();
			cos.close();
			out.close();
			byte[][] returns = new byte[2][];

			returns[0] = Utils.base64Encode(iv);
			returns[1] = Utils.base64Encode(out.toByteArray());

			return returns;

		}
		catch (InvalidCacheLoadException icle) {
			// will occur if couldn't load key
			SurespotLog.v(TAG, "symmetricBase64Encrypt", icle);
		}

		catch (Exception e) {
			SurespotLog.w(TAG, "symmetricBase64Encrypt", e);
		}
		return null;

	}

	public static void symmetricDecrypt(final String username, final String ivs, final String cipherData,
			final IAsyncCallback<String> callback) {
		new AsyncTask<Void, Void, String>() {

			@Override
			protected String doInBackground(Void... params) {

				CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

				byte[] cipherBytes = null;
				byte[] iv = null;
				ParametersWithIV ivParams = null;
				try {

					cipherBytes = Utils.base64Decode(cipherData);
					iv = Utils.base64Decode(ivs);
					ivParams = new ParametersWithIV(new KeyParameter(SurespotApplication.getCachingService().getSharedSecret(username), 0,
							AES_KEY_LENGTH), iv);

					ccm.reset();
					ccm.init(false, ivParams);

					byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

					int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);

					len += ccm.doFinal(buf, len);
					return new String(buf);
				}
				catch (InvalidCacheLoadException icle) {
					// will occur if couldn't load key
					SurespotLog.v(TAG, "symmetricBase64DecryptSync", icle);
				}
				catch (Exception e) {
					SurespotLog.w(TAG, "symmetricBase64DecryptSync", e);
				}
				return null;

			}

			@Override
			protected void onPostExecute(String result) {
				callback.handleResponse(result);
			}
		}.execute();
	}

	public static String symmetricDecryptSync(final String username, final String ivs, final String cipherData) {

		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = null;
		byte[] iv = null;
		ParametersWithIV ivParams = null;
		try {

			cipherBytes = Utils.base64Decode(cipherData);
			iv = Utils.base64Decode(ivs);
			byte[] secret = SurespotApplication.getCachingService().getSharedSecret(username);
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
			SurespotLog.w(TAG, "symmetricDecryptSync", e);
		}
		return null;

	}

	public static void symmetricEncrypt(final String username, final String plaintext, final IAsyncCallback<String[]> callback) {
		new AsyncTask<Void, Void, String[]>() {
			@Override
			protected String[] doInBackground(Void... params) {

				CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

				// crashes with getBlockSize() bytes, don't know why?
				byte[] iv = new byte[ccm.getUnderlyingCipher().getBlockSize() - 1];
				mSecureRandom.nextBytes(iv);
				ParametersWithIV ivParams;
				try {
					ivParams = new ParametersWithIV(new KeyParameter(SurespotApplication.getCachingService().getSharedSecret(username), 0,
							AES_KEY_LENGTH), iv);

					ccm.reset();
					ccm.init(true, ivParams);

					byte[] enc = plaintext.getBytes();
					byte[] buf = new byte[ccm.getOutputSize(enc.length)];

					int len = ccm.processBytes(enc, 0, enc.length, buf, 0);

					len += ccm.doFinal(buf, len);
					String[] returns = new String[2];

					returns[0] = new String(Utils.base64Encode(iv));
					returns[1] = new String(Utils.base64Encode(buf));

					return returns;

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

			@Override
			protected void onPostExecute(String[] result) {
				callback.handleResponse(result);
			}
		}.execute();

	}

	/**
	 * Derive key from password.
	 * 
	 * @param password
	 * @param plaintext
	 * @return
	 */
	public static String[] symmetricEncryptSyncPK(final String password, final String plaintext) {

		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		// crashes with getBlockSize() bytes, don't know why?
		byte[] iv = new byte[ccm.getUnderlyingCipher().getBlockSize() - 1];
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
			String[] returns = new String[3];

			returns[0] = new String(Utils.base64Encode(iv));
			returns[1] = new String(Utils.base64Encode(derived[0]));
			returns[2] = new String(Utils.base64Encode(buf));

			return returns;

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
	 * Derive key from password
	 * 
	 * @param password
	 * @param ivs
	 * @param salts
	 * @param cipherData
	 * @return
	 */
	public static String symmetricDecryptSyncPK(final String password, final String ivs, final String salts, final String cipherData) {

		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		byte[] cipherBytes = null;
		byte[] iv = null;
		byte[] salt = null;
		ParametersWithIV ivParams = null;
		try {

			cipherBytes = Utils.base64Decode(cipherData);
			iv = Utils.base64Decode(ivs);
			salt = Utils.base64Decode(salts);
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
			SurespotLog.w(TAG, "symmetricDecryptSync", e);
		}
		return null;

	}

	private static byte[][] derive(String password) {
		int iterationCount = 1000;
		int saltLength = SALT_LENGTH;
		int keyLength = 256;

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
		int keyLength = 256;

		byte[] keyBytes = null;

		KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
		SecretKeyFactory keyFactory;
		try {
			keyFactory = SecretKeyFactory.getInstance("PBEWITHSHA-256AND256BITAES-CBC-BC", "SC");
			keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
		}
		catch (Exception e) {
			SurespotLog.e(TAG, "deriveKey", e);
		}

		return keyBytes;
	}

}
