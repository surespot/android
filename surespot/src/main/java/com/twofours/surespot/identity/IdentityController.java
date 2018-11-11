package com.twofours.surespot.identity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nick.androidkeystore.android.security.KeyStore;
import org.nick.androidkeystore.android.security.KeyStoreJb43;
import org.nick.androidkeystore.android.security.KeyStoreKk;
import org.nick.androidkeystore.android.security.KeyStoreM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.Response;

public class IdentityController {
    private static final String TAG = "IdentityController";
    public static final String IDENTITY_EXTENSION = ".ssi";
    public static final String PUBLICKEYPAIR_EXTENSION = ".spk";
    public static final String CACHE_IDENTITY_ID = "_cache_identity";
    public static final String EXPORT_IDENTITY_ID = "_export_identity";
    public static final Object IDENTITY_FILE_LOCK = new Object();
    private static boolean mHasIdentity;
    private static KeyStore mKs;
    private static Map<String, String> mPasswords = new HashMap<>(5);

    private synchronized static void setLoggedInUser(final Context context, SurespotIdentity identity, Cookie cookie, String password) {
        // load the identity
        if (identity != null) {
            setLastUser(context, identity.getUsername());
            Utils.putSharedPrefsString(context, "referrer", null);
            CredentialCachingService ccs = SurespotApplication.getCachingService(context);
            if (ccs != null) {
                ccs.login(identity, cookie, password);
            }
            mPasswords.put(identity.getUsername(), password);
        }
        else {
            SurespotLog.w(TAG, "getIdentity null");
        }
    }

