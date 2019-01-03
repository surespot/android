package com.twofours.surespot.services;

import android.content.Context;
import android.text.TextUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.Tuple;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkManager;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import okhttp3.Cookie;


public class CredentialCachingService {
    private static final String TAG = "CredentialCachingService";

    private Map<String, SurespotIdentity> mIdentities;
    private Map<String, Cookie> mCookies = new HashMap<String, Cookie>();
    private LoadingCache<Tuple<String, PublicKeyPairKey>, PublicKeys> mPublicIdentities;
    private LoadingCache<SharedSecretKey, byte[]> mSharedSecrets;
    private LoadingCache<Tuple<String, String>, String> mLatestVersions;

    private Context mContext;

    public CredentialCachingService(Context context) {
        mContext = context;

        SurespotLog.i(TAG, "constructor");

        CacheLoader<Tuple<String, PublicKeyPairKey>, PublicKeys> keyPairCacheLoader = new CacheLoader<Tuple<String, PublicKeyPairKey>, PublicKeys>() {

            @Override
            public PublicKeys load(Tuple<String, PublicKeyPairKey> key) throws Exception {
                PublicKeys keys = IdentityController.getPublicKeyPair2(mContext, key.first, key.second.getUsername(), key.second.getVersion());
                String version = keys.getVersion();

                SurespotLog.v(TAG, "keyPairCacheLoader getting latest version");
                String latestVersion = getLatestVersionIfPresent(key.first, key.second.getUsername());

                if (latestVersion == null || (Integer.parseInt(version) > Integer.parseInt(latestVersion))) {
                    SurespotLog.v(TAG, "keyPairCacheLoader setting latestVersion, username: %s, version: %s", key.second.getUsername(), version);
                    mLatestVersions.put(new Tuple<String, String>(key.first, key.second.getUsername()), version);
                }

                return keys;
            }
        };

        CacheLoader<SharedSecretKey, byte[]> secretCacheLoader = new CacheLoader<SharedSecretKey, byte[]>() {
            @Override
            public byte[] load(SharedSecretKey key) throws Exception {
                SurespotLog.i(TAG, "secretCacheLoader, ourUsername: %s, ourVersion: %s, theirUsername: %s, theirVersion: %s, hashed: %b", key.getOurUsername(), key.getOurVersion(), key.getTheirUsername(),
                        key.getTheirVersion(), key.getHashed());

                try {
                    PublicKey publicKey = mPublicIdentities.get(new Tuple<String, PublicKeyPairKey>(key.getOurUsername(), new PublicKeyPairKey(new VersionMap(key.getTheirUsername(), key.getTheirVersion())))).getDHKey();
                    byte[] secret = EncryptionController.generateSharedSecretSync(getIdentity(mContext, key.getOurUsername(), null).getKeyPairDH(key.getOurVersion())
                            .getPrivate(), publicKey, key.getHashed());

                    saveSharedSecrets(key.getOurUsername());
                    return secret;
                }
                catch (InvalidCacheLoadException e) {
                    SurespotLog.w(TAG, e, "secretCacheLoader");
                }
                catch (ExecutionException e) {
                    SurespotLog.w(TAG, e, "secretCacheLoader");
                }

                return null;
            }
        };

        CacheLoader<Tuple<String, String>, String> versionCacheLoader = new CacheLoader<Tuple<String, String>, String>() {
            @Override
            public String load(Tuple<String, String> identityAndFriendname) throws Exception {
                //get the network controller for the identity
                NetworkController nc = NetworkManager.getNetworkController(mContext, identityAndFriendname.first);
                String version = nc.getKeyVersionSync(identityAndFriendname.second);
                SurespotLog.d(TAG, "versionCacheLoader: retrieved keyversion from server for username: %s, version: %s", identityAndFriendname.second, version);
                return version;
            }
        };

        mPublicIdentities = CacheBuilder.newBuilder().build(keyPairCacheLoader);
        mSharedSecrets = CacheBuilder.newBuilder().build(secretCacheLoader);
        mLatestVersions = CacheBuilder.newBuilder().build(versionCacheLoader);
        mIdentities = new HashMap<String, SurespotIdentity>(5);


    }

    public synchronized void login(SurespotIdentity identity, Cookie cookie, String password) {
        SurespotLog.i(TAG, "Logging in: %s", identity.getUsername());

        // load cache data from disk
        if (password != null) {

            Map<SharedSecretKey, byte[]> secrets = SurespotApplication.getStateController().loadSharedSecrets(identity.getUsername(), password);
            if (secrets != null) {
                mSharedSecrets.putAll(secrets);
            }

            // save cookie
            SurespotApplication.getStateController().saveCookie(identity.getUsername(), password, cookie);
        }

        this.mCookies.put(identity.getUsername(), cookie);

        updateIdentity(identity, false);
    }

    private String getPassword(Context context, String username) {
        String password = IdentityController.getStoredPasswordForIdentity(context, username);
        return password;
    }

