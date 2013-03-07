package com.twofours.surespot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.services.CredentialCachingService;

public class IdentityController {
	private static final String TAG = "IdentityController";
	public static final String IDENTITY_EXTENSION = ".ssi";
	public static final String PUBLICKEYPAIR_EXTENSION = ".spk";
	public static final String IDENTITY_BACKUP_EXTENSION = ".sib";
	public static final String CACHE_IDENTITY_ID = "_cache_identity";
	public static final String EXPORT_IDENTITY_ID = "_export_identity";
	private static final Map<String, SurespotIdentity> mIdentities = new HashMap<String, SurespotIdentity>();
	private static boolean mHasIdentity;

	public static synchronized void createIdentity(final Context context, final String username, final String password,
			final KeyPair keyPairDH, final KeyPair keyPairECDSA, final Cookie cookie) {
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = new SurespotIdentity(username);
		identity.addKeyPairs("1", keyPairDH, keyPairECDSA);

		saveIdentity(identityDir, identity, password + CACHE_IDENTITY_ID);
		setLoggedInUser(context, username, password, cookie);

		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_CHAT, null);
	}

	private static synchronized String saveIdentity(String identityDir, SurespotIdentity identity, String password) {
		byte[] identityBytes = createIdentityBytes(identity, password);
		return saveIdentity(identityDir, identity.getUsername() + IDENTITY_EXTENSION, identityBytes);
	}

	private static synchronized String saveIdentity(String identityDir, String filename, byte[] identityBytes) {
		try {
			String identityFile = identityDir + File.separator + filename;
			SurespotLog.v(TAG, "saving identity: " + identityFile);

			if (!FileUtils.ensureDir(identityDir)) {
				SurespotLog.e(TAG, "Could not create identity dir: " + identityDir, new RuntimeException("Could not create identity dir: "
						+ identityDir));
				return null;
			}

			FileOutputStream fos = new FileOutputStream(identityFile);
			fos.write(identityBytes);
			fos.close();

			return identityFile;
		}
		catch (FileNotFoundException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		return null;
	}

	private static synchronized String savePublicKeyPair(String username, String version, String keyPair) {
		try {
			String dir = FileUtils.getPublicKeyDir(MainActivity.getContext());
			if (!FileUtils.ensureDir(dir)) {
				SurespotLog.e(TAG, "Could not create public key pair dir: " + dir, new RuntimeException(
						"Could not create public key pair dir: " + dir));
				return null;
			}

			String pkFile = dir + File.separator + username + "_" + version + PUBLICKEYPAIR_EXTENSION;
			SurespotLog.v(TAG, "saving public key pair: " + pkFile);

			FileOutputStream fos = new FileOutputStream(pkFile);
			fos.write(keyPair.getBytes());
			fos.close();

			return pkFile;
		}
		catch (FileNotFoundException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		return null;
	}

	private static byte[] createIdentityBytes(SurespotIdentity identity, String password) {
		JSONObject json = new JSONObject();
		try {
			json.put("username", identity.getUsername());

			JSONArray keys = new JSONArray();

			for (PrivateKeyPairs keyPair : identity.getKeyPairs()) {
				JSONObject jsonKeyPair = new JSONObject();

				jsonKeyPair.put("version", keyPair.getVersion());
				jsonKeyPair.put("dhPriv", new String(Utils.base64Encode(keyPair.getKeyPairDH().getPrivate().getEncoded())));
				jsonKeyPair.put("dhPub", EncryptionController.encodePublicKey(keyPair.getKeyPairDH().getPublic()));
				jsonKeyPair.put("dsaPriv", new String(Utils.base64Encode(keyPair.getKeyPairDSA().getPrivate().getEncoded())));
				jsonKeyPair.put("dsaPub", EncryptionController.encodePublicKey(keyPair.getKeyPairDSA().getPublic()));

				keys.put(jsonKeyPair);
			}

			json.put("keys", keys);

			byte[] identityBytes = EncryptionController.symmetricEncryptSyncPK(password, json.toString());
			return identityBytes;

		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}

		return null;
	}

	public static PublicKeys getPublicKeyPair(String username, String version) {

		PublicKeys keys = loadPublicKeyPair(username, version);
		if (keys != null) {
			SurespotLog.v(TAG, "loaded public keys from disk");
			return keys;
		}

		String result = MainActivity.getNetworkController().getPublicKeysSync(username, version);
		if (result != null) {

			JSONObject json = verifyPublicKeyPair(result);
			if (json == null) {
				return null;
			}

			try {
				String readVersion = json.getString("version");
				if (!readVersion.equals(version)) {
					return null;
				}
				String spubDH = json.getString("dhPub");
				String spubECDSA = json.getString("dsaPub");

				PublicKey dhPub = EncryptionController.recreatePublicKey("ECDH", spubDH);
				PublicKey dsaPub = EncryptionController.recreatePublicKey("ECDSA", spubECDSA);

				savePublicKeyPair(username, version, json.toString());
				SurespotLog.v(TAG, "loaded public keys from server");
				return new PublicKeys(version, dhPub, dsaPub);
			}
			catch (JSONException e) {
				SurespotLog.w(TAG, "recreatePublicKeyPair", e);
			}
		}
		return null;
	}

	private static JSONObject verifyPublicKeyPair(String jsonKeypair) {
		try {
			JSONObject json = new JSONObject(jsonKeypair);
			// String version = json.getString("version");
			String spubDH = json.getString("dhPub");
			String sSigDH = json.getString("dhPubSig");

			String spubECDSA = json.getString("dsaPub");
			String sSigECDSA = json.getString("dsaPubSig");

			// verify sig against the server pk
			boolean dhVerify = EncryptionController.verifyPublicKey(sSigDH, spubDH);
			if (!dhVerify) {
				// TODO inform user
				// alert alert
				SurespotLog.w(TAG, "could not verify DH key against server signature", new KeyException(
						"Could not verify DH key against server signature."));
				return null;
			}
			else {
				SurespotLog.i(TAG, "DH key successfully verified");
			}

			boolean dsaVerify = EncryptionController.verifyPublicKey(sSigECDSA, spubECDSA);
			if (!dsaVerify) {
				// alert alert
				SurespotLog.w(TAG, "could not verify DSA key against server signature", new KeyException(
						"Could not verify DSA key against server signature."));
				return null;
			}
			else {
				SurespotLog.i(TAG, "DSA key successfully verified");
			}

			return json;
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "recreatePublicIdentity", e);
		}
		return null;
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
		return getIdentityNames(context, FileUtils.getIdentityDir(context));
	}

	public static void userLoggedIn(Context context, String username, String password, Cookie cookie) {
		setLoggedInUser(context, username, password, cookie);

	}

	private synchronized static void setLoggedInUser(Context context, String username, String password, Cookie cookie) {
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, username);
		SurespotApplication.getCachingService().login(username, password, cookie);
	}

	public static void logout() {
//		ChatController chatController = MainActivity.getChatController();
//		if (chatController != null) {
//			chatController.logout();
//		}
		SurespotApplication.getCachingService().logout();
		MainActivity.getNetworkController().logout();
	}

	public static SurespotIdentity getIdentity(String username) {
		return getIdentity(MainActivity.getContext(), username);
	}

	public static SurespotIdentity getIdentity(Context context) {
		return getIdentity(context, SurespotApplication.getCachingService().getLoggedInUser());
	}

	private static SurespotIdentity getIdentity(Context context, String username) {
		return getIdentity(context, username, null);
	}

	public static SurespotIdentity getIdentity(Context context, String username, String password) {
		SurespotIdentity identity = mIdentities.get(username);
		if (identity == null) {
			// get the password from the caching service
			if (password == null) {
				password = SurespotApplication.getCachingService().getPassword(username);
			}

			if (password != null) {
				identity = loadIdentity(context, FileUtils.getIdentityDir(context), username, password + CACHE_IDENTITY_ID);
				mIdentities.put(username, identity);
			}

		}
		return identity;
	}

	private synchronized static PublicKeys loadPublicKeyPair(String username, String version) {

		// try to load identity
		String pkFilename = FileUtils.getPublicKeyDir(MainActivity.getContext()) + File.separator + username + "_" + version
				+ PUBLICKEYPAIR_EXTENSION;
		File pkFile = new File(pkFilename);

		if (!pkFile.canRead()) {
			SurespotLog.v(TAG, "Could not load public key pair file: " + pkFilename);
			return null;
		}

		try {
			FileInputStream pkStream = new FileInputStream(pkFile);
			byte[] pkBytes = new byte[(int) pkFile.length()];
			pkStream.read(pkBytes);
			pkStream.close();

			JSONObject pkpJSON = new JSONObject(new String(pkBytes));

			return new PublicKeys(pkpJSON.getString("version"), EncryptionController.recreatePublicKey("ECDH", pkpJSON.getString("dhPub")),
					EncryptionController.recreatePublicKey("ECDSA", pkpJSON.getString("dsaPub")));

		}
		catch (Exception e) {
			SurespotLog.w(TAG, "loadPublicKeyPair", e);
		}
		return null;

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

			String identity = EncryptionController.symmetricDecryptSyncPK(password, idBytes);

			if (identity == null) {
				SurespotLog.w(TAG, "could not decrypt identity: " + username);
				return null;
			}
			JSONObject jsonIdentity = new JSONObject(identity);
			String name = (String) jsonIdentity.get("username");

			if (!name.equals(username)) {
				SurespotLog.e(TAG, "internal identity did not match", new RuntimeException("internal identity: " + name
						+ " did not match: " + username));
				return null;
			}

			SurespotIdentity si = new SurespotIdentity(username);

			JSONArray keys = jsonIdentity.getJSONArray("keys");
			for (int i = 0; i < keys.length(); i++) {
				JSONObject json = keys.getJSONObject(i);
				String version = json.getString("version");
				String spubDH = json.getString("dhPub");
				String sprivDH = json.getString("dhPriv");
				String spubECDSA = json.getString("dsaPub");
				String sprivECDSA = json.getString("dsaPriv");
				si.addKeyPairs(
						version,
						new KeyPair(EncryptionController.recreatePublicKey("ECDH", spubDH), EncryptionController.recreatePrivateKey("ECDH",
								sprivDH)),
						new KeyPair(EncryptionController.recreatePublicKey("ECDSA", spubECDSA), EncryptionController.recreatePrivateKey(
								"ECDSA", sprivECDSA)));

			}

			return si;
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "loadIdentity", e);
		}

		return null;
	}

	public static boolean hasIdentity() {
		if (!mHasIdentity) {
			mHasIdentity = getIdentityNames(MainActivity.getContext()).size() > 0;
		}
		return mHasIdentity;
	}

	public static boolean hasLoggedInUser() {
		return getLoggedInUser() != null;

	}

	public static String getLoggedInUser() {
		CredentialCachingService service = SurespotApplication.getCachingService();
		if (service != null) {
			return service.getLoggedInUser();

		}
		else {
			return null;
		}
	}

	public static Cookie getCookie() {
		Cookie cookie = null;
		CredentialCachingService service = SurespotApplication.getCachingService();
		if (service != null) {
			String user = service.getLoggedInUser();
			if (user != null) {
				cookie = SurespotApplication.getCachingService().getCookie(user);
			}
		}
		return cookie;

	}

	public static void exportIdentity(final Context context, String username, final String password, final IAsyncCallback<String> callback) {
		final SurespotIdentity identity = getIdentity(context, username, password);
		if (identity == null)
			callback.handleResponse(null);

		final File exportDir = FileUtils.getIdentityExportDir();
		if (FileUtils.ensureDir(exportDir.getPath())) {

			// do OOB verification
			MainActivity.getNetworkController().validate(username, password,
					EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, password), new AsyncHttpResponseHandler() {
						public void onSuccess(int statusCode, String content) {
							String path = saveIdentity(exportDir.getPath(), identity, password + EXPORT_IDENTITY_ID);
							callback.handleResponse(path == null ? null : "identity exported to " + path);
						}

						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
									break;
								case 404:
									callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
									break;

								default:
									SurespotLog.w(TAG, "exportIdentity", error);
									callback.handleResponse(null);
								}
							}
							else {
								callback.handleResponse(null);
							}
						}
					});
		}
		else {
			callback.handleResponse(null);
		}

	}

	public static void importIdentity(final Context context, File exportDir, String username, final String password,
			final IAsyncCallback<IdentityOperationResult> callback) {
		final SurespotIdentity identity = loadIdentity(context, exportDir.getPath(), username, password + EXPORT_IDENTITY_ID);
		if (identity != null) {
			MainActivity.getNetworkController().validate(username, password,
					EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, password), new AsyncHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, String content) {
							saveIdentity(FileUtils.getIdentityDir(context), identity, password + CACHE_IDENTITY_ID);
							callback.handleResponse(new IdentityOperationResult(context.getText(R.string.identity_imported_successfully)
									.toString(), true));
						}

						@Override
						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(new IdentityOperationResult(context
											.getString(R.string.incorrect_password_or_key), false));
									break;
								case 404:
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.no_such_user), false));
									break;

								default:
									SurespotLog.w(TAG, "importIdentity", error);
									callback.handleResponse(new IdentityOperationResult(context.getText(R.string.could_not_import_identity)
											.toString(), false));
								}
							}
							else {
								callback.handleResponse(new IdentityOperationResult(context.getText(R.string.could_not_import_identity)
										.toString(), false));
							}
						}
					});

		}
		else {
			callback.handleResponse(new IdentityOperationResult(context.getText(R.string.could_not_import_identity).toString(), false));
		}

	}

	/**
	 * run this on a thread
	 * 
	 * @param username
	 * @return
	 */
	public static String getTheirLatestVersion(String username) {
		return SurespotApplication.getCachingService().getLatestVersion(username);
	}

	public static String getOurLatestVersion() {
		return getIdentity(MainActivity.getContext()).getLatestVersion();
	}

	public static String getOurLatestVersion(String username) {
		return getIdentity(MainActivity.getContext(), username).getLatestVersion();
	}

	public static void rollKeys(Context context, String username, String password, String keyVersion, KeyPair keyPairDH, KeyPair keyPairsDSA) {
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = getIdentity(context, username, password);
		identity.addKeyPairs(keyVersion, keyPairDH, keyPairsDSA);

		byte[] identityBytes = createIdentityBytes(identity, password + CACHE_IDENTITY_ID);

		String idFile = saveIdentity(identityDir, identity.getUsername() + IDENTITY_EXTENSION, identityBytes);
		String idFileBackup = saveIdentity(identityDir, identity.getUsername() + IDENTITY_BACKUP_EXTENSION, identityBytes);
		// big problems if we can't save it
		if (idFile == null) {

			if (idFile == null) {
				// TODO give user other options to save it
				SurespotLog.e(TAG, "could not save identity after rolling keys",
						new Exception("could not save identity after rolling keys"));
			}
		}

	}

	public static void updateLatestVersion(Context context, String username, String version) {
		// see if we are the user that's been revoked
		// if we have the latest version locally, if we don't then this user has been revoked from a different device
		// and should not be used on this device anymore
		if (username.equals(getLoggedInUser()) && version.compareTo(getOurLatestVersion()) > 0) {
			SurespotLog.v(TAG, "user revoked, deleting data and logging out");

			// bad news
			// first log them out
			SurespotApplication.getCachingService().logout();

			// clear the data
			StateController.wipeState(context, username);

			// delete identities locally?
			MainActivity.getNetworkController().setUnauthorized(true);

			// boot them out
			Intent intent = new Intent(context, LoginActivity.class);
			intent.putExtra("401", true);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			context.startActivity(intent);

			// TODO notify user?
		}
		else {
			SurespotApplication.getCachingService().updateLatestVersion(username, version);
		}

	}
}
