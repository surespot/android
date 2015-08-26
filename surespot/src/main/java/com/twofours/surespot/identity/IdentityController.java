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
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nick.androidkeystore.android.security.KeyStore;
import org.nick.androidkeystore.android.security.KeyStoreJb43;
import org.nick.androidkeystore.android.security.KeyStoreKk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.Tuple;
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
    public static final String PUBLICKEYPAIR_EXTENSION = ".spk";
    public static final String CACHE_IDENTITY_ID = "_cache_identity";
    public static final String EXPORT_IDENTITY_ID = "_export_identity";
    public static final Object IDENTITY_FILE_LOCK = new Object();
    private static boolean mHasIdentity;
    private static KeyStore mKs;

    private synchronized static void setLoggedInUser(final Context context, SurespotIdentity identity, Cookie cookie, String password) {
        // load the identity
        if (identity != null) {
            Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, identity.getUsername());
            Utils.putSharedPrefsString(context, "referrer", null);
            SurespotApplication.getCachingService().login(identity, cookie, password);
            // if we're logging in we probably didn't just buy it
            SurespotApplication.getBillingController().clearJustPurchased();
        } else {
            SurespotLog.w(TAG, "getIdentity null");
        }
    }

    public static synchronized void createIdentity(final Context context, final String username, final String password, final String salt,
                                                   final KeyPair keyPairDH, final KeyPair keyPairECDSA, final Cookie cookie) {
        SurespotIdentity identity = new SurespotIdentity(username, salt);
        identity.addKeyPairs("1", keyPairDH, keyPairECDSA);
        saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
        setLoggedInUser(context, identity, cookie, password);

    }

    public static void updatePassword(Context context, SurespotIdentity identity, String username, String currentPassword, String newPassword, String newSalt) {
        if (identity != null) {
            identity.setSalt(newSalt);
            saveIdentity(context, true, identity, newPassword + CACHE_IDENTITY_ID);
            updateKeychainPassword(context, username, newPassword);
        }
    }


    private static synchronized String saveIdentity(Context backupContext, boolean internal, SurespotIdentity identity, String password) {
        String identityDir = null;
        String filename = null;
        // export files don't have case sensitivity so we have to massage the filename to indicate case
        if (internal) {
            filename = identity.getUsername() + IDENTITY_EXTENSION;
            identityDir = FileUtils.getIdentityDir(backupContext);
        } else {
            filename = caseInsensitivize(identity.getUsername()) + IDENTITY_EXTENSION;
            identityDir = FileUtils.getIdentityExportDir().getAbsolutePath();
        }

        if (identityDir == null || filename == null) {
            return null;
        }

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
        } catch (FileNotFoundException e) {
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
        } catch (JSONException e) {
            SurespotLog.w(TAG, e, "encryptIdentity");
        }
        return null;
    }

    public static void getExportIdentity(final Activity context, final String username, final String password,
                                         final IAsyncCallbackTuple<byte[], String> callback) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                final SurespotIdentity identity = getIdentity(context, username, password);
                if (identity == null) {
                    callback.handleResponse(null, null);
                }

                byte[] saltyBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());

                String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltyBytes)));
                // do OOB verification
                NetworkController networkController = MainActivity.getNetworkController();
                if (networkController == null) {
                    try {
                        networkController = new NetworkController(context, null, null);
                    } catch (Exception e) {
                        context.finish();
                        return null;
                    }
                }

                networkController.validate(username, dPassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword),
                        new AsyncHttpResponseHandler() {
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
                                            SurespotLog.i(TAG, error, "exportIdentity");
                                            callback.handleResponse(null, null);
                                    }
                                } else {
                                    callback.handleResponse(null, null);
                                }
                            }
                        });

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
        if (username.equals(getLoggedInUser())) {
            isLoggedIn = true;
        }

        SurespotApplication.getCachingService().clearIdentityData(username, true);

        if (isLoggedIn) {
            SurespotApplication.getCachingService().logout(true);
        }

        clearStoredPasswordForIdentity(username);

        MainActivity.getNetworkController().clearCache();
        StateController.wipeState(context, username);

        synchronized (IDENTITY_FILE_LOCK) {
            String identityFilename = FileUtils.getIdentityDir(context) + File.separator + username + IDENTITY_EXTENSION;
            File file = new File(identityFilename);
            file.delete();

            // delete export identity
            final File exportDir = FileUtils.getIdentityExportDir();

            // could potentially delete the wrong file so don't delete the case insensitive version
            // identityFilename = exportDir + File.separator + username + IDENTITY_EXTENSION;
            // file = new File(identityFilename);
            // file.delete();

            identityFilename = exportDir + File.separator + caseInsensitivize(username) + IDENTITY_EXTENSION;
            file = new File(identityFilename);
            file.delete();
        }

        if (isLoggedIn) {
            UIUtils.launchMainActivityDeleted(context);
        }

    }

    public static SurespotIdentity getIdentity(Context context) {
        return getIdentity(context, null, null);
    }

    public static SurespotIdentity getIdentity(Context context, String username, String password) {
        if (username == null) {
            username = getLastLoggedInUser(context);
        }

        SurespotIdentity identity = SurespotApplication.getCachingService().getIdentity(context, username, password);
        return identity;
    }

    public synchronized static SurespotIdentity loadIdentity(Context context, String username, String password) {
        return loadIdentity(context, true, username, password + CACHE_IDENTITY_ID);
    }

    private synchronized static SurespotIdentity loadIdentity(Context context, boolean internal, String username, String password) {
        String dir = null;
        String identityFilename = null;
        boolean checkCase = true;
        // try case insensitive filename
        if (internal) {
            dir = FileUtils.getIdentityDir(context);
            identityFilename = dir + File.separator + username + IDENTITY_EXTENSION;
        } else {
            dir = FileUtils.getIdentityExportDir().getAbsolutePath();
            identityFilename = dir + File.separator + caseInsensitivize(username) + IDENTITY_EXTENSION;
            checkCase = false;
        }

        if (identityFilename == null || dir == null) {
            return null;
        }

        File idFile = new File(identityFilename);

        if (!idFile.canRead() && !internal) {
            SurespotLog.i(TAG, "identity file: %s not present", identityFilename);

            // try case sensitive filename
            identityFilename = dir + File.separator + username + IDENTITY_EXTENSION;
            idFile = new File(identityFilename);

            if (!idFile.canRead()) {
                SurespotLog.i(TAG, "identity file: %s not present", identityFilename);
            }
            return null;
        }

        try {
            byte[] idBytes = null;
            synchronized (IDENTITY_FILE_LOCK) {

                // might have copied old ungzipped drive identity file to sdcard so handle both
                // RM#260
                idBytes = FileUtils.gunzipIfNecessary(FileUtils.readFileNoGzip(identityFilename));
            }

            if (idBytes != null) {
                return decryptIdentity(idBytes, username, password, checkCase);
            }

        } catch (Exception e) {
            SurespotLog.w(TAG, e, "loadIdentity");
        }

        return null;
    }

    private static SurespotIdentity decryptIdentity(byte[] idBytes, String username, String password, boolean checkCase) {
        String identity = EncryptionController.symmetricDecryptSyncPK(password, idBytes);

        if (identity == null) {
            SurespotLog.w(TAG, "could not decrypt identity: %s", username);
            return null;
        }

        try {
            JSONObject jsonIdentity = new JSONObject(identity);
            String name = jsonIdentity.getString("username");
            String salt = jsonIdentity.getString("salt");

            // SurespotLog.w(TAG, "loaded identity: %s, salt: %s", name, salt);
            // if (checkCase && !name.equals(username) || (!checkCase && !name.toLowerCase().equals(username.toLowerCase()))) {
            // SurespotLog.e(TAG, new RuntimeException("internal identity: " + name + " did not match: " + username), "internal identity did not match");
            // return null;
            // }

            SurespotIdentity si = new SurespotIdentity(name, salt);

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

    public static void importIdentity(final Activity context, File exportDir, String username, final String password,
                                      final IAsyncCallback<IdentityOperationResult> callback) {
        final SurespotIdentity identity = loadIdentity(context, false, username, password + EXPORT_IDENTITY_ID);
        if (identity != null) {

            byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
            final String finalusername = identity.getUsername();
            String dpassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

            NetworkController networkController = MainActivity.getNetworkController();

            if (networkController == null) {
                try {
                    networkController = new NetworkController(context, null, null);
                } catch (Exception e) {
                    context.finish();
                    return;
                }
            }

            networkController.validate(finalusername, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), finalusername, dpassword),
                    new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, String content) {
                            String file = saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
                            if (file != null) {
                                updateKeychainPassword(context, finalusername, password);
                                SurespotApplication.getCachingService().updateIdentity(identity, true);
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully, finalusername),
                                        true));
                            } else {
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, finalusername),
                                        false));
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
                                        SurespotLog.i(TAG, error, "importIdentity");
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name,
                                                finalusername), false));
                                }
                            } else {
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, finalusername),
                                        false));
                            }
                        }
                    });

        } else {
            callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username), false));
        }

    }

    public static void importIdentityBytes(final Activity context, final String username, final String password, byte[] identityBytes,
                                           final IAsyncCallback<IdentityOperationResult> callback) {
        final SurespotIdentity identity = decryptIdentity(identityBytes, username, password + EXPORT_IDENTITY_ID, true);
        if (identity != null) {

            byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
            final String finalusername = identity.getUsername();
            String dpassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));

            NetworkController networkController = MainActivity.getNetworkController();
            if (networkController == null) {
                try {
                    networkController = new NetworkController(context, null, null);
                } catch (Exception e) {
                    context.finish();
                    return;
                }
            }

            networkController.validate(finalusername, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), finalusername, dpassword),
                    new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, String content) {
                            String file = saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
                            if (file != null) {
                                updateKeychainPassword(context, finalusername, password);
                                SurespotApplication.getCachingService().updateIdentity(identity, true);
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully, finalusername),
                                        true));
                            } else {
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                        false));
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
                                        SurespotLog.i(TAG, error, "importIdentity");
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                                false));
                                }
                            } else {
                                callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                        false));
                            }
                        }
                    });

        } else {
            callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username), false));
        }

    }

    public static void exportIdentity(final Context context, String username, final String password, final IAsyncCallback<String> callback) {
        final SurespotIdentity identity = getIdentity(context, username, password);
        if (identity == null) {
            callback.handleResponse(null);
            return;
        }

        final String finalUsername = identity.getUsername();
        final File exportDir = FileUtils.getIdentityExportDir();
        if (FileUtils.ensureDir(exportDir.getPath())) {
            byte[] saltyBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());

            String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltyBytes)));
            // do OOB verification
            MainActivity.getNetworkController().validate(username, dPassword,
                    EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword), new AsyncHttpResponseHandler() {
                        public void onSuccess(int statusCode, String content) {
                            String path = saveIdentity(null, false, identity, password + EXPORT_IDENTITY_ID);
                            callback.handleResponse(path == null ? null : context.getString(R.string.backed_up_identity_to_path, finalUsername, path));
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
                                        SurespotLog.i(TAG, error, "exportIdentity");
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

    private static PublicKeys getPublicKeyPair(String username, String version, JSONObject jsonKeyPair) {
        try {
            String readVersion = jsonKeyPair.getString("version");
            if (!readVersion.equals(version)) {
                return null;
            }

            JSONObject json = verifyPublicKeyPair(jsonKeyPair);
            if (json == null) {
                return null;
            }


            String spubDH = json.getString("dhPub");
            String spubECDSA = json.getString("dsaPub");

            PublicKey dhPub = EncryptionController.recreatePublicKey("ECDH", spubDH);
            PublicKey dsaPub = EncryptionController.recreatePublicKey("ECDSA", spubECDSA);

            savePublicKeyPair(username, version, json.toString());
            SurespotLog.i(TAG, "loaded public keys from server for username %s", username);
            return new PublicKeys(version, dhPub, dsaPub, new Date().getTime());
        } catch (JSONException e) {
            SurespotLog.w(TAG, e, "recreatePublicKeyPair");
        }

        return null;
    }

    public static PublicKeys getPublicKeyPair2(String username, String version) {

        int currentVersion = Integer.parseInt(version, 10);
        int wantedVersion = currentVersion;

        PublicKeys keys = null;
        String sDownloadedKeys = null;

        PublicKeys validatedKeys = null;
        int validatedKeyVersion = 0;
        Hashtable<Integer, PublicKey> dsaKeys = new Hashtable<Integer, PublicKey>();
        Hashtable<Integer, JSONObject> resultKeys = new Hashtable<Integer, JSONObject>();


        //attempt to load keys from disk until we have some
        while (currentVersion > 0) {
            String sCurrentVersion = Integer.toString(currentVersion, 10);

            //load keys locally
            //if we have them they've been validated and we can validate any new keys we downloaded
            keys = loadPublicKeyPair(username, sCurrentVersion);
            if (keys != null) {
                validatedKeys = keys;
                validatedKeyVersion = currentVersion;
                break;
            }
            currentVersion--;
        }

        //if we have the keys for the version we want return them
        if (validatedKeys != null && wantedVersion == validatedKeyVersion) {
            return validatedKeys;
        } else {
            //get keys from server since the last validated version
            sDownloadedKeys = MainActivity.getNetworkController().getPublicKeysSync(username, Integer.toString(validatedKeyVersion + 1, 10));

            //validate from the last validated version to the version we want
            if (sDownloadedKeys != null) {
                try {
                    JSONArray json = new JSONArray(sDownloadedKeys);

                    for (int i = 0; i < json.length(); i++) {
                        //build some data structures
                        JSONObject jsonKeys = json.getJSONObject(i);
                        int readVersion = jsonKeys.getInt("version");
                        String spubECDSA = jsonKeys.getString("dsaPub");
                        PublicKey dsaPub = EncryptionController.recreatePublicKey("ECDSA", spubECDSA);
                        dsaKeys.put(readVersion, dsaPub);
                        resultKeys.put(readVersion, jsonKeys);
                    }

                    //if we have clientSig, use new validation
                    //otherwise use old (for now until we cut off old version)

                    JSONObject wantedKey = resultKeys.get(wantedVersion);

                    if (!wantedKey.has("clientSig")) {
                        SurespotLog.d(TAG, "Validating username: %s, version: %s, keys using v1 code", username, version);
                        //TODO need to recheck somehow and eventually get all keys validated using v2 code
                        return getPublicKeyPair(username, version, wantedKey);

                    } else {
                        SurespotLog.d(TAG, "Validating username: %s, version: %s, keys using v2 code", username, version);

                        PublicKey previousDsaKey = null;
                        if (validatedKeys != null) {
                            //if we have a key validated start with that
                            previousDsaKey = validatedKeys.getDSAKey();
                        } else {
                            //otherwise start from ground zero
                            previousDsaKey = dsaKeys.get(1);
                        }
                        //validate in order
                        String sDhPub = null;
                        String sDsaPub = null;

                        for (int validatingVersion = validatedKeyVersion + 1; validatingVersion <= wantedVersion; validatingVersion++) {

                            JSONObject jsonKey = resultKeys.get(validatingVersion);
                            sDhPub = jsonKey.getString("dhPub");
                            sDsaPub = jsonKey.getString("dsaPub");


                            //validate dh and dsa against server sig
                            boolean verified = EncryptionController.verifySig(
                                    EncryptionController.ServerPublicKey,
                                    resultKeys.get(validatingVersion).getString("serverSig"),
                                    username,
                                    validatingVersion,
                                    sDhPub,
                                    sDsaPub
                            );

                            if (!verified) {
                                return null;
                            }

                            //client sig
                            verified = EncryptionController.verifySig(

                                    previousDsaKey,
                                    resultKeys.get(validatingVersion).getString("clientSig"),
                                    username,
                                    validatingVersion,
                                    sDhPub,
                                    sDsaPub);

                            if (!verified) {
                                return null;
                            }

                            //save some keys
                            savePublicKeyPair(username, String.valueOf(validatingVersion), jsonKey.toString());

                            //get next previous signing key
                            previousDsaKey = dsaKeys.get(validatingVersion);

                        }

                        PublicKey dhPub = EncryptionController.recreatePublicKey("ECDH", sDhPub);
                        PublicKey dsaPub = dsaKeys.get(wantedVersion);


                        //savePublicKeyPair(username, version, json.toString());
                        SurespotLog.i(TAG, "loaded and verified public keys from server for username %s", username);
                        return new PublicKeys(version, dhPub, dsaPub, new Date().getTime());
                    }
                } catch (JSONException e) {
                    SurespotLog.w(TAG, e, "recreatePublicKeyPair");
                }
            }
        }
        return null;
    }


    private static JSONObject verifyPublicKeyPair(JSONObject jsonKeypair) {
        try {
            String spubDH = jsonKeypair.getString("dhPub");
            String sSigDH = jsonKeypair.getString("dhPubSig");

            String spubECDSA = jsonKeypair.getString("dsaPub");
            String sSigECDSA = jsonKeypair.getString("dsaPubSig");

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

            return jsonKeypair;
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
                identityNames.add(caseSensitivize(f.getName().substring(0, f.getName().length() - IDENTITY_EXTENSION.length())));
            }
        }

        // sort ignoring case
        Collections.sort(identityNames, new Comparator<String>() {

            @Override
            public int compare(String lhs, String rhs) {
                return ComparisonChain.start().compare(lhs.toLowerCase(), rhs.toLowerCase(), Ordering.natural()).result();
            }
        });
        return identityNames;

    }

    public static String getIdentityNameFromFile(File file) {
        return getIdentityNameFromFilename(file.getName());
    }

    public static String getIdentityNameFromFilename(String filename) {
        return caseSensitivize(filename.substring(0, filename.length() - IDENTITY_EXTENSION.length()));
    }

    public static synchronized int getIdentityCount(Context context) {
        return getIdentityNames(context, FileUtils.getIdentityDir(context)).size();
    }

    public static List<String> getIdentityNames(Context context) {
        return getIdentityNames(context, FileUtils.getIdentityDir(context));
    }

    public static File[] getExportIdentityFiles(Context context, String dir) {
        File[] files = new File(dir).listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(IDENTITY_EXTENSION);
            }
        });

        return files;

    }

    public static void userLoggedIn(Context context, SurespotIdentity identity, Cookie cookie, String password) {
        setLoggedInUser(context, identity, cookie, password);
    }

    public static void logout() {
        if (hasLoggedInUser()) {
            if (MainActivity.getNetworkController() != null) {
                MainActivity.getNetworkController().logout();
            }
            CredentialCachingService cache = SurespotApplication.getCachingService();
            if (cache != null) {
                cache.logout(false);
            }
        }
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

        long lastModified = pkFile.lastModified();

        try {

            byte[] pkBytes = FileUtils.readFile(pkFilename);

            JSONObject pkpJSON = new JSONObject(new String(pkBytes));

            return new PublicKeys(pkpJSON.getString("version"), EncryptionController.recreatePublicKey("ECDH", pkpJSON.getString("dhPub")),
                    EncryptionController.recreatePublicKey("ECDSA", pkpJSON.getString("dsaPub")), lastModified);

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

    public static String getLastLoggedInUser(Context context) {
        return Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER);
    }

    public static Cookie getCookieForUser(String username) {
        Cookie cookie = null;
        CredentialCachingService service = SurespotApplication.getCachingService();
        if (service != null) {

            if (username != null) {
                SurespotLog.d(TAG, "getting cookie for %s", username);

                cookie = SurespotApplication.getCachingService().getCookie(username);

                SurespotLog.d(TAG, "returning cookie: %s", cookie);
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
        return SurespotApplication.getCachingService() == null ? null : SurespotApplication.getCachingService().getIdentity(null).getLatestVersion();
    }

    public static String getOurLatestVersion(String username) {
        return SurespotApplication.getCachingService().getIdentity(null, username, null).getLatestVersion();

    }

    public static void rollKeys(Context context, SurespotIdentity identity, String username, String password, String keyVersion, KeyPair keyPairDH, KeyPair keyPairsDSA) {
        if (identity == null) {
            // TODO give user other options to save it
            SurespotLog.e(TAG, new Exception("could not save identity after rolling keys"), "could not save identity after rolling keys");
            return;
        }

        identity.addKeyPairs(keyVersion, keyPairDH, keyPairsDSA);
        String idFile = saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
        // big problems if we can't save it, but shouldn't happen as we create
        // the file first
        if (idFile == null) {
            // TODO give user other options to save it
            SurespotLog.e(TAG, new Exception("could not save identity after rolling keys"), "could not save identity after rolling keys");
        }

        SurespotApplication.getCachingService().updateIdentity(identity, true);
    }

    public static void updateLatestVersion(Context context, String username, String version) {
        // see if we are the user that's been revoked
        // if we have the latest version locally, if we don't then this user has
        // been revoked from a different device
        // and should not be used on this device anymore
        if (username.equals(getLoggedInUser()) && (Integer.parseInt(version) > Integer.parseInt(getOurLatestVersion()))) {
            SurespotLog.v(TAG, "user revoked, deleting data and logging out");

            // bad news
            // delete the identity file and cached data
            deleteIdentity(context, username);

            // delete identities locally?
            MainActivity.getNetworkController().setUnauthorized(true, true);

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

    // convert to case insensitive by prepending uppercase chars with _ and _
    public static String caseInsensitivize(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);

            if (Character.isUpperCase(c)) {
                sb.append("_");
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String caseSensitivize(String string) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);

            if (c == '_') {
                sb.append(Character.toUpperCase(string.charAt(++i)));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static final boolean IS_JB43 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    private static final boolean IS_JB = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static final boolean IS_KK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";

    public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";
    public static final String RESET_ACTION = "com.android.credentials.RESET";

    public static void initKeystore() {
        SurespotLog.d(TAG, "initKeyStore");
        if (mKs == null) {
            if (IS_KK) {
                mKs = KeyStoreKk.getInstance();
            } else if (IS_JB43) {
                mKs = KeyStoreJb43.getInstance();
            } else {
                mKs = KeyStore.getInstance();
            }

        }
    }

    public static KeyStore getKeystore() {
        return mKs;
    }

    public static void destroyKeystore() {
        if (mKs != null) {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... arg0) {
                    String[] keys = mKs.saw("");
                    for (String key : keys) {
                        boolean success = mKs.delete(key);
                        SurespotLog.d(TAG, String.format("delete key '%s' success: %s", key, success));
                        if (!success && IS_JB) {
                            success = mKs.delKey(key);
                            SurespotLog.d(TAG, String.format("delKey '%s' success: %s", key, success));
                        }
                    }
                    mKs = null;
                    return null;

                }
            }.execute();
        }
    }

    public static boolean isKeystoreUnlocked() {
        if (mKs == null) {
            initKeystore();
        }
        if (mKs == null) {
            return false;
        }
        return mKs.state() == KeyStore.State.UNLOCKED;
    }

    public static boolean unlock(Context activity) {
        SurespotLog.d(TAG, "unlock");
        if (mKs.state() == KeyStore.State.UNLOCKED) {
            return true;
        }

        Intent intent = new Intent(activity, SurespotKeystoreActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        activity.startActivity(intent);
        return false;
    }

    public static String getStoredPasswordForIdentity(Context context, String username) {
        SurespotLog.d(TAG, "getStoredPasswordForIdentity: %s", username);

        if (username != null) {
            if (isKeystoreUnlocked()) {
                byte[] secret = mKs.get(username);
                if (secret != null) {
                    SurespotLog.d(TAG, "getStoredPasswordForIdentity...found password for %s", username);
                    return new String(secret);
                }
            } else {
                SurespotLog.d(TAG, "getStoredPasswordForIdentity...keystore locked");
            }
        }

        return null;
    }

    public static boolean storePasswordForIdentity(Context activity, String username, String password) {
        if (activity == null)
            return false;

        if (isKeystoreUnlocked()) {
            if (username != null && password != null) {
                Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
                return mKs.put(username, password.getBytes());
            }
        } else {
            unlock(activity);
        }

        return false;
    }

    public static boolean clearStoredPasswordForIdentity(String username) {
        if (username != null) {
            if (isKeystoreUnlocked()) {
                return mKs.delete(username);
            }
        }

        return false;
    }

    private static void updateKeychainPassword(Context context, String username, String password) {
        //update stored password if we have one
        String storedPassword = getStoredPasswordForIdentity(context, username);
        if (storedPassword != null) {
            storePasswordForIdentity(context, username, password);
        }
    }

}