    public boolean setSession(Context context, String username) {
        SurespotLog.d(TAG, "setSession: %s", username);

        // need identity + cookie or password
        // see if we have the identity
        SurespotIdentity identity = getIdentity(context, username, null);
        boolean hasIdentity = identity != null;

        SurespotLog.d(TAG, "hasIdentity: %b", hasIdentity);

        String password = getPassword(context, username);
        boolean hasPassword = password != null;

        boolean hasCookie = false;
        Cookie cookie = getCookie(username);

        if (cookie != null) {
            hasCookie = true;
            SurespotLog.d(TAG, "we have cookie");
        }

        boolean sessionSet = hasIdentity && (hasPassword || hasCookie);
        if (sessionSet) {

            if (hasPassword) {
                StateController sc = SurespotApplication.getStateController();
                if (sc != null) {
                    Map<SharedSecretKey, byte[]> secrets = sc.loadSharedSecrets(username, password);
                    if (secrets != null) {
                        SurespotLog.d(TAG, "setSession loaded %d shared secrets for %s", secrets.size(), username);
                        mSharedSecrets.putAll(secrets);
                    }
                }
            }
        }
        return sessionSet;
    }

    private void saveSharedSecrets(String username) {
        if (!TextUtils.isEmpty(username)) {
            String password = getPassword(mContext, username);
            if (!TextUtils.isEmpty(password)) {

                Map<SharedSecretKey, byte[]> secrets = mSharedSecrets.asMap();
                SurespotLog.d(TAG, "saveSharedSecrets, username: %s, count: %d", username, secrets.size());
                SurespotApplication.getStateController().saveSharedSecrets(username, password, secrets);
            }
        }
    }

    public void updateIdentity(SurespotIdentity identity, boolean onlyIfExists) {
        boolean update = mIdentities.containsKey(identity.getUsername()) || !onlyIfExists;
        if (update) {
            SurespotLog.d(TAG, "updating identity: %s", identity.getUsername());
            this.mIdentities.put(identity.getUsername(), identity);
            // add all my identity's public keys to the cache

            Iterator<PrivateKeyPairs> iterator = identity.getKeyPairs().iterator();
            while (iterator.hasNext()) {
                PrivateKeyPairs pkp = iterator.next();
                String version = pkp.getVersion();

                this.mPublicIdentities.put(new Tuple<String, PublicKeyPairKey>(
                                identity.getUsername(), new PublicKeyPairKey(new VersionMap(identity.getUsername(), version))),
                        new PublicKeys(version, identity.getKeyPairDH(version).getPublic(), identity.getKeyPairDSA(version).getPublic(), 0));
            }
        }
    }


    public Cookie getCookie(String username) {
        Cookie cookie = mCookies.get(username);
        if (cookie == null) {
            // load from disk if we have password
            String password = getPassword(mContext, username);
            if (password != null) {
                StateController stateController = SurespotApplication.getStateController();
                if (stateController != null) {
                    cookie = stateController.loadCookie(username, password);
                    if (cookie != null) {
                        mCookies.put(username, cookie);
                    }
                }
            }
        }
        return cookie;
    }

    public byte[] getSharedSecret(String ourUsername, String ourVersion, String theirUsername, String theirVersion, boolean hashed) {
        if (ourUsername != null) {
            // get the cache for this user
            try {
                return mSharedSecrets.get(new SharedSecretKey(new VersionMap(ourUsername, ourVersion), new VersionMap(theirUsername, theirVersion), hashed));
            }
            catch (InvalidCacheLoadException e) {
                SurespotLog.w(TAG, e, "getSharedSecret");
            }
            catch (ExecutionException e) {
                SurespotLog.w(TAG, e, "getSharedSecret");
            }
        }
        return null;

    }

    public SurespotIdentity getIdentity(Context context, String username, String password) {
        SurespotIdentity identity = mIdentities.get(username);
        if (context == null) {
            context = mContext;
        }
        if (identity == null) {
            // if we have the password load it
            if (password == null) {
                password = getPassword(context, username);
            }
            if (password != null) {
                identity = IdentityController.loadIdentity(context, username, password);
                if (identity != null) {
                    updateIdentity(identity, false);
                }
            }
        }
        return identity;
    }

    public void clearUserData(String ourUsername, String theirUsername) {
        mLatestVersions.invalidate(theirUsername);


        for (Tuple<String, PublicKeyPairKey> key : mPublicIdentities.asMap().keySet()) {
            if (key.first.equals(ourUsername) && key.second.getUsername().equals(theirUsername)) {
                SurespotLog.v(TAG, "invalidating public key cache entry for: %s", theirUsername);
                mPublicIdentities.invalidate(key);
            }
        }

        for (SharedSecretKey key : mSharedSecrets.asMap().keySet()) {
            if (key.getOurUsername().equals(ourUsername) && key.getTheirUsername().equals(theirUsername)) {
                SurespotLog.v(TAG, "invalidating shared secret cache entry for our username: %s, theirusername: %s", ourUsername, theirUsername);
                mSharedSecrets.invalidate(key);
            }
        }
    }

    public synchronized void clear() {
        mPublicIdentities.invalidateAll();
        mSharedSecrets.invalidateAll();
        mLatestVersions.invalidateAll();
        mCookies.clear();
        mIdentities.clear();
    }

