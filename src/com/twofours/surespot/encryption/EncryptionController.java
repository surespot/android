package com.twofours.surespot.encryption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutionException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.crypto.InvalidCipherTextException;
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
import android.util.Log;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.twofours.surespot.SurespotIdentity;
import com.twofours.surespot.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class EncryptionController {
	private static final String TAG = "EncryptionController";
	private static final String IDENTITY_KEY = "surespot_identity";
	private static final int AES_KEY_LENGTH = 32;

	private static ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private static SurespotIdentity mIdentity;
	private static SecureRandom mSecureRandom;

	private static LoadingCache<String, ECPublicKey> mPublicKeys;
	private static LoadingCache<String, byte[]> mSharedSecrets;

	static {
		Log.v(TAG, "constructor");
		// attempt to load key pair
		mSecureRandom = new SecureRandom();

		mIdentity = loadIdentity();
		CacheLoader<String, byte[]> secretCacheLoader = new CacheLoader<String, byte[]>() {

			@Override
			public byte[] load(String username) throws Exception {

				return generateSharedSecretSync(username);
			}
		};

		CacheLoader<String, ECPublicKey> keyCacheLoader = new CacheLoader<String, ECPublicKey>() {

			@Override
			public ECPublicKey load(String username) throws Exception {
				String result = NetworkController.getPublicKeySync(username);
				if (result != null) {
					return recreatePublicKey(result);
				}
				return null;
			}
		};

		mPublicKeys = CacheBuilder.newBuilder().build(keyCacheLoader);
		mSharedSecrets = CacheBuilder.newBuilder().build(secretCacheLoader);
	}

	public static String getPublicKeyString() {
		if (hasIdentity()) {
			return encodePublicKey((ECPublicKey) mIdentity.getKeyPair().getPublic());
		} else {
			return null;
		}
	}

	public static Boolean hasIdentity() {
		return mIdentity != null;
	}

	public static String getIdentityUsername() {
		if (hasIdentity()) {
			return mIdentity.getUsername();

		} else {
			return null;
		}
	}

	private static SurespotIdentity loadIdentity() {
		String jsonIdentity = Utils.getSharedPrefsString(IDENTITY_KEY);
		if (jsonIdentity == null)
			return null;

		// we have a identity stored, load the fucker up and reconstruct the keys

		try {
			JSONObject json = new JSONObject(jsonIdentity);
			String username = (String) json.get("username");
			String sPrivateKey = (String) json.get("private_key");
			String sPublicKey = (String) json.get("public_key");

			SurespotIdentity identity = new SurespotIdentity(username, new KeyPair(recreatePublicKey(sPublicKey),
					recreatePrivateKey(sPrivateKey)));
			return identity;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private static ECPublicKey recreatePublicKey(String encodedKey) {

		try {
			ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(curve.getCurve().decodePoint(Utils.base64Decode(encodedKey)), curve);
			KeyFactory fact = KeyFactory.getInstance("ECDH", "SC");
			ECPublicKey pubKey = (ECPublicKey) fact.generatePublic(pubKeySpec);
			return pubKey;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	private static ECPrivateKey recreatePrivateKey(String encodedKey) {
		// recreate key from hex string
		ECPrivateKeySpec priKeySpec = new ECPrivateKeySpec(new BigInteger(Utils.base64Decode(encodedKey)), curve);

		try {
			KeyFactory fact = KeyFactory.getInstance("ECDH", "SC");
			ECPrivateKey privKey = (ECPrivateKey) fact.generatePrivate(priKeySpec);
			return privKey;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

				} catch (NoSuchAlgorithmException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				} catch (NoSuchProviderException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				} catch (InvalidAlgorithmParameterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return null;
			}

			protected void onPostExecute(KeyPair result) {
				callback.handleResponse(result);
			}
		}.execute();
	}

	public static synchronized void saveIdentity(SurespotIdentity identity) {

		mIdentity = identity;
		ECPublicKey ecpub = (ECPublicKey) identity.getKeyPair().getPublic();
		ECPrivateKey ecpriv = (ECPrivateKey) identity.getKeyPair().getPrivate();

		// Log.d("ke","encoded public key: " +
		// ecpk.getEncoded().toString());
		// pair.getPublic().
		// ecpk.getW().;
		// ecprik.getD().toByteArray();
		String generatedPrivDHex = new String(Utils.base64Encode(ecpriv.getD().toByteArray()));

		String publicKey = encodePublicKey(ecpub);
		Log.d("ke", "generated public key:" + publicKey);
		Log.d("ke", "generated private key d:" + generatedPrivDHex);

		// save keypair in shared prefs json format (hex for now) TODO
		// use something other than hex

		JSONObject json = new JSONObject();
		try {
			json.putOpt("username", identity.getUsername());
			json.putOpt("private_key", generatedPrivDHex);
			json.putOpt("public_key", publicKey);
			Utils.putSharedPrefsString(IDENTITY_KEY, json.toString());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static String encodePublicKey(ECPublicKey publicKey) {
		return new String(Utils.base64Encode(publicKey.getQ().getEncoded()));
	}

	//
	// private static void generateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
	// new AsyncGenerateSharedSecret(username, callback).execute();
	// }

	private static byte[] generateSharedSecretSync(String username) {
		if (mIdentity == null)
			return null;
		try {
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
			ka.init(mIdentity.getKeyPair().getPrivate());
			ka.doPhase(mPublicKeys.get(username), true);
			byte[] sharedSecret = ka.generateSecret();

			Log.d("ke", "shared Key: " + new String(Utils.base64Encode(new BigInteger(sharedSecret).toByteArray())));
			return sharedSecret;

		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
					SecretKey key = new SecretKeySpec(mSharedSecrets.get(username), 0, AES_KEY_LENGTH, "AES");
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
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidAlgorithmParameterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchPaddingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}

			@Override
			protected void onPostExecute(byte[] result) {
				callback.handleResponse(result);
			}
		}.execute();
	}

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

					SecretKey key = new SecretKeySpec(mSharedSecrets.get(username), 0, AES_KEY_LENGTH, "AES");
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
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidAlgorithmParameterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchPaddingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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

					SecretKey key = new SecretKeySpec(mSharedSecrets.get(username), 0, AES_KEY_LENGTH, "AES");
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
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidAlgorithmParameterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchPaddingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;

			}

			@Override
			protected void onPostExecute(byte[][] result) {
				callback.handleResponse(result);
			}
		}.execute();

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
					ivParams = new ParametersWithIV(new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);

				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				} catch (InvalidCacheLoadException icle) {
					icle.printStackTrace();
					return null;
				}

				ccm.reset();
				ccm.init(false, ivParams);

				byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

				int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);
				try {
					len += ccm.doFinal(buf, len);
					return new String(buf);
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidCipherTextException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;

			}

			@Override
			protected void onPostExecute(String result) {
				callback.handleResponse(result);
			}
		}.execute();
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
					ivParams = new ParametersWithIV(new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);
				} catch (ExecutionException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return null;
				}

				ccm.reset();
				ccm.init(true, ivParams);

				byte[] enc = plaintext.getBytes();
				byte[] buf = new byte[ccm.getOutputSize(enc.length)];

				int len = ccm.processBytes(enc, 0, enc.length, buf, 0);
				try {
					len += ccm.doFinal(buf, len);
					String[] returns = new String[2];

					returns[0] = new String(Utils.base64Encode(iv));
					returns[1] = new String(Utils.base64Encode(buf));

					return returns;

				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidCipherTextException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;

			}

			@Override
			protected void onPostExecute(String[] result) {
				callback.handleResponse(result);
			}
		}.execute();

	}
	//
	// public static void eccEncrypt(final String username, final String plaintext, final IAsyncCallback<String> callback) {
	// symmetricEncrypt(username, plaintext, callback);
	// }
	//
	// public static void eccDecrypt(final String from, final String ciphertext, final IAsyncCallback<String> callback) {
	// symmetricDecrypt(from, ciphertext, callback);
	// }
}
