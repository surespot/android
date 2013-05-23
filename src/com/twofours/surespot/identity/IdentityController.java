package com.twofours.surespot.identity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.ui.UIUtils;

public class IdentityController {
	private static final String TAG = "IdentityController";
	public static final String IDENTITY_EXTENSION = ".ssi";
	public static final String IDENTITY_DELETED_EXTENSION = ".dsi";
	public static final String PUBLICKEYPAIR_EXTENSION = ".spk";
	public static final String CACHE_IDENTITY_ID = "_cache_identity";
	public static final String EXPORT_IDENTITY_ID = "_export_identity";
	public static final Object IDENTITY_FILE_LOCK = new Object();
	private static boolean mHasIdentity;

	private synchronized static void setLoggedInUser(final Context context, SurespotIdentity identity, Cookie cookie) {
		// load the identity
		if (identity != null) {
			Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, identity.getUsername());
			Utils.putSharedPrefsString(context, "referrer", null);
			SurespotApplication.getCachingService().login(identity, cookie);

			// set logging and crash reporting based on shared prefs for the
			// user
			SharedPreferences sp = context.getSharedPreferences(identity.getUsername(), Context.MODE_PRIVATE);

			// PROD TODO set to false on release
			// boolean prefLogging = sp.getBoolean("pref_logging",
			// SurespotConstants.LOGGING);
			// SurespotLog.v(TAG, "setting logging based on preference: %b",
			// prefLogging);

			// SurespotLog.setLogging(prefLogging);
			SurespotLog.setCrashReporting(sp.getBoolean("pref_crash_reporting", SurespotConstants.CRASH_REPORTING));

			// you would think changing the shared prefs name would update the
			// internal state but it doesn't
			// ACRA.getConfig().setSharedPreferenceName(identity.getUsername());
			// ACRA.getConfig().setSharedPreferenceMode(Context.MODE_PRIVATE);
			//
			// boolean enableACRA = sp.getBoolean(ACRA.PREF_ENABLE_ACRA, false);
			// ACRA.getErrorReporter().setEnabled(enableACRA);
		} else {
			SurespotLog.w(TAG, "getIdentity null");
		}
	}

	public static synchronized void createIdentity(final Context context, final String username, final String password, final String salt,
			final KeyPair keyPairDH, final KeyPair keyPairECDSA, final Cookie cookie) {
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = new SurespotIdentity(username, salt);
		identity.addKeyPairs("1", keyPairDH, keyPairECDSA);
		saveIdentity(context, identityDir, identity, password + CACHE_IDENTITY_ID);
		setLoggedInUser(context, identity, cookie);

	}

	public static void updatePassword(Context context, String username, String currentPassword, String newPassword, String newSalt) {
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = getIdentity(context, username, currentPassword);
		identity.setSalt(newSalt);
		if (identity != null) {
			saveIdentity(context, identityDir, identity, newPassword + CACHE_IDENTITY_ID);
		}
	}

	private static synchronized String saveIdentity(Context backupContext, String identityDir, SurespotIdentity identity, String password) {
		String filename = identity.getUsername() + IDENTITY_EXTENSION;

		byte[] identityBytes = encryptIdentity(identity, password);

		if (identityBytes == null) {
			return null;
		}

		String identityFile = identityDir + File.separator + filename;
		try {
			synchronized (IDENTITY_FILE_LOCK) {

				SurespotLog.v(TAG, "saving identity: %s, salt: %s", identityFile, identity.getSalt());

				if (!FileUtils.ensureDir(identityDir)) {
					SurespotLog.e(TAG, new RuntimeException("Could not create identity dir: " + identityDir), "Could not create identity dir: %s", identityDir);
					return null;
				}

				FileUtils.writeFile(identityFile, identityBytes);
			}

			// tell com.twofours.surespot.backup manager the data has changed
			if (backupContext != null) {
				SurespotLog.v(TAG, "telling com.twofours.surespot.backup manager data changed");
				// SurespotApplication.mBackupManager.dataChanged();
			}
			return identityFile;
		}

		catch (FileNotFoundException e) {
			SurespotLog.w(TAG, e, "saveIdentity");
		} catch (IOException e) {
			SurespotLog.w(TAG, e, "saveIdentity");
		}
		return null;
	}

	private static byte[] encryptIdentity(SurespotIdentity identity, String password) {
		JSONObject json = new JSONObject();
		try {
			json.put("username", identity.getUsername());
			json.put("salt", identity.getSalt());

			JSONArray keys = new JSONArray();

			for (PrivateKeyPairs keyPair : identity.getKeyPairs()) {
				JSONObject jsonKeyPair = new JSONObject();

				jsonKeyPair.put("version", keyPair.getVersion());
				jsonKeyPair.put("dhPriv", new String(ChatUtils.base64EncodeNowrap(keyPair.getKeyPairDH().getPrivate().getEncoded())));
				jsonKeyPair.put("dhPub", EncryptionController.encodePublicKey(keyPair.getKeyPairDH().getPublic()));
				jsonKeyPair.put("dsaPriv", new String(ChatUtils.base64EncodeNowrap(keyPair.getKeyPairDSA().getPrivate().getEncoded())));
				jsonKeyPair.put("dsaPub", EncryptionController.encodePublicKey(keyPair.getKeyPairDSA().getPublic()));

				keys.put(jsonKeyPair);
			}

			json.put("keys", keys);

			byte[] identityBytes = EncryptionController.symmetricEncryptSyncPK(password, json.toString());
			return identityBytes;
		}

		catch (JSONException e) {
			SurespotLog.w(TAG, e, "saveIdentity");
		}
		return null;
	}

	public static void getExportIdentity(final Context context, final String username, final String password, final IAsyncCallbackTuple<byte[], String> callback) {

		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {

				final SurespotIdentity identity = getIdentity(context, username, password);
				if (identity == null) {
					callback.handleResponse(null, null);
				}

				final File exportDir = FileUtils.getIdentityExportDir();
				if (FileUtils.ensureDir(exportDir.getPath())) {
					byte[] saltyBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());

					String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltyBytes)));
					// do OOB verification
					MainActivity.getNetworkController().validate(username, dPassword,
							EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword), new AsyncHttpResponseHandler() {
								public void onSuccess(int statusCode, String content) {

									callback.handleResponse(encryptIdentity(identity, password + EXPORT_IDENTITY_ID), null);
								}

								public void onFailure(Throwable error) {

									if (error instanceof HttpResponseException) {
										int statusCode = ((HttpResponseException) error).getStatusCode();
										// would use 401 but we're intercepting
										// those
										// and I don't feel like special casing
										// it
										switch (statusCode) {
										case 403:
											callback.handleResponse(null, context.getString(R.string.incorrect_password_or_key));
											break;
										case 404:
											callback.handleResponse(null, context.getString(R.string.incorrect_password_or_key));
											break;

										default:
											SurespotLog.w(TAG, error, "exportIdentity");
											callback.handleResponse(null, null);
										}
									} else {
										callback.handleResponse(null, null);
									}
								}
							});
				} else {
					callback.handleResponse(null, null);
				}
				return null;
			}

		}.execute();

	}

	public static boolean identityFileExists(Context context, String username) {
		String identityDir = getIdentityDir(context);
		String filename = username + IDENTITY_EXTENSION;
		String sIdentityFile = identityDir + File.separator + filename;
		return new File(sIdentityFile).exists();
	}

	public static boolean ensureIdentityFile(Context context, String username, boolean overwrite) {
		// make sure file we're going to save to is writable before we start

		String identityDir = getIdentityDir(context);
		File idDirFile = new File(identityDir);
		idDirFile.mkdirs();

		if (!idDirFile.isDirectory()) {
			return false;
		}

		String filename = username + IDENTITY_EXTENSION;
		String sIdentityFile = identityDir + File.separator + filename;

		File identityFile = new File(sIdentityFile);
		boolean exists = identityFile.exists();

		if (exists && !overwrite) {
			return false;
		} else {
			if (exists) {
				return identityFile.isFile() && identityFile.canWrite();
			} else {

				try {
					// make sure we'll have the space to write the identity file
					FileOutputStream fos = new FileOutputStream(identityFile);
					fos.write(new byte[10000]);
					fos.close();
					identityFile.delete();
					return true;
				} catch (IOException e) {
					return false;
				}
			}
		}
	}

	private static String getIdentityDir(Context context) {
		String identityDir = FileUtils.getIdentityDir(context);
		return identityDir;
	}

	public static synchronized void deleteIdentity(Context context, String username) {
		// force identity reload
		mHasIdentity = false;

		boolean isLoggedIn = false;
		if (username.equals(IdentityController.getLoggedInUser())) {
			isLoggedIn = true;
		}

		if (isLoggedIn) {
			SurespotApplication.getCachingService().logout();
		}

		SurespotApplication.getCachingService().clearIdentityData(username, true);

		MainActivity.getNetworkController().clearCache();
		StateController.wipeState(context, username);

		synchronized (IDENTITY_FILE_LOCK) {
			String identityFilename = FileUtils.getIdentityDir(context) + File.separator + username + IDENTITY_EXTENSION;
			File file = new File(identityFilename);
			file.delete();

			// delete export identity
			final File exportDir = FileUtils.getIdentityExportDir();
			identityFilename = exportDir + File.separator + username + IDENTITY_EXTENSION;
			file = new File(identityFilename);
			file.delete();

			// create deleted file so we can remove the
			// com.twofours.surespot.backup if there is one
			String deletedFilename = FileUtils.getIdentityDir(context) + File.separator + username + IDENTITY_DELETED_EXTENSION;
			try {
				new File(deletedFilename).createNewFile();
			} catch (IOException e) {
				SurespotLog.e(TAG, e, "could not create deleted identity file: %s", deletedFilename);
			}

		}

		// SurespotApplication.mBackupManager.dataChanged();

		if (isLoggedIn) {
			UIUtils.launchMainActivityDeleted(context);
		}

	}

	public static SurespotIdentity getIdentity(String username) {
		return SurespotApplication.getCachingService().getIdentity(username);
	}

	public static SurespotIdentity getIdentity() {
		return SurespotApplication.getCachingService().getIdentity();
	}

	public static SurespotIdentity getIdentity(Context context, String username, String password) {
		SurespotIdentity identity = SurespotApplication.getCachingService().getIdentity(username);
		if (identity == null) {
			identity = loadIdentity(context, FileUtils.getIdentityDir(context), username, password + CACHE_IDENTITY_ID);
		}
		return identity;

	}

	private synchronized static SurespotIdentity loadIdentity(Context context, String dir, String username, String password) {
		String identityFilename = dir + File.separator + username + IDENTITY_EXTENSION;
		File idFile = new File(identityFilename);

		if (!idFile.canRead()) {
			SurespotLog.e(TAG, new IOException("Could not load identity file: " + identityFilename), "Could not load identity.");
			return null;
		}

		try {
			byte[] idBytes = null;
			synchronized (IDENTITY_FILE_LOCK) {

				idBytes = FileUtils.readFile(identityFilename);

			}

			if (idBytes != null) {
				return decryptIdentity(idBytes, username, password);
			}

		} catch (Exception e) {
			SurespotLog.w(TAG, e, "loadIdentity");
		}

		return null;
	}

	private static SurespotIdentity decryptIdentity(byte[] idBytes, String username, String password) {
		String identity = EncryptionController.symmetricDecryptSyncPK(password, idBytes);

		if (identity == null) {
			SurespotLog.w(TAG, "could not decrypt identity: %s", username);
			return null;
		}

		try {
			JSONObject jsonIdentity = new JSONObject(identity);
			String name = jsonIdentity.getString("username");
			String salt = jsonIdentity.getString("salt");

			SurespotLog.w(TAG, "loaded identity: %s, salt: %s", username, salt);
			if (!name.equals(username)) {
				SurespotLog.e(TAG, new RuntimeException("internal identity: " + name + " did not match: " + username), "internal identity did not match");
				return null;
			}

			SurespotIdentity si = new SurespotIdentity(username, salt);

			JSONArray keys = jsonIdentity.getJSONArray("keys");
			for (int i = 0; i < keys.length(); i++) {
				JSONObject json = keys.getJSONObject(i);
				String version = json.getString("version");
				String spubDH = json.getString("dhPub");
				String sprivDH = json.getString("dhPriv");
				String spubECDSA = json.getString("dsaPub");
				String sprivECDSA = json.getString("dsaPriv");
				si.addKeyPairs(version,
						new KeyPair(EncryptionController.recreatePublicKey("ECDH", spubDH), EncryptionController.recreatePrivateKey("ECDH", sprivDH)),
						new KeyPair(EncryptionController.recreatePublicKey("ECDSA", spubECDSA), EncryptionController.recreatePrivateKey("ECDSA", sprivECDSA)));

			}

			return si;
		} catch (JSONException e) {
		}
		return null;

	}

	public static void importIdentity(final Context context, File exportDir, final String username, final String password,
			final IAsyncCallback<IdentityOperationResult> callback) {
		final SurespotIdentity identity = loadIdentity(context, exportDir.getPath(), username, password + EXPORT_IDENTITY_ID);
		if (identity != null) {

			byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
			String dpassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

			NetworkController networkController = MainActivity.getNetworkController();
			if (networkController == null) {
				networkController = new NetworkController(context, null);
			}
			networkController.validate(username, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dpassword),
					new AsyncHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, String content) {

							// should never happen
							SurespotIdentity existingIdentity = loadIdentity(context, FileUtils.getIdentityDir(context), username, password + CACHE_IDENTITY_ID);
							if (existingIdentity != null) {
								int importVersion = Integer.parseInt(identity.getLatestVersion());
								int existingVersion = Integer.parseInt(existingIdentity.getLatestVersion());
								if (importVersion <= existingVersion) {
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.newer_identity_exists), false));
									return;
								}
							}

							String file = saveIdentity(context, FileUtils.getIdentityDir(context), identity, password + CACHE_IDENTITY_ID);
							if (file != null) {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully), true));
							} else {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
							}
						}

						@Override
						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those
								// and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.incorrect_password_or_key), false));
									break;
								case 404:
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.no_such_user), false));
									break;

								default:
									SurespotLog.w(TAG, error, "importIdentity");
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
								}
							} else {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
							}
						}
					});

		} else {
			callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
		}

	}

	public static void importIdentityBytes(final Context context, final String username, final String password, byte[] identityBytes,
			final IAsyncCallback<IdentityOperationResult> callback) {
		final SurespotIdentity identity = decryptIdentity(identityBytes, username, password + EXPORT_IDENTITY_ID);
		if (identity != null) {

			byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
			String dpassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

			NetworkController networkController = MainActivity.getNetworkController();
			if (networkController == null) {
				networkController = new NetworkController(context, null);
			}
			networkController.validate(username, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dpassword),
					new AsyncHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, String content) {

							// should never happen
							SurespotIdentity existingIdentity = loadIdentity(context, FileUtils.getIdentityDir(context), username, password + CACHE_IDENTITY_ID);
							if (existingIdentity != null) {
								int importVersion = Integer.parseInt(identity.getLatestVersion());
								int existingVersion = Integer.parseInt(existingIdentity.getLatestVersion());
								if (importVersion <= existingVersion) {
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.newer_identity_exists), false));
									return;
								}
							}

							String file = saveIdentity(context, FileUtils.getIdentityDir(context), identity, password + CACHE_IDENTITY_ID);
							if (file != null) {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully), true));
							} else {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
							}
						}

						@Override
						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those
								// and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.incorrect_password_or_key), false));
									break;
								case 404:
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.no_such_user), false));
									break;

								default:
									SurespotLog.w(TAG, error, "importIdentity");
									callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
								}
							} else {
								callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
							}
						}
					});

		} else {
			callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_import_identity), false));
		}

	}

	public static void exportIdentity(final Context context, final String username, final String password, final IAsyncCallback<String> callback) {
		final SurespotIdentity identity = getIdentity(context, username, password);
		if (identity == null) {
			callback.handleResponse(null);
			return;
		}

		final File exportDir = FileUtils.getIdentityExportDir();
		if (FileUtils.ensureDir(exportDir.getPath())) {
			byte[] saltyBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());

			String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltyBytes)));
			// do OOB verification
			MainActivity.getNetworkController().validate(username, dPassword,
					EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword), new AsyncHttpResponseHandler() {
						public void onSuccess(int statusCode, String content) {
							String path = saveIdentity(null, exportDir.getPath(), identity, password + EXPORT_IDENTITY_ID);
							callback.handleResponse(path == null ? null : context.getString(R.string.backed_up_identity_to_path, username, path));
						}

						public void onFailure(Throwable error) {

							if (error instanceof HttpResponseException) {
								int statusCode = ((HttpResponseException) error).getStatusCode();
								// would use 401 but we're intercepting those
								// and I don't feel like special casing it
								switch (statusCode) {
								case 403:
									callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
									break;
								case 404:
									callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
									break;

								default:
									SurespotLog.w(TAG, error, "exportIdentity");
									callback.handleResponse(null);
								}
							} else {
								callback.handleResponse(null);
							}
						}
					});
		} else {
			callback.handleResponse(null);
		}

	}

	private static synchronized String savePublicKeyPair(String username, String version, String keyPair) {
		try {
			String dir = FileUtils.getPublicKeyDir(MainActivity.getContext()) + File.separator + username;
			if (!FileUtils.ensureDir(dir)) {
				SurespotLog.e(TAG, new RuntimeException("Could not create public key pair dir: %s" + dir), "Could not create public key pair dir: %s", dir);
				return null;
			}

			String pkFile = dir + File.separator + version + PUBLICKEYPAIR_EXTENSION;
			SurespotLog.v(TAG, "saving public key pair: %s", pkFile);

			FileUtils.writeFile(pkFile, keyPair);

			return pkFile;
		} catch (IOException e) {
			SurespotLog.w(TAG, e, "saveIdentity");
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
			} catch (JSONException e) {
				SurespotLog.w(TAG, e, "recreatePublicKeyPair");
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
				SurespotLog.w(TAG, new KeyException("Could not verify DH key against server signature."), "could not verify DH key against server signature");
				return null;
			} else {
				SurespotLog.i(TAG, "DH key successfully verified");
			}

			boolean dsaVerify = EncryptionController.verifyPublicKey(sSigECDSA, spubECDSA);
			if (!dsaVerify) {
				// alert alert
				SurespotLog.w(TAG, new KeyException("Could not verify DSA key against server signature."), "could not verify DSA key against server signature");
				return null;
			} else {
				SurespotLog.i(TAG, "DSA key successfully verified");
			}

			return json;
		} catch (JSONException e) {
			SurespotLog.w(TAG, e, "recreatePublicIdentity");
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

		// sort ignoring case
		Collections.sort(identityNames, new Comparator<String>() {

			@Override
			public int compare(String lhs, String rhs) {
				return lhs.compareToIgnoreCase(rhs);
			}
		});
		return identityNames;

	}

	public static List<String> getDeletedIdentityNames(Context context) {
		String dir = getIdentityDir(context);

		ArrayList<String> identityNames = new ArrayList<String>();
		File[] files = new File(dir).listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(IDENTITY_DELETED_EXTENSION);
			}
		});

		if (files != null) {
			for (File f : files) {
				identityNames.add(getIdentityNameFromFile(f));
			}
		}

		return identityNames;
	}

	public static String getIdentityNameFromFile(File file) {
		return getIdentityNameFromFilename(file.getName());
	}

	public static String getIdentityNameFromFilename(String filename) {
		return filename.substring(0, filename.length() - IDENTITY_DELETED_EXTENSION.length());
	}

	public static synchronized int getIdentityCount(Context context) {
		return getIdentityNames(context, FileUtils.getIdentityDir(context)).size();
	}

	public static List<String> getIdentityNames(Context context) {
		return getIdentityNames(context, FileUtils.getIdentityDir(context));
	}

	public static File[] getIdentityFiles(Context context, String dir) {
		File[] files = new File(dir).listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(IDENTITY_EXTENSION);
			}
		});

		return files;

	}

	public static void userLoggedIn(Context context, SurespotIdentity identity, Cookie cookie) {
		setLoggedInUser(context, identity, cookie);
	}

	public static void logout() {
		// ChatController chatController = MainActivity.getChatController();
		// if (chatController != null) {
		// chatController.logout();
		// }
		if (MainActivity.getNetworkController() != null) {
			MainActivity.getNetworkController().logout();
		}
		SurespotApplication.getCachingService().logout();
	}

	private synchronized static PublicKeys loadPublicKeyPair(String username, String version) {

		// try to load identity
		String pkFilename = FileUtils.getPublicKeyDir(MainActivity.getContext()) + File.separator + username + File.separator + version
				+ PUBLICKEYPAIR_EXTENSION;
		File pkFile = new File(pkFilename);

		if (!pkFile.canRead()) {
			SurespotLog.v(TAG, "Could not load public key pair file: %s", pkFilename);
			return null;
		}

		try {

			byte[] pkBytes = FileUtils.readFile(pkFilename);

			JSONObject pkpJSON = new JSONObject(new String(pkBytes));

			return new PublicKeys(pkpJSON.getString("version"), EncryptionController.recreatePublicKey("ECDH", pkpJSON.getString("dhPub")),
					EncryptionController.recreatePublicKey("ECDSA", pkpJSON.getString("dsaPub")));

		} catch (Exception e) {
			SurespotLog.w(TAG, "loadPublicKeyPair", e);
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

		} else {
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
		return SurespotApplication.getCachingService().getIdentity().getLatestVersion();
	}

	public static String getOurLatestVersion(String username) {
		return SurespotApplication.getCachingService().getIdentity(username).getLatestVersion();

	}

	public static void rollKeys(Context context, String username, String password, String keyVersion, KeyPair keyPairDH, KeyPair keyPairsDSA) {
		String identityDir = FileUtils.getIdentityDir(context);
		SurespotIdentity identity = getIdentity(context, username, password);
		identity.addKeyPairs(keyVersion, keyPairDH, keyPairsDSA);
		String idFile = saveIdentity(context, identityDir, identity, password + CACHE_IDENTITY_ID);
		// big problems if we can't save it, but shouldn't happen as we create
		// the file first
		if (idFile == null) {
			// TODO give user other options to save it
			SurespotLog.e(TAG, new Exception("could not save identity after rolling keys"), "could not save identity after rolling keys");
		}

		// if we're logged in update the identity in the cache
		if (getLoggedInUser().equals(username)) {
			SurespotApplication.getCachingService().updateIdentity(identity);
		}

	}

	public static void updateLatestVersion(Context context, String username, String version) {
		// see if we are the user that's been revoked
		// if we have the latest version locally, if we don't then this user has
		// been revoked from a different device
		// and should not be used on this device anymore
		if (username.equals(getLoggedInUser()) && version.compareTo(getOurLatestVersion()) > 0) {
			SurespotLog.v(TAG, "user revoked, deleting data and logging out");

			// bad news
			// delete the identity file and cached data
			deleteIdentity(context, username);

			// delete identities locally?
			MainActivity.getNetworkController().setUnauthorized(true);

			// boot them out
			launchLoginActivity(context);

			// TODO tell user?
			// Utils.makeLongToast(context, "identity: " + username +
			// " revoked");

		} else {
			SurespotApplication.getCachingService().updateLatestVersion(username, version);
		}

	}

	private static void launchLoginActivity(Context context) {
		Intent intent = new Intent(context, LoginActivity.class);
		intent.putExtra("401", true);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(intent);

	}

}