    public synchronized void clearIdentityData(String username, boolean fully) {
        mCookies.remove(username);
        mIdentities.remove(username);

        if (fully) {
            for (SharedSecretKey key : mSharedSecrets.asMap().keySet()) {
                if (key.getOurUsername().equals(username)) {
                    mSharedSecrets.invalidate(key);
                }
            }
        }
    }

    public synchronized void logout(String username, boolean deleted) {
        SurespotLog.i(TAG, "Logging out: %s", username);

        if (!deleted) {
            saveSharedSecrets(username);
        }

        clearIdentityData(username, true);
    }

    private synchronized String getLatestVersionIfPresent(String ourUsername, String theirUsername) {
        return mLatestVersions.getIfPresent(new Tuple<String, String>(ourUsername, theirUsername));
    }

    public synchronized String getLatestVersion(String ourUsername, String theirUsername) {
        try {
            if (ourUsername != null) {
                String version = mLatestVersions.get(new Tuple<String, String>(ourUsername, theirUsername));
                SurespotLog.v(TAG, "getLatestVersion, username: %s, version: %s", theirUsername, version);
                return version;
            }
        }
        catch (InvalidCacheLoadException e) {
            SurespotLog.w(TAG, e, "getLatestVersion");
        }
        catch (ExecutionException e) {
            SurespotLog.w(TAG, e, "getLatestVersion");
        }
        return null;
    }

    public synchronized void updateLatestVersion(String ourUsername, String theirUsername, String version) {
        if (theirUsername != null && version != null) {
            String latestVersion = getLatestVersionIfPresent(ourUsername, theirUsername);
            if (latestVersion == null || (Integer.parseInt(version) > Integer.parseInt(latestVersion))) {
                mLatestVersions.put(new Tuple<String, String>(ourUsername, theirUsername), version);
            }
        }
    }

    public static class VersionMap {
        private String mUsername;
        private String mVersion;

        public VersionMap(String username, String version) {
            mUsername = username;
            mVersion = version;
        }

        public String getUsername() {
            return mUsername;
        }

        public String getVersion() {
            return mVersion;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mUsername == null) ? 0 : mUsername.hashCode());
            result = prime * result + ((mVersion == null) ? 0 : mVersion.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof VersionMap)) {
                return false;
            }
            VersionMap other = (VersionMap) obj;
            if (mUsername == null) {
                if (other.mUsername != null) {
                    return false;
                }
            }
            else if (!mUsername.equals(other.mUsername)) {
                return false;
            }
            if (mVersion == null) {
                if (other.mVersion != null) {
                    return false;
                }
            }
            else if (!mVersion.equals(other.mVersion)) {
                return false;
            }
            return true;
        }
    }

    private static class PublicKeyPairKey {
        private VersionMap mVersionMap;

        public PublicKeyPairKey(VersionMap versionMap) {
            mVersionMap = versionMap;
        }

        public String getUsername() {
            return mVersionMap.getUsername();
        }

        public String getVersion() {
            return mVersionMap.getVersion();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mVersionMap == null) ? 0 : mVersionMap.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof PublicKeyPairKey)) {
                return false;
            }
            PublicKeyPairKey other = (PublicKeyPairKey) obj;
            if (mVersionMap == null) {
                if (other.mVersionMap != null) {
                    return false;
                }
            }
            else if (!mVersionMap.equals(other.mVersionMap)) {
                return false;
            }
            return true;
        }
    }

    public static class SharedSecretKey {
        private VersionMap mOurVersionMap;
        private VersionMap mTheirVersionMap;
        private boolean mHashed;

        public SharedSecretKey(VersionMap ourVersionMap, VersionMap theirVersionMap, boolean hashed) {
            mOurVersionMap = ourVersionMap;
            mTheirVersionMap = theirVersionMap;
            mHashed = hashed;
        }

        public String getOurUsername() {
            return mOurVersionMap.getUsername();
        }

        public String getOurVersion() {
            return mOurVersionMap.getVersion();
        }

        public String getTheirUsername() {
            return mTheirVersionMap.getUsername();
        }

        public String getTheirVersion() {
            return mTheirVersionMap.getVersion();
        }

        public boolean getHashed() {
            return mHashed;
        }

        @Override
        public int hashCode() {
            int result = mOurVersionMap != null ? mOurVersionMap.hashCode() : 0;
            result = 31 * result + (mTheirVersionMap != null ? mTheirVersionMap.hashCode() : 0);
            result = 31 * result + (mHashed ? 1 : 0);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof SharedSecretKey)) {
                return false;
            }
            SharedSecretKey other = (SharedSecretKey) obj;
            if (mOurVersionMap == null) {
                if (other.mOurVersionMap != null) {
                    return false;
                }
            }
            else if (!mOurVersionMap.equals(other.mOurVersionMap)) {
                return false;
            }
            if (mTheirVersionMap == null) {
                if (other.mTheirVersionMap != null) {
                    return false;
                }
            }
            else if (!mTheirVersionMap.equals(other.mTheirVersionMap)) {
                return false;
            }

            if (mHashed != other.getHashed()) {
                return false;
            }
            return true;
        }
    }

}
