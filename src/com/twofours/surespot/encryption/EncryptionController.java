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

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncCallback;

public class EncryptionController {
	private static String ASYMKEYPAIR_PREFKEY = "asymKeyPair";
	private static final int AES_KEY_LENGTH = 32;

	private ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private KeyPair keyPair;
	private SecureRandom mSecureRandom;

	private Map<String, ECPublicKey> mPublicKeys;
	private Map<String, byte[]> mSharedSecrets;

	public EncryptionController() {
		// attempt to load key pair
		mSecureRandom = new SecureRandom();

		keyPair = loadKeyPair();
		mPublicKeys = new Hashtable<String, ECPublicKey>();
		mSharedSecrets = new Hashtable<String, byte[]>();
	}

	public String getPublicKeyString() {
		return encodePublicKey((ECPublicKey) keyPair.getPublic());
	}

	public Boolean hasKeyPair() {
		return keyPair != null;
	}

	private KeyPair loadKeyPair() {
		SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences("encryption",
				android.content.Context.MODE_PRIVATE);
		String asymKeyPair = settings.getString(ASYMKEYPAIR_PREFKEY, null);
		if (asymKeyPair == null)
			return null;

		// we have a keypair stored, load the fuckers up and reconstruct the keys

		try {

			JSONObject json = new JSONObject(asymKeyPair);
			String sPrivateKey = (String) json.get("private_key");
			String sPublicKey = (String) json.get("public_key");
			return new KeyPair(recreatePublicKey(sPublicKey), recreatePrivateKey(sPrivateKey));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private ECPublicKey recreatePublicKey(String encodedKey) {
		ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(curve.getCurve().decodePoint(Hex.decode(encodedKey)), curve);

		KeyFactory fact;
		try {
			fact = KeyFactory.getInstance("ECDH", "SC");
			ECPublicKey pubKey = (ECPublicKey) fact.generatePublic(pubKeySpec);
			return pubKey;
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

	private ECPrivateKey recreatePrivateKey(String encodedKey) {
		// recreate key from hex string
		ECPrivateKeySpec priKeySpec = new ECPrivateKeySpec(new BigInteger(Hex.decode(encodedKey)), curve);

		KeyFactory fact;
		try {
			fact = KeyFactory.getInstance("ECDH", "SC");
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

	public void generateKeyPair(IAsyncCallback<KeyPair> callback) {
			new AsyncGenerateKeyPair(callback).execute();
	}
	
	private class AsyncGenerateKeyPair extends AsyncTask<Void, Void, KeyPair> {
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
			mCallback.handleResponse(result);
		}

	}

	public void saveKeyPair(KeyPair pair) {

		keyPair = pair;
		ECPublicKey ecpub = (ECPublicKey) pair.getPublic();
		ECPrivateKey ecpriv = (ECPrivateKey) pair.getPrivate();

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
			json.putOpt("private_key", generatedPrivDHex);
			json.putOpt("public_key", publicKey);
			SharedPreferences settings = SurespotApplication.getAppContext().getSharedPreferences("encryption",
					android.content.Context.MODE_PRIVATE);
			settings.edit().putString(ASYMKEYPAIR_PREFKEY, json.toString()).commit();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static String encodePublicKey(ECPublicKey publicKey) {
		return new String(Hex.encode(publicKey.getQ().getEncoded()));
	}

	private void generateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
		new AsyncGenerateSharedSecret(username, callback).execute();
	}

	private class AsyncGenerateSharedSecret extends AsyncTask<Void, Void, byte[]> {
		private IAsyncCallback<byte[]> mCallback;
		private String mUsername;

		public AsyncGenerateSharedSecret(String username, IAsyncCallback<byte[]> callback) {
			mUsername = username;
			mCallback = callback;

		}

		@Override
		protected byte[] doInBackground(Void... arg0) {
			// perform async

			if (keyPair == null)
				return null;
			try {
				KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
				ka.init(keyPair.getPrivate());
				ka.doPhase(mPublicKeys.get(mUsername), true);
				byte[] sharedSecret = ka.generateSecret();

				Log.d("ke", "shared Key: " + new String(Hex.encode(new BigInteger(sharedSecret).toByteArray())));
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
			}
			return null;
		}

		protected void onPostExecute(byte[] result) {
			mCallback.handleResponse(result);
		}

	}

	private void symmetricDecrypt(String username, String cipherTextJson, IAsyncCallback<String> callback) {
		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		JSONObject json;
		byte[] cipherBytes = null;
		byte[] iv = null;
		try {
			json = new JSONObject(cipherTextJson);
			cipherBytes = Hex.decode(json.getString("ciphertext"));
			iv = Hex.decode(json.getString("iv").getBytes());
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		ParametersWithIV params = new ParametersWithIV(
				new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);

		ccm.reset();
		ccm.init(false, params);

		byte[] buf = new byte[ccm.getOutputSize(cipherBytes.length)];

		int len = ccm.processBytes(cipherBytes, 0, cipherBytes.length, buf, 0);
		try {
			len += ccm.doFinal(buf, len);
			callback.handleResponse(new String(buf));
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidCipherTextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void symmetricEncrypt(String username, String plaintext, IAsyncCallback<String> callback) {
		CCMBlockCipher ccm = new CCMBlockCipher(new AESLightEngine());

		//crashes with getBlockSize() bytes, don't know why?
		byte[] iv = new byte[ccm.getUnderlyingCipher().getBlockSize()-1];
		mSecureRandom.nextBytes(iv);
		ParametersWithIV params = new ParametersWithIV(
				new KeyParameter(mSharedSecrets.get(username), 0, AES_KEY_LENGTH), iv);

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
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidCipherTextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void eccEncrypt(final String username, final String plaintext, final IAsyncCallback<String> callback) {
		hydratePublicKey(username, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				symmetricEncrypt(username, plaintext, callback);
			}
		});
	}

	public void eccDecrypt(final String from, final String ciphertext, final IAsyncCallback<String> callback) {

		hydratePublicKey(from, new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				symmetricDecrypt(from, ciphertext, callback);
			}

		});
	}

	public void hydratePublicKey(final String username, final IAsyncCallback<Void> callback) {
		byte[] secret = mSharedSecrets.get(username);
		if (secret == null) {
			SurespotApplication.getNetworkController().getPublicKey(username, new IAsyncCallback<String>() {

				@Override
				public void handleResponse(String result) {
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
			});
		} else {
			callback.handleResponse(null);
		}
	}
}