    public synchronized static void setLastUser(final Context context, String username) {
        Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, username);
    }

    public static synchronized void createIdentity(final Context context, final String username, final String password, final String salt,
                                                   final KeyPair keyPairDH, final KeyPair keyPairECDSA, final okhttp3.Cookie cookie) {
        SurespotIdentity identity = new SurespotIdentity(username, salt);
        identity.addKeyPairs("1", keyPairDH, keyPairECDSA);
        saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
        setLoggedInUser(context, identity, cookie, password);
    }

    static void updatePassword(Context context, SurespotIdentity identity, String username, String newPassword, String newSalt) {
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
        }
        else {
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
        }
        catch (FileNotFoundException e) {
            SurespotLog.w(TAG, e, "saveIdentity");
        }
        catch (IOException e) {
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
                NetworkController networkController = NetworkManager.getNetworkController(context, username);
                networkController.validate(username, dPassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword),
                        new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                callback.handleResponse(null, null);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                if (response.isSuccessful()) {
                                    callback.handleResponse(encryptIdentity(identity, password + EXPORT_IDENTITY_ID), null);
                                }
                                else {

                                    switch (response.code()) {
                                        case 403:
                                            callback.handleResponse(null, context.getString(R.string.incorrect_password_or_key));
                                            break;
                                        case 404:
                                            callback.handleResponse(null, context.getString(R.string.incorrect_password_or_key));
                                            break;

                                        default:
                                            SurespotLog.i(TAG, "exportIdentity unexpected http response: %d", response.code());
                                            callback.handleResponse(null, null);
                                    }

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
        }
        else {
            if (exists) {
                return identityFile.isFile() && identityFile.canWrite();
            }
            else {

                try {
                    // make sure we'll have the space to write the identity file
                    FileOutputStream fos = new FileOutputStream(identityFile);
                    fos.write(new byte[10000]);
                    fos.close();
                    identityFile.delete();
                    return true;
                }
                catch (IOException e) {
                    return false;
                }
            }
        }
    }

    private static String getIdentityDir(Context context) {
        String identityDir = FileUtils.getIdentityDir(context);
        return identityDir;
    }

    static synchronized void deleteIdentity(Context context, String deletedUsername, final boolean preserveBackedUpIdentity) {
        // force identity reload
        mHasIdentity = false;

        boolean isLoggedIn = false;
        if (deletedUsername.equals(getLoggedInUser())) {
            isLoggedIn = true;
        }

        CredentialCachingService ccs = SurespotApplication.getCachingService(context);
        if (ccs != null) {
            ccs.clearIdentityData(deletedUsername, true);
        }
        logout(context, deletedUsername, true);
        NetworkManager.getNetworkController(context, deletedUsername).clearCache();

        FileCacheController fcc = SurespotApplication.getFileCacheController();
        if (fcc != null) {
            fcc.clearCache();
        }

        StateController.wipeState(context, deletedUsername);

        synchronized (IDENTITY_FILE_LOCK) {
            String identityFilename = FileUtils.getIdentityDir(context) + File.separator + deletedUsername + IDENTITY_EXTENSION;
            File file = new File(identityFilename);
            file.delete();

            if (!preserveBackedUpIdentity) {
                // delete export identity
                final File exportDir = FileUtils.getIdentityExportDir();

                // could potentially delete the wrong file so don't delete the case insensitive version
                // identityFilename = exportDir + File.separator + username + IDENTITY_EXTENSION;
                // file = new File(identityFilename);
                // file.delete();

                identityFilename = exportDir + File.separator + caseInsensitivize(deletedUsername) + IDENTITY_EXTENSION;
                file = new File(identityFilename);
                file.delete();
            }
        }

        if (isLoggedIn) {
            UIUtils.launchMainActivityDeleted(context);
        }
    }

    static SurespotIdentity getIdentity(Context context, String username) {
        return getIdentity(context, username, null);
    }

    public static SurespotIdentity getIdentity(Context context, String username, String password) {
        if (username == null) {
            return null;
        }

        CredentialCachingService ccs = SurespotApplication.getCachingService(context);

        SurespotIdentity identity = null;
        if (ccs != null) {
            identity = ccs.getIdentity(context, username, password);
        }
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
        }
        else {
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

        }
        catch (Exception e) {
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
        }
        catch (JSONException e) {
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

            NetworkController networkController = NetworkManager.getNetworkController(context, username);
            networkController.validate(finalusername, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), finalusername, dpassword),
                    new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, finalusername),
                                    false));
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String file = saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
                                if (file != null) {
                                    updateKeychainPassword(context, finalusername, password);
                                    CredentialCachingService ccs = SurespotApplication.getCachingService(context);
                                    if (ccs != null) {
                                        ccs.updateIdentity(identity, true);
                                    }
                                    callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully, finalusername),
                                            true));
                                }
                                else {
                                    callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, finalusername),
                                            false));
                                }
                            }
                            else {
                                switch (response.code()) {
                                    case 403:
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.incorrect_password_or_key), false));
                                        break;
                                    case 404:
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.no_such_user), false));
                                        break;

                                    default:
                                        SurespotLog.i(TAG, "importIdentity unexpected HTTP response code: %d", response.code());
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name,
                                                finalusername), false));
                                }
                            }
                        }
                    });
        }
        else {
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

            NetworkController networkController = NetworkManager.getNetworkController(context, username);
            networkController.validate(finalusername, dpassword, EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), finalusername, dpassword),
                    new Callback() {


                        @Override
                        public void onFailure(Call call, IOException e) {
                            callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                    false));
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String file = saveIdentity(context, true, identity, password + CACHE_IDENTITY_ID);
                                if (file != null) {
                                    updateKeychainPassword(context, finalusername, password);
                                    CredentialCachingService ccs = SurespotApplication.getCachingService(context);
                                    if (ccs != null) {
                                        ccs.updateIdentity(identity, true);
                                    }
                                    callback.handleResponse(new IdentityOperationResult(context.getString(R.string.identity_imported_successfully, finalusername),
                                            true));
                                }
                                else {
                                    callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                            false));
                                }
                            }
                            else {
                                switch (response.code()) {
                                    case 403:
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.incorrect_password_or_key), false));
                                        break;
                                    case 404:
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.no_such_user), false));
                                        break;

                                    default:
                                        SurespotLog.i(TAG, "importIdentityBytes unexpected HTTP response code: %d", response.code());
                                        callback.handleResponse(new IdentityOperationResult(context.getString(R.string.could_not_restore_identity_name, username),
                                                false));
                                }
                            }
                        }
                    });

        }
        else {
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
            NetworkManager.getNetworkController(context, username).validate(username, dPassword,
                    EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword), new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            callback.handleResponse(null);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                String path = saveIdentity(null, false, identity, password + EXPORT_IDENTITY_ID);
                                callback.handleResponse(path == null ? null : context.getString(R.string.backed_up_identity_to_path, finalUsername, path));
                            }
                            else {
                                switch (response.code()) {
                                    case 403:
                                        callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
                                        break;
                                    case 404:
                                        callback.handleResponse(context.getString(R.string.incorrect_password_or_key));
                                        break;

                                    default:
                                        SurespotLog.i(TAG, "importIdentity unexpected HTTP response code: %d", response.code());
                                        callback.handleResponse(null);
                                }
                            }
                        }


                    });
        }
        else {
            callback.handleResponse(null);
        }

    }

    private static synchronized String savePublicKeyPair(Context context, String username, String version, String keyPair) {
        try {
            String dir = FileUtils.getPublicKeyDir(context) + File.separator + username;
            if (!FileUtils.ensureDir(dir)) {
                SurespotLog.e(TAG, new RuntimeException("Could not create public key pair dir: %s" + dir), "Could not create public key pair dir: %s", dir);
                return null;
            }

            String pkFile = dir + File.separator + version + PUBLICKEYPAIR_EXTENSION;
            SurespotLog.v(TAG, "saving public key pair: %s", pkFile);

            FileUtils.writeFile(pkFile, keyPair);

            return pkFile;
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "saveIdentity");
        }
        return null;
    }

    private static PublicKeys getPublicKeyPair(Context context, String username, String version, JSONObject jsonKeyPair) {
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

            savePublicKeyPair(context, username, version, json.toString());
            SurespotLog.i(TAG, "loaded public keys from server for username %s", username);
            return new PublicKeys(version, dhPub, dsaPub, new Date().getTime());
        }
        catch (JSONException e) {
            SurespotLog.w(TAG, e, "recreatePublicKeyPair");
        }

        return null;
    }

    public static PublicKeys getPublicKeyPair2(Context context, String ourUsername, String theirUsername, String version) {
        if (context == null) {
            return null;
        }

        int currentVersion = Integer.parseInt(version, 10);
        int wantedVersion = currentVersion;

        PublicKeys keys = null;
        String sDownloadedKeys = null;

        PublicKeys validatedKeys = null;
        int validatedKeyVersion = 0;
        Hashtable<Integer, PublicKey> dsaKeys = new Hashtable<Integer, PublicKey>();
        Hashtable<Integer, PublicKey> dhKeys = new Hashtable<Integer, PublicKey>();
        Hashtable<Integer, JSONObject> resultKeys = new Hashtable<Integer, JSONObject>();


        //attempt to load keys from disk until we have some
        while (currentVersion > 0) {
            String sCurrentVersion = Integer.toString(currentVersion, 10);

            //load keys locally
            //if we have them they've been validated and we can validate any new keys we downloaded
            keys = loadPublicKeyPair(context, theirUsername, sCurrentVersion);
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
        }
        else {
            //get keys from server since the last validated version
            sDownloadedKeys = NetworkManager.getNetworkController(context, ourUsername).getPublicKeysSync(theirUsername, Integer.toString(validatedKeyVersion + 1, 10));

            //validate from the last validated version to the version we want
            if (sDownloadedKeys != null) {
                try {
                    JSONArray json = new JSONArray(sDownloadedKeys);

                    for (int i = 0; i < json.length(); i++) {
                        //build some data structures
                        JSONObject jsonKeys = json.getJSONObject(i);
                        int readVersion = jsonKeys.getInt("version");
                        String spubECDSA = jsonKeys.getString("dsaPub");
                        String spubECDH = jsonKeys.getString("dhPub");
                        PublicKey dsaPub = EncryptionController.recreatePublicKey("ECDSA", spubECDSA);
                        dsaKeys.put(readVersion, dsaPub);
                        PublicKey dhPub = EncryptionController.recreatePublicKey("ECDH", spubECDH);
                        dhKeys.put(readVersion, dhPub);
                        resultKeys.put(readVersion, jsonKeys);
                    }

                    //if we have clientSig, use new validation
                    //otherwise use old (for now until we cut off old version)

                    JSONObject wantedKey = resultKeys.get(wantedVersion);

                    if (wantedKey.has("clientSig2")) {
                        SurespotLog.d(TAG, "Validating username: %s, version: %s, keys using v3 code", theirUsername, version);

                        PublicKey previousDsaKey = null;
                        if (validatedKeys != null) {
                            //if we have a key validated start with that
                            previousDsaKey = validatedKeys.getDSAKey();
                        }
                        else {
                            //otherwise start from ground zero
                            previousDsaKey = dsaKeys.get(1);
                        }
                        //validate in order
                        PublicKey dhPub = null;
                        PublicKey dsaPub = null;

                        String sDhPub = null;
                        String sDsaPub = null;

                        for (int validatingVersion = validatedKeyVersion + 1; validatingVersion <= wantedVersion; validatingVersion++) {

                            dhPub = dhKeys.get(validatingVersion);
                            dsaPub = dsaKeys.get(validatingVersion);

                            sDhPub = new String(ChatUtils.base64EncodeNowrap(dhPub.getEncoded()));
                            sDsaPub = new String(ChatUtils.base64EncodeNowrap(dsaPub.getEncoded()));


                            //validate dh and dsa against server sig
                            boolean verified = EncryptionController.verifySig(
                                    EncryptionController.ServerPublicKey,
                                    resultKeys.get(validatingVersion).getString("serverSig2"),
                                    theirUsername,
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
                                    resultKeys.get(validatingVersion).getString("clientSig2"),
                                    theirUsername,
                                    validatingVersion,
                                    sDhPub,
                                    sDsaPub);

                            if (!verified) {
                                return null;
                            }

                            //save some keys
                            JSONObject jsonKey = resultKeys.get(validatingVersion);
                            savePublicKeyPair(context, theirUsername, String.valueOf(validatingVersion), jsonKey.toString());

                            //get next previous signing key
                            previousDsaKey = dsaKeys.get(validatingVersion);

                        }



                        //savePublicKeyPair(username, version, json.toString());
                        SurespotLog.i(TAG, "loaded and verified public keys from server for username %s", theirUsername);
                        return new PublicKeys(version, dhPub, dsaPub, new Date().getTime());
                    }
                    else {
                        if (wantedKey.has("clientSig")) {
                            SurespotLog.d(TAG, "Validating username: %s, version: %s, keys using v2 code", theirUsername, version);

                            PublicKey previousDsaKey = null;
                            if (validatedKeys != null) {
                                //if we have a key validated start with that
                                previousDsaKey = validatedKeys.getDSAKey();
                            }
                            else {
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
                                        theirUsername,
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
                                        theirUsername,
                                        validatingVersion,
                                        sDhPub,
                                        sDsaPub);

                                if (!verified) {
                                    return null;
                                }

                                //save some keys
                                savePublicKeyPair(context, theirUsername, String.valueOf(validatingVersion), jsonKey.toString());

                                //get next previous signing key
                                previousDsaKey = dsaKeys.get(validatingVersion);

                            }

                            PublicKey dhPub = dhKeys.get(wantedVersion);
                            PublicKey dsaPub = dsaKeys.get(wantedVersion);


                            //savePublicKeyPair(username, version, json.toString());
                            SurespotLog.i(TAG, "loaded and verified public keys from server for username %s", theirUsername);
                            return new PublicKeys(version, dhPub, dsaPub, new Date().getTime());

                        }
                        else {
                            SurespotLog.d(TAG, "Validating username: %s, version: %s, keys using v1 code", theirUsername, version);
                            //TODO need to recheck somehow and eventually get all keys validated using v2 code
                            return getPublicKeyPair(context, theirUsername, version, wantedKey);
                        }
                    }

                }
                catch (JSONException e) {
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
            }
            else {
                SurespotLog.i(TAG, "DH key successfully verified");
            }

            boolean dsaVerify = EncryptionController.verifyPublicKey(sSigECDSA, spubECDSA);
            if (!dsaVerify) {
                // alert alert
                SurespotLog.w(TAG, new KeyException("Could not verify DSA key against server signature."), "could not verify DSA key against server signature");
                return null;
            }
            else {
                SurespotLog.i(TAG, "DSA key successfully verified");
            }

            return jsonKeypair;
        }
        catch (JSONException e) {
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

    public static synchronized void logout(Context context, String username, boolean deleted) {
        ChatController cc = ChatManager.getChatController(username);
        if (cc != null) {
            cc.logout();
        }

        if (!deleted) {
            NetworkManager.getNetworkController(context, username).logout();
        }

        CredentialCachingService cache = SurespotApplication.getCachingService(context);
        if (cache != null) {
            cache.logout(username, deleted);
        }

        clearStoredPasswordForIdentity(context, username);
    }

    public static synchronized void logout(Context context) {
        if (getLoggedInUser() != null) {
            logout(context, getLoggedInUser(), false);
        }
    }

    private synchronized static PublicKeys loadPublicKeyPair(Context context, String username, String version) {

        // try to load identity
        String pkFilename = FileUtils.getPublicKeyDir(context) + File.separator + username + File.separator + version
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

        }
        catch (Exception e) {
            SurespotLog.w(TAG, "loadPublicKeyPair", e);
        }
        return null;

    }

    public static boolean hasIdentity(Context context) {
        if (!mHasIdentity) {
            mHasIdentity = getIdentityNames(context).size() > 0;
        }
        return mHasIdentity;
    }

    public static boolean hasLoggedInUser() {
        return getLoggedInUser() != null;
    }

    public static String getLoggedInUser() {
        return ChatManager.getLoggedInUser();
    }

    public static String getLastLoggedInUser(Context context) {
        return Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER);
    }

    public static Cookie getCookieForUser(Context context, String username) {
        Cookie cookie = null;
        CredentialCachingService service = SurespotApplication.getCachingService(context);
        if (service != null) {

            if (username != null) {
                SurespotLog.d(TAG, "getting cookie for %s", username);

                cookie = service.getCookie(username);

                SurespotLog.d(TAG, "returning cookie: %s", cookie);
            }
        }
        return cookie;

    }

    public static String getTheirLatestVersion(Context context, String ourUsername, String theirUsername) {
        CredentialCachingService cachingService = SurespotApplication.getCachingService(context);
        if (cachingService != null) {
            return cachingService.getLatestVersion(ourUsername, theirUsername);
        }
        return null;
    }

    public static String getOurLatestVersion(Context context, String username) {
        CredentialCachingService cachingService = SurespotApplication.getCachingService(context);
        if (cachingService != null) {
            SurespotIdentity identity = cachingService.getIdentity(context, username, null);
            if (identity != null) {
                return identity.getLatestVersion();
            }
        }

        return null;
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

        CredentialCachingService ccs = SurespotApplication.getCachingService(context);
        if (ccs != null) {
            ccs.updateIdentity(identity, true);
        }
    }

    public static void updateLatestVersion(Context context, String username, String version) {
        // see if we are the user that's been revoked
        boolean sameUser = false;
        if (username.equals(getLoggedInUser())) {
            sameUser = true;
        }

        SurespotLog.d(TAG, "updateLatestVersion, username: %s, version: %s, sameUser: %b", username, version, sameUser);
        //us
        if (sameUser) {
            //if we can't get the latest version we weren't logged in (we couldn't be here if we hadn't received the user control message after logging in)
            //so we couldn't have issued the key roll
            //therefore blow the identity away

            if (Integer.parseInt(version) > Integer.parseInt(getOurLatestVersion(context, username))) {
                SurespotLog.v(TAG, "user revoked, deleting data and logging out");

                // bad news
                // delete the identity file and cached data
                deleteIdentity(context, username, false);

                // boot them out
                launchLoginActivity(context);
            }
        }
        else {
            //not us
            CredentialCachingService ccs = SurespotApplication.getCachingService(context);
            if (ccs != null) {
                ccs.updateLatestVersion(getLoggedInUser(), username, version);
            }
        }
    }

    private static void launchLoginActivity(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra("401", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
            }
            else {
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
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static final boolean IS_JB43 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    private static final boolean IS_JB = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static final boolean IS_KK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    private static final boolean IS_M = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

    public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";

    public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";
    public static final String RESET_ACTION = "com.android.credentials.RESET";

    public static Boolean USE_PUBLIC_KEYSTORE_M = false;

    public static void initKeystore() {
        SurespotLog.d(TAG, "initKeyStore");
        if (mKs == null) {
            if (IS_M) {
                // Experimental:
                USE_PUBLIC_KEYSTORE_M = true; // using org.nick keystoreM for now, flow is not quite right with proper public API yet

                if (!USE_PUBLIC_KEYSTORE_M) {
                    mKs = KeyStoreM.getInstance();
                }
            }
            else if (IS_KK) {
                mKs = KeyStoreKk.getInstance();
            }
            else if (IS_JB43) {
                mKs = KeyStoreJb43.getInstance();
            }
            else {
                mKs = KeyStore.getInstance();
            }
        }
    }

    public static KeyStore getKeystore() {
        return mKs;
    }


    public static void destroyKeystore() {
        if (USE_PUBLIC_KEYSTORE_M) {
            AndroidMKeystoreController.destroyMKeystore();
        }
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static boolean isAndroidMKeystoreSecure(Context context) {
        KeyguardManager mKeyguardManager = (KeyguardManager) context.getSystemService(context.KEYGUARD_SERVICE);
        if (!mKeyguardManager.isKeyguardSecure()) {
            return false;
        }

        return true;
    }

    public static boolean isKeystoreUnlocked(Context context, String username) {
        if (USE_PUBLIC_KEYSTORE_M) {
            if (!isAndroidMKeystoreSecure(context)) {
                // Show a message that the user hasn't set up a lock screen, but let them continue
                Utils.makeLongToast(context, context.getString(R.string.secure_lock_screen_not_set_up));
            }

            try {
                AndroidMKeystoreController.loadEncryptedPassword(context, username, true);
            }
            catch (InvalidKeyException e) {
                // at some point, we may want to use KeyguardManager.createConfirmDeviceCredentialIntent
                // but for now, we're not setting up the key in the keystore so that it must be manually unlocked by the user
            }
            return true;
        }
        if (mKs == null) {
            initKeystore();
        }
        if (mKs == null) {
            return false;
        }
        try {
            if (mKs.state() == KeyStore.State.UNINITIALIZED) {
                // can we do anything about keystore being in uninitialized state?
            }
            return mKs.state() == KeyStore.State.UNLOCKED;
        }
        catch (Exception ex) {
            // fix to #742 - org.nick....KeyStoreKk.state() throwing an exception
            // if we can't access keystore state(), treat it as locked so we don't get an exception
            // higher up the call chain
            return false;
        }
    }

    public static boolean unlock(Context activity) {

        SurespotLog.d(TAG, "unlock");

        if (USE_PUBLIC_KEYSTORE_M) {
            return true;
        }

        if (mKs == null) {
            return false;
        }

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
            //see if we have it in RAM
            String password = mPasswords.get(username);
            if (password != null) {
                return password;
            }

            return getKeyStorePasswordForIdentity(context, username);
        }

        return null;
    }

    public static String getKeyStorePasswordForIdentity(Context context, String username) {
        SurespotLog.d(TAG, "getKeyStorePasswordForIdentity: %s", username);


        if (USE_PUBLIC_KEYSTORE_M) {
            try {
                return AndroidMKeystoreController.loadEncryptedPassword(context, username, false);
            }
            catch (InvalidKeyException e) {
                SurespotLog.d(TAG, "InvalidKeyException loading encrypted password for %s: " + e.getMessage(), username);
                return null;
            }
        }

        if (isKeystoreUnlocked(context, username)) {
            byte[] secret = mKs.get(username);
            if (secret != null) {
                SurespotLog.d(TAG, "getStoredPasswordForIdentity...found password for %s", username);
                return new String(secret);
            }
        }
        else {
            SurespotLog.d(TAG, "getStoredPasswordForIdentity...keystore locked");
        }

        return null;
    }

    public static boolean storePasswordForIdentity(Context activity, String username, String password) throws InvalidKeyException {
        if (activity == null) {
            return false;
        }

        if (isKeystoreUnlocked(activity, username)) {
            if (username != null && password != null) {
                Utils.putSharedPrefsBoolean(activity, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);

                if (USE_PUBLIC_KEYSTORE_M) {
                    AndroidMKeystoreController.saveEncryptedPassword(activity, username, password);
                }
                else {
                    return mKs.put(username, password.getBytes());
                }
            }
        }
        else {
            return unlock(activity);
        }

        return false;
    }

    public static boolean clearStoredPasswordForIdentity(Context context, String username) {
        if (username != null) {
            mPasswords.remove(username);
            if (USE_PUBLIC_KEYSTORE_M) {
                java.security.KeyStore ks = null;
                try {
                    ks = java.security.KeyStore.getInstance("AndroidKeyStore");
                    ks.load(null);
                    ks.deleteEntry(username);
                }
                catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                    e.printStackTrace();
                    SurespotLog.d(TAG, "Error clearing stored password: " + e.getMessage());
                    return false;
                }
                return true;
            }
            if (isKeystoreUnlocked(context, username)) {
                return mKs.delete(username);
            }
        }

        return false;
    }

    private static void updateKeychainPassword(Context context, String username, String password) {
        mPasswords.put(username, password);
        //update stored password if we have one
        String storedPassword = getKeyStorePasswordForIdentity(context, username);
        if (storedPassword != null) {
            try {
                storePasswordForIdentity(context, username, password);
            }
            catch (InvalidKeyException e) {
                Intent intent = new Intent(context, SurespotKeystoreActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                context.startActivity(intent);
            }
        }
    }

    public static JSONObject updateSignatures(Context context, String username) {
        //iterate through all identity public keys and generate new client sigs
        SurespotIdentity identity = getIdentity(context, username);
        String previousVersion = "1";
        PrivateKey previousDSAKey = identity.getKeyPairDSA(previousVersion).getPrivate();
        JSONObject signatures = new JSONObject();
        try {
            int latestVersion = Integer.parseInt(identity.getLatestVersion());
            for (int i = 1; i <= latestVersion; i++) {

                String currentVersion = Integer.toString(i);
                SurespotLog.d(TAG, "Signing version %s with version %s", currentVersion, previousVersion);
                KeyPair dhPair = identity.getKeyPairDH(currentVersion);
                KeyPair dsaPair = identity.getKeyPairDSA(currentVersion);

                //don't use PEM format as this can be different based on the platform which causes problems
                //when importing identity between platforms
                String sDhPub = new String(ChatUtils.base64EncodeNowrap(dhPair.getPublic().getEncoded()));
                String sDsaPub = new String(ChatUtils.base64EncodeNowrap(dsaPair.getPublic().getEncoded()));

                // sign the dh public key, username, and version so clients can validate
                String sig = EncryptionController.sign(previousDSAKey, identity.getUsername(), i, sDhPub, sDsaPub);
                signatures.put(currentVersion, sig);

                //SurespotLog.d(TAG, "signed dh: %s, dsa: %s, sig: %s ", sDhPub, sDsaPub, sig);

                if (i > 1) {
                    previousVersion = Integer.toString(i);
                    previousDSAKey = identity.getKeyPairDSA(previousVersion).getPrivate();
                }
            }
        }
        catch (JSONException e) {
            SurespotLog.e(TAG, e, "error generating update signatures");
        }
        return signatures;
    }
}
