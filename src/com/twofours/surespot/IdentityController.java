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

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.jce.interfaces.ECPublicKey;

import android.content.Context;
import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.AsyncHttpResponseHandler;
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
	public static final String CACHE_IDENTITY_ID = "_cache_identity";
	public static final String EXPORT_IDENTITY_ID = "_export_identity";
	private static final Map<String, SurespotIdentity> mIdentities = new HashMap<String, SurespotIdentity>();
	private static boolean mHasIdentity;

	public static synchronized void createIdentity(Context context, String username, String password, KeyPair keyPairDH,
			KeyPair keyPairECDSA, Cookie cookie) {
		// TODO thread
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = new SurespotIdentity(username, keyPairDH, keyPairECDSA);
		saveIdentity(identityDir, identity, password + CACHE_IDENTITY_ID);
		setLoggedInUser(context, username, password, cookie);
	}

	private static String saveIdentity(String identityDir, SurespotIdentity identity, String password) {
		String identityFile = identityDir + File.separator + identity.getUsername() + IDENTITY_EXTENSION;

		SurespotLog.v(TAG, "saving identity: " + identityFile);

		String spubDH = EncryptionController.encodePublicKey(identity.getKeyPairDH().getPublic());
		String sprivDH = new String(Utils.base64Encode(identity.getKeyPairDH().getPrivate().getEncoded()));
		String spubECDSA = EncryptionController.encodePublicKey(identity.getKeyPairECDSA().getPublic());
		String sprivECDSA = new String(Utils.base64Encode(identity.getKeyPairECDSA().getPrivate().getEncoded()));

		SurespotLog.d(TAG, "saving dh public key:" + spubDH);
		SurespotLog.d(TAG, "saving ecdsa public key:" + spubECDSA);

		JSONObject json = new JSONObject();
		try {
			json.putOpt("username", identity.getUsername());
			json.putOpt("dhPriv", sprivDH);
			json.putOpt("dhPub", spubDH);
			json.putOpt("dsaPriv", sprivECDSA);
			json.putOpt("dsaPub", spubECDSA);

			if (!FileUtils.ensureDir(identityDir)) {
				SurespotLog.e(TAG, "Could not create identity dir: " + identityDir, new RuntimeException("Could not create identity dir: "
						+ identityDir));
				return null;
			}

			String[] ciphers = EncryptionController.symmetricEncryptSyncPK(password, json.toString());

			JSONObject idWrapper = new JSONObject();
			idWrapper.put("iv", ciphers[0]);
			idWrapper.put("salt", ciphers[1]);
			idWrapper.put("identity", ciphers[2]);
			// idWrapper.put("time", new Date().getTime());
			// idWrapper.put("username", identity.getUsername());
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

	public static SurespotPublicIdentity recreatePublicIdentity(String jsonIdentity) {

		try {
			JSONObject json = new JSONObject(jsonIdentity);
			String username = json.getString("username");
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
				SurespotLog.i(TAG, username + ": DH key successfully verified");
			}

			boolean dsaVerify = EncryptionController.verifyPublicKey(sSigECDSA, spubECDSA);
			if (!dsaVerify) {
				// alert alert
				SurespotLog.w(TAG, "could not verify DSA key against server signature", new KeyException(
						"Could not verify DSA key against server signature."));
				return null;
			}
			else {
				SurespotLog.i(TAG, username = ": DSA key successfully verified");
			}

			PublicKey dhPub = EncryptionController.recreatePublicKey("ECDH", spubDH);
			PublicKey dsaPub = EncryptionController.recreatePublicKey("ECDSA", spubECDSA);

			return new SurespotPublicIdentity(username, dhPub, dsaPub, sSigDH, sSigECDSA);

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

	public static SurespotIdentity getIdentity(Context context) {
		return getIdentity(context, SurespotApplication.getCachingService().getLoggedInUser());
	}

	public static SurespotIdentity getIdentity(Context context, String username) {
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

			String spubDH = json.getString("dhPub");
			String sprivDH = json.getString("dhPriv");
			String spubECDSA = json.getString("dsaPub");
			String sprivECDSA = json.getString("dsaPriv");

			return new SurespotIdentity(name, new KeyPair(EncryptionController.recreatePublicKey("ECDH", spubDH),
					EncryptionController.recreatePrivateKey("ECDH", sprivDH)), new KeyPair(EncryptionController.recreatePublicKey("ECDSA",
					spubECDSA), EncryptionController.recreatePrivateKey("ECDSA", sprivECDSA)));
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "loadIdentity", e);
		}

		return null;
	}

	public static boolean hasIdentity() {
		if (!mHasIdentity) {
			mHasIdentity = getIdentityNames(SurespotApplication.getContext()).size() > 0;
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

	public static void exportIdentity(Context context, String username, final String password, final IAsyncCallback<String> callback) {
		final SurespotIdentity identity = getIdentity(context, username, password);
		if (identity == null)
			callback.handleResponse(null);

		final File exportDir = FileUtils.getIdentityExportDir();
		if (FileUtils.ensureDir(exportDir.getPath())) {
			SurespotApplication.getNetworkController().validate(username, password, null, new AsyncHttpResponseHandler() {
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
							callback.handleResponse("incorrect password");
							break;
						case 404:
							callback.handleResponse("no such user");
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
			SurespotApplication.getNetworkController().validate(username, password,
					EncryptionController.encodePublicKey((ECPublicKey) identity.getKeyPairDH().getPublic()),
					new AsyncHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, String content) {
							if (content.equals("true")) {
								saveIdentity(FileUtils.getIdentityDir(context), identity, password + CACHE_IDENTITY_ID);
								callback.handleResponse(new IdentityOperationResult(context
										.getText(R.string.identity_imported_successfully).toString(), true));
							}
							else {
								callback.handleResponse(new IdentityOperationResult(context.getText(R.string.could_not_import_identity)
										.toString(), false));
							}
						}

						@Override
						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(new IdentityOperationResult("incorrect password", false));
									break;
								case 404:
									callback.handleResponse(new IdentityOperationResult("no such user", false));
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

	public static void logout(final Context context, final IAsyncCallback<Boolean> callback) {
		SurespotApplication.getNetworkController().logout(new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, String content) {
				SurespotApplication.getCachingService().logout();
				// Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, null);

				callback.handleResponse(true);
			}

			@Override
			public void onFailure(Throwable error, String content) {
				SurespotLog.w(TAG, "logout onFailure", error);
				callback.handleResponse(false);
			}
		});
	}

}
