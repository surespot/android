package com.twofours.surespot.encryption;

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
import java.util.Hashtable;
import java.util.Map;

import javax.crypto.KeyAgreement;

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
import org.spongycastle.util.encoders.Hex;

import android.os.AsyncTask;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;
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

	private static Map<String, ECPublicKey> mPublicKeys;
	private static Map<String, byte[]> mSharedSecrets;

	static {
		Log.v(TAG, "constructor");
		// attempt to load key pair
		mSecureRandom = new SecureRandom();

		mIdentity = loadIdentity();
		mPublicKeys = new Hashtable<String, ECPublicKey>();
		mSharedSecrets = new Hashtable<String, byte[]>();
	}

	public static String getPublicKeyString() {
		if (hasIdentity()) {
			return encodePublicKey((ECPublicKey) mIdentity.getKeyPair().getPublic());
		}
		else {
			return null;
		}
	}

	public static Boolean hasIdentity() {
		return mIdentity != null;
	}

	public static String getIdentityUsername() {
		if (hasIdentity()) {
			return mIdentity.getUsername();

		}
		else {
			return null;
		}
	}

	private static SurespotIdentity loadIdentity() {
		String jsonIdentity = Utils.getSharedPrefsString(IDENTITY_KEY);
		if (jsonIdentity == null) return null;

		// we have a identity stored, load the fucker up and reconstruct the keys

		try {

			JSONObject json = new JSONObject(jsonIdentity);
			String username = (String) json.get("username");
			String sPrivateKey = (String) json.get("private_key");
			String sPublicKey = (String) json.get("public_key");

			SurespotIdentity identity = new SurespotIdentity(username, new KeyPair(recreatePublicKey(sPublicKey),
					recreatePrivateKey(sPrivateKey)));
			return identity;
		}
		catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private static ECPublicKey recreatePublicKey(String encodedKey) {
		ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(curve.getCurve().decodePoint(Hex.decode(encodedKey)), curve);

		KeyFactory fact;
		try {
			fact = KeyFactory.getInstance("ECDH", "SC");
			ECPublicKey pubKey = (ECPublicKey) fact.generatePublic(pubKeySpec);
			return pubKey;
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	private static ECPrivateKey recreatePrivateKey(String encodedKey) {
		// recreate key from hex string
		ECPrivateKeySpec priKeySpec = new ECPrivateKeySpec(new BigInteger(Hex.decode(encodedKey)), curve);

		KeyFactory fact;
		try {
			fact = KeyFactory.getInstance("ECDH", "SC");
			ECPrivateKey privKey = (ECPrivateKey) fact.generatePrivate(priKeySpec);
			return privKey;
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static void generateKeyPair(IAsyncCallback<KeyPair> callback) {
		new AsyncGenerateKeyPair(callback).execute();
	}

	private static class AsyncGenerateKeyPair extends AsyncTask<Void, Void, KeyPair> {
		private IAsyncCallback<KeyPair> mCallback;

		public AsyncGenerateKeyPair(IAsyncCallback<KeyPair> callback) {

			mCallback = callback;

		}

		@Override
		protected KeyPair doInBackground(Void... arg0) {
			// perform async

			try {
				KeyPairGenerator g = KeyPairGenerator.getInstance("ECDH", "SC");
				g.initialize(curve, new SecureRandom());
				KeyPair pair = g.generateKeyPair();
				return pair;

			}
			catch (NoSuchAlgorithmException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			catch (NoSuchProviderException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return null;
		}

		protected void onPostExecute(KeyPair result) {
			mCallback.handleResponse(result);
		}

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
		String generatedPrivDHex = new String(Hex.encode(ecpriv.getD().toByteArray()));

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
		}
		catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static String encodePublicKey(ECPublicKey publicKey) {
		return new String(Hex.encode(publicKey.getQ().getEncoded()));
	}

	private static void generateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
		new AsyncGenerateSharedSecret(username, callback).execute();
	}

	private static class AsyncGenerateSharedSecret extends AsyncTask<Void, Void, byte[]> {
		private IAsyncCallback<byte[]> mCallback;
		private String mUsername;

		public AsyncGenerateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
			mUsername = username;
			mCallback = callback;

		}

		@Override
		protected byte[] doInBackground(Void... arg0) {
			// perform async

			if (mIdentity == null) return null;
			try {
				KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
				ka.init(mIdentity.getKeyPair().getPrivate());
				ka.doPhase(mPublicKeys.get(mUsername), true);
				byte[] sharedSecret = ka.generateSecret();

				Log.d("ke", "shared Key: " + new String(Hex.encode(new BigInteger(sharedSecret).toByteArray())));
				return sharedSecret;

			}
			catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

			}
			catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (NoSuchProviderException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		protected void onPostExecute(byte[] result) {
			mCallback.handleResponse(result);
		}

	}

	private static void symmetricDecrypt(String username, String cipherTextJson, IAsyncCallback<String> callback) {
		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		JSONObject json;
		byte[] cipherBytes = null;
		byte[] iv = null;
		try {
			json = new JSONObject(cipherTextJson);
			cipherBytes = Hex.decode(json.getString("ciphertext"));
			iv = Hex.decode(json.getString("iv").getBytes());
		}
		catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		ParametersWithIV params = new ParametersWithIV(new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);

		ccm.reset();
		ccm.init(false, params);

		byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

		int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);
		try {
			len += ccm.doFinal(buf, len);
			callback.handleResponse(new String(buf));
		}
		catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidCipherTextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void symmetricEncrypt(String username, String plaintext, IAsyncCallback<String> callback) {
		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		// crashes with getBlockSize() bytes, don't know why?
		byte[] iv = new byte[ccm.getUnderlyingCipher().getBlockSize() - 1];
		mSecureRandom.nextBytes(iv);
		ParametersWithIV params = new ParametersWithIV(new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);

		ccm.reset();
		ccm.init(true, params);

		byte[] enc = plaintext.getBytes();
		byte[] buf = new byte[ccm.getOutputSize(enc.length)];

		int len = ccm.processBytes(enc, 0, enc.length, buf, 0);
		try {
			len += ccm.doFinal(buf, len);
			JSONObject json = new JSONObject();
			json.put("iv", new String(Hex.encode(iv)));

			json.put("ciphertext", new String(Hex.encode(buf)));
			callback.handleResponse(json.toString());
		}
		catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}
		catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidCipherTextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void eccEncrypt(final String username, final String plaintext, final IAsyncCallback<String> callback) {
		hydratePublicKey(username, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				symmetricEncrypt(username, plaintext, callback);
			}
		});
	}

	public static void eccDecrypt(final String from, final String ciphertext, final IAsyncCallback<String> callback) {

		hydratePublicKey(from, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				symmetricDecrypt(from, ciphertext, callback);
			}

		});
	}

	public static void hydratePublicKey(final String username, final IAsyncCallback<Void> callback) {
		byte[] secret = mSharedSecrets.get(username);
		if (secret == null) {
			NetworkController.getPublicKey(username, new AsyncHttpResponseHandler() {

				@Override
				public void onSuccess(String result) {

					ECPublicKey pubKey = recreatePublicKey(result);
					mPublicKeys.put(username, pubKey);
					generateSharedSecret(username, new IAsyncCallback<byte[]>() {

						@Override
						public void handleResponse(byte[] result) {
							mSharedSecrets.put(username, result);
							callback.handleResponse(null);
						}

					});

				}

				@Override
				public void onFailure(Throwable error, String content) {
					Log.e(TAG, content);
				}

			});
		}
		else {
			callback.handleResponse(null);
		}
	}
}
