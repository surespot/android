package com.twofours.surespot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;

import android.content.Context;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.SerializableCookie;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class IdentityController {
	private static final String TAG = "IdentityController";
	public static final String IDENTITY_EXTENSION = ".ssi";
	private static final Map<String, SurespotIdentity> mIdentities = new HashMap<String, SurespotIdentity>();
	private static String mLoggedInUser;

	public static void setLoggedInIdentity(Context context, SurespotIdentity identity) {
		// TODO thread

		ECPublicKey ecpub = (ECPublicKey) identity.getKeyPair().getPublic();
		ECPrivateKey ecpriv = (ECPrivateKey) identity.getKeyPair().getPrivate();

		// SurespotLog.d("ke","encoded public key: " +
		// ecpk.getEncoded().toString());
		// pair.getPublic().
		// ecpk.getW().;
		// ecprik.getD().toByteArray();
		String generatedPrivDHex = new String(Utils.base64Encode(ecpriv.getD().toByteArray()));

		String publicKey = EncryptionController.encodePublicKey(ecpub);
		SurespotLog.d("ke", "generated public key:" + publicKey);
		SurespotLog.d("ke", "generated private key d:" + generatedPrivDHex);

		// save keypair in shared prefs json format (hex for now) TODO
		// use something other than hex

		JSONObject json = new JSONObject();
		try {
			json.putOpt("username", identity.getUsername());
			json.putOpt("private_key", generatedPrivDHex);
			json.putOpt("public_key", publicKey);
			if (identity.getCookie() != null) {
				json.putOpt("cookie", encodeCookie(new SerializableCookie(identity.getCookie())));
			}
			String identityDir = getIdentityDir(context);
			if (!FileUtils.ensureDir(identityDir)) {
				SurespotLog.e(TAG, "Could not create identity cache dir.", new RuntimeException("Could not create identity cache dir."));
				return;
			}

			String identityFile = identityDir + File.separator + identity.getUsername() + IDENTITY_EXTENSION;

			SurespotLog.v(TAG, "saving identity: " + identityFile);

			FileOutputStream fos = new FileOutputStream(identityFile);
			fos.write(json.toString().getBytes());
			fos.close();

			setLoggedInUser(context, identity.getUsername());
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (FileNotFoundException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}

	}

	private static String getIdentityDir(Context context) {
		File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
		return cacheDir.getPath();

	}

	public static List<String> getIdentityNames(Context context) {
		File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
		ArrayList<String> identityNames = new ArrayList<String>();
		for (File f : cacheDir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(IDENTITY_EXTENSION);
			}
		})) {
			identityNames.add(f.getName().substring(0, f.getName().length() - IDENTITY_EXTENSION.length()));
		}

		return identityNames;

	}

	public static void userLoggedIn(Context context, String username, Cookie cookie) {
		SurespotIdentity identity = getIdentity(context, username);
		identity.setCookie(cookie);
		setLoggedInIdentity(context, identity);
	}

	private static void setLoggedInUser(Context context, String username) {
		mLoggedInUser = username;
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.CURRENT_USER, username);
	}

	public static String getLoggedInUser(Context context) {
		if (mLoggedInUser == null) {
			mLoggedInUser = Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.CURRENT_USER);
		}
		return mLoggedInUser;
	}

	public static SurespotIdentity getIdentity(Context context, String username) {
		return loadIdentity(context, username);
	}

	protected static String encodeCookie(SerializableCookie cookie) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			ObjectOutputStream outputStream = new ObjectOutputStream(os);
			outputStream.writeObject(cookie);
		}
		catch (Exception e) {
			return null;
		}

		return new String(Utils.base64Encode(os.toByteArray()));
	}

	protected static Cookie decodeCookie(String cookieStr) {
		byte[] bytes = Utils.base64Decode(cookieStr);
		ByteArrayInputStream is = new ByteArrayInputStream(bytes);
		Cookie cookie = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(is);
			cookie = ((SerializableCookie) ois.readObject()).getCookie();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return cookie;
	}

	private synchronized static SurespotIdentity loadIdentity(Context context, String username) {
		SurespotIdentity identity = mIdentities.get(username);
		if (identity == null) {

			// try to load identity
			String identityFilename = getIdentityDir(context) + File.separator + username + IDENTITY_EXTENSION;
			File idFile = new File(identityFilename);

			if (!idFile.canRead()) {
				SurespotLog.e(TAG, "Could not load identity.", new IOException("Could not load identity file: " + identityFilename));
				return null;
			}

			try {
				FileInputStream idStream = new FileInputStream(idFile);
				byte[] idBytes = new byte[(int) idFile.length()];
				idStream.read(idBytes);
				idStream.close();

				JSONObject json = new JSONObject(new String(idBytes));
				String name = (String) json.get("username");

				if (!name.equals(username)) {
					SurespotLog.e(TAG, "internal identity did not match", new RuntimeException("internal identity: " + name
							+ " did not match: " + username));
					return null;
				}

				String sPrivateKey = (String) json.get("private_key");
				String sPublicKey = (String) json.get("public_key");
				String sCookie = json.optString("cookie");
				Cookie cookie = (sCookie == null || sCookie.isEmpty() ? null : decodeCookie(sCookie));

				identity = new SurespotIdentity(name, new KeyPair(EncryptionController.recreatePublicKey(sPublicKey),
						EncryptionController.recreatePrivateKey(sPrivateKey)), cookie);

				mIdentities.put(username, identity);
				// IdentityController.saveIdentity(SurespotConfiguration.getContext(), identity);

			}
			catch (Exception e) {
				SurespotLog.w(TAG, "loadIdentity", e);
			}
		}
		return identity;
	}

	public static boolean hasIdentity(Context context) {
		return getIdentityNames(context).size() > 0;
	}

	public static boolean hasLoggedInUser(Context context) {
		return getLoggedInUser(context) != null;

	}

	public static Cookie getCookie(Context context) {
		Cookie cookie = null;
		String user = getLoggedInUser(context);
		if (user != null) {

			SurespotIdentity identity = getIdentity(context, user);
			if (identity != null) {
				cookie = identity.getCookie();
			}
		}
		return cookie;

	}

	public static SurespotIdentity getIdentity(Context context) {
		return getIdentity(context, getLoggedInUser(context));
	}
}
