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
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
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
import android.util.Log;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncCallback;

public class EncryptionController {
	private static String ASYMKEYPAIR_PREFKEY = "asymKeyPair";
	private static final int AES_KEY_LENGTH = 32;
	
	
	private ECParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp521r1");
	private KeyPair keyPair;
	
	
	private PaddedBufferedBlockCipher cipher;
	private SecureRandom secureRandom;
	private int symmetricBlockSize;
	
	private Map<String, ECPublicKey> mPublicKeys;
	private Map<String, byte[]> mSharedSecrets;


	public EncryptionController() {
		// attempt to load key pair
		keyPair = loadKeyPair();
		cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESLightEngine()));		
		secureRandom = new SecureRandom();
		symmetricBlockSize = cipher.getBlockSize();
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

	private ECPublicKey recreatePublicKey(String encodedKey)  {
		ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(curve.getCurve().decodePoint(Hex.decode(encodedKey)),
				curve);
		
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
	
	public KeyPair generateKeyPair() {

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

	private byte[] generateSharedSecret(ECPublicKey publicKey) {
		if (keyPair == null) return null;
		try { 
			KeyAgreement ka = KeyAgreement.getInstance("ECDH", "SC");
			ka.init(keyPair.getPrivate());
			ka.doPhase(publicKey, true);
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
	
	private ParametersWithIV generateAESKey() {
		byte[] aes_key_bytes = new byte[AES_KEY_LENGTH];		
		byte[] iv = new byte[symmetricBlockSize];
		secureRandom.nextBytes(aes_key_bytes);
		secureRandom.nextBytes(iv);
		return new ParametersWithIV(new KeyParameter(aes_key_bytes), iv);
	}
	
	private ParametersWithIV createParametersWithIV(byte[] key, byte[] iv) {

		return new ParametersWithIV(new KeyParameter(key), iv);
	}
	
	private String symmetricDecrypt() {
		return null;
		
	}

	public void eccDecrypt(String from, String ciphertext, IAsyncCallback<String> callback) {
		getSecret(from, new IAsyncCallback<byte[]>() {

			@Override
			public void handleResponse(byte[] result) {
				
				
			}
			
		});
	}
	
	public void getSecret(final String username, final IAsyncCallback<byte[]> callback) {
		byte[] secret = mSharedSecrets.get(username);
		if (secret == null) {		
			SurespotApplication.getNetworkController().getPublicKey(username, new IAsyncCallback<String>() {
				
				@Override
				public void handleResponse(String result) {
					ECPublicKey pubKey = recreatePublicKey(result);				
					mPublicKeys.put(username, pubKey);
					byte[] secret = generateSharedSecret(pubKey);
					mSharedSecrets.put(username, secret);
					callback.handleResponse(secret);
				}
			});
		}
		else {
			callback.handleResponse(secret);
		}
	}
}
