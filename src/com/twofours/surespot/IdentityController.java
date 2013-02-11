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

	public static synchronized void setLoggedInIdentity(Context context, SurespotIdentity identity) {
		// TODO thread
		String identityDir = getIdentityDir(context);
		saveIdentity(identityDir, identity, "surespot");
		setLoggedInUser(context, identity.getUsername());
	}

	private static String saveIdentity(String identityDir, SurespotIdentity identity, String password) {
		String identityFile = identityDir + File.separator + identity.getUsername() + IDENTITY_EXTENSION;

		SurespotLog.v(TAG, "saving identity: " + identityFile);

		ECPublicKey ecpub = (ECPublicKey) identity.getKeyPair().getPublic();
		ECPrivateKey ecpriv = (ECPrivateKey) identity.getKeyPair().getPrivate();

		String generatedPrivDHex = new String(Utils.base64Encode(ecpriv.getD().toByteArray()));

		String publicKey = EncryptionController.encodePublicKey(ecpub);
		// SurespotLog.d(TAG, "saving public key:" + publicKey);
		// SurespotLog.d(TAG, "saving private key d:" + generatedPrivDHex);

		JSONObject json = new JSONObject();
		try {
			json.putOpt("username", identity.getUsername());
			json.putOpt("private_key", generatedPrivDHex);
			json.putOpt("public_key", publicKey);
			if (identity.getCookie() != null) {
				json.putOpt("cookie", encodeCookie(new SerializableCookie(identity.getCookie())));
			}

			if (!FileUtils.ensureDir(identityDir)) {
				SurespotLog.e(TAG, "Could not create identity dir: " + identityDir, new RuntimeException("Could not create identity dir: "
						+ identityDir));
				return null;
			}

			String[] ciphers = EncryptionController.symmetricEncryptSync(password, json.toString());

			JSONObject idWrapper = new JSONObject();
			idWrapper.put("iv", ciphers[0]);
			idWrapper.put("identity", ciphers[1]);
			idWrapper.put("salt", ciphers[2]);
			FileOutputStream fos = new FileOutputStream(identityFile);
			fos.write(idWrapper.toString().getBytes());
			fos.close();

			return identityFile;

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
		return null;
	}

	private static String getIdentityDir(Context context) {
		File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
		return cacheDir.getPath();

	}

	public static List<String> getIdentityNames(Context context, String dir) {

		ArrayList<String> identityNames = new ArrayList<String>();
		File[] files = new File(dir).listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(IDENTITY_EXTENSION);
			}
		});

		if (files != null) {
			for (File f : files) {
				identityNames.add(f.getName().substring(0, f.getName().length() - IDENTITY_EXTENSION.length()));
			}
		}

		return identityNames;

	}

	public static List<String> getIdentityNames(Context context) {
		File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
		return getIdentityNames(context, cacheDir.getPath());
	}

	public static void userLoggedIn(Context context, String username, Cookie cookie) {
		SurespotIdentity identity = getIdentity(context, username);
		identity.setCookie(cookie);
		setLoggedInIdentity(context, identity);
	}

	private synchronized static void setLoggedInUser(Context context, String username) {
		mLoggedInUser = username;
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.CURRENT_USER, username);
	}

	public static synchronized String getLoggedInUser(Context context) {
		if (mLoggedInUser == null) {
			mLoggedInUser = Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.CURRENT_USER);
		}
		return mLoggedInUser;
	}

	public static SurespotIdentity getIdentity(Context context, String username) {
		SurespotIdentity identity = mIdentities.get(username);
		if (identity == null) {

			identity = loadIdentity(context, getIdentityDir(context), username, "surespot");
			mIdentities.put(username, identity);
		}
		return identity;
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

	private synchronized static SurespotIdentity loadIdentity(Context context, String dir, String username, String password) {

		// try to load identity
		String identityFilename = dir + File.separator + username + IDENTITY_EXTENSION;
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

			JSONObject jsonWrapper = new JSONObject(new String(idBytes));
			String ivs = jsonWrapper.getString("iv");
			String cipherId = jsonWrapper.getString("identity");
			String salt = jsonWrapper.getString("salt");

			String identity = EncryptionController.symmetricDecryptSyncPK(password, ivs, salt, cipherId);

			JSONObject json = new JSONObject(identity);
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

			return new SurespotIdentity(name, new KeyPair(EncryptionController.recreatePublicKey(sPublicKey),
					EncryptionController.recreatePrivateKey(sPrivateKey)), cookie);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "loadIdentity", e);
		}

		return null;
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

	public static String exportIdentity(Context context, String user, String password) {
		SurespotIdentity identity = getIdentity(context, user);
		if (identity == null)
			return null;

		File exportDir = FileUtils.getIdentityExportDir();
		if (FileUtils.ensureDir(exportDir.getPath())) {
			return saveIdentity(exportDir.getPath(), identity, password);
		}
		else {
			return null;
		}

	}

	public static void importIdentities(Context context, File exportDir, String password) {
		List<String> idNames = getIdentityNames(context, exportDir.getPath());
		for (String name : idNames) {
			SurespotIdentity identity = loadIdentity(context, exportDir.getPath(), name, password);
			saveIdentity(getIdentityDir(context), identity, password);
		}

	}

}
