package com.twofours.surespot.services;

import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat.Builder;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.ui.UIUtils;

@SuppressLint("NewApi")
public class CredentialCachingService extends Service {
	private static final String TAG = "CredentialCachingService";

	private final IBinder mBinder = new CredentialCachingBinder();

	private Map<String, SurespotIdentity> mIdentities;
	private Map<String, Cookie> mCookies = new HashMap<String, Cookie>();
	private static String mLoggedInUser;
	private LoadingCache<PublicKeyPairKey, PublicKeys> mPublicIdentities;
	private LoadingCache<SharedSecretKey, byte[]> mSharedSecrets;
	private LoadingCache<String, String> mLatestVersions;

	@Override
	public void onCreate() {
		SurespotLog.i(TAG, "onCreate");

		CacheLoader<PublicKeyPairKey, PublicKeys> keyPairCacheLoader = new CacheLoader<PublicKeyPairKey, PublicKeys>() {

			@Override
			public PublicKeys load(PublicKeyPairKey key) throws Exception {
				PublicKeys keys = IdentityController.getPublicKeyPair2(key.getUsername(), key.getVersion());
				String version = keys.getVersion();

				SurespotLog.v(TAG, "keyPairCacheLoader getting latest version");
				String latestVersion = getLatestVersionIfPresent(key.getUsername());

				if (latestVersion == null || (Integer.parseInt(version) > Integer.parseInt(latestVersion))) {
					SurespotLog.v(TAG, "keyPairCacheLoader setting latestVersion, username: %s, version: %s", key.getUsername(), version);
					mLatestVersions.put(key.getUsername(), version);
				}

				return keys;
			}
		};

		CacheLoader<SharedSecretKey, byte[]> secretCacheLoader = new CacheLoader<SharedSecretKey, byte[]>() {
			@Override
			public byte[] load(SharedSecretKey key) throws Exception {
				SurespotLog.i(TAG, "secretCacheLoader, ourVersion: %s, theirUsername: %s, theirVersion: %s, hashed: %b", key.getOurVersion(), key.getTheirUsername(),
						key.getTheirVersion(), key.getHashed());

				try {
					PublicKey publicKey = mPublicIdentities.get(new PublicKeyPairKey(new VersionMap(key.getTheirUsername(), key.getTheirVersion()))).getDHKey();
					return EncryptionController.generateSharedSecretSync(getIdentity(null, key.getOurUsername(), null).getKeyPairDH(key.getOurVersion())
							.getPrivate(), publicKey, key.getHashed());
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

		CacheLoader<String, String> versionCacheLoader = new CacheLoader<String, String>() {
			@Override
			public String load(String key) throws Exception {

				String version = MainActivity.getNetworkController().getKeyVersionSync(key);
				SurespotLog.v(TAG, "versionCacheLoader: retrieved keyversion from server for username: %s, version: %s", key, version);
				return version;
			}
		};

		mPublicIdentities = CacheBuilder.newBuilder().build(keyPairCacheLoader);
		mSharedSecrets = CacheBuilder.newBuilder().build(secretCacheLoader);
		mLatestVersions = CacheBuilder.newBuilder().build(versionCacheLoader);
		mIdentities = new HashMap<String, SurespotIdentity>(5);

		Notification notification = null;

		// if we're < 4.3 then start foreground service
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			boolean keystoreEnabled = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED);

			// if we're not using the keychain in 4.3+ start as a foreground service
			if (!keystoreEnabled) {

				// if this is the first time using the app don't use foreground service
				boolean alreadyPrevented = Utils.getSharedPrefsBoolean(this, "firstTimePreventedForegroundService");
				if (alreadyPrevented) {
					// in 4.3 and above they decide to fuck us by showing the notification
					// so make the text meaningful at least
					PendingIntent contentIntent = PendingIntent.getActivity(this, SurespotConstants.IntentRequestCodes.BACKGROUND_CACHE_NOTIFICATION,
							new Intent(this, MainActivity.class), 0);
					notification = UIUtils.generateNotification(new Builder(this), contentIntent, getPackageName(), R.drawable.surespot_logo_grey,
							getString(R.string.caching_service_notification_title).toString(), getString(R.string.caching_service_notification_message));
					notification.priority = Notification.PRIORITY_MIN;
				}
				else {
					Utils.putSharedPrefsBoolean(this, "firstTimePreventedForegroundService", true);
				}
			}
		}
		else {
			notification = new Notification(0, null, System.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR;

		}

		if (notification != null) {
			startForeground(SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION, notification);
		}

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

		mLoggedInUser = identity.getUsername();
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
		Date date = new Date();
		Date expire = new Date(date.getTime() - 60 * 60 * 1000);

		// if the cookie expires within the hour make them login again
		if (cookie != null && !cookie.isExpired(expire)) {
			hasCookie = true;
			SurespotLog.d(TAG, "we have non expired cookie");
		}

		boolean sessionSet = hasIdentity && (hasPassword || hasCookie);
		if (sessionSet) {
			mLoggedInUser = username;
			if (hasPassword) {
				Map<SharedSecretKey, byte[]> secrets = SurespotApplication.getStateController().loadSharedSecrets(username, password);
				if (secrets != null) {
					mSharedSecrets.putAll(secrets);
				}
			}
		}
		return sessionSet;
	}

	private void saveSharedSecrets() {
		if (mLoggedInUser != null) {
			String password = getPassword(this, mLoggedInUser);
			if (password != null) {
				Map<SharedSecretKey, byte[]> secrets = mSharedSecrets.asMap();
				SurespotApplication.getStateController().saveSharedSecrets(mLoggedInUser, password, secrets);
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
				this.mPublicIdentities.put(new PublicKeyPairKey(new VersionMap(identity.getUsername(), version)), new PublicKeys(version, identity
						.getKeyPairDH(version).getPublic(), identity.getKeyPairDSA(version).getPublic(), 0));
			}
		}
	}

	public String getLoggedInUser() {
		return mLoggedInUser;
	}

	public Cookie getCookie(String username) {
		Cookie cookie = mCookies.get(username);
		if (cookie == null) {
			// load from disk if we have password
			String password = getPassword(this, username);
			if (password != null) {
				cookie = SurespotApplication.getStateController().loadCookie(username, password);
				if (cookie != null) {
					mCookies.put(username, cookie);
				}
			}
		}
		return cookie;
	}

	public byte[] getSharedSecret(String ourVersion, String theirUsername, String theirVersion, boolean hashed) {
		if (getLoggedInUser() != null) {
			// get the cache for this user
			try {
				return mSharedSecrets.get(new SharedSecretKey(new VersionMap(getLoggedInUser(), ourVersion), new VersionMap(theirUsername, theirVersion), hashed));
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

	public SurespotIdentity getIdentity(Context context) {
		return getIdentity(context, mLoggedInUser, null);
	}

	public SurespotIdentity getIdentity(Context context, String username, String password) {
		SurespotIdentity identity = mIdentities.get(username);
		if (identity == null && context != null) {
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

	public void clearUserData(String username) {
		mLatestVersions.invalidate(username);

		for (PublicKeyPairKey key : mPublicIdentities.asMap().keySet()) {
			if (key.getUsername().equals(username)) {
				SurespotLog.v(TAG, "invalidating public key cache entry for: %s", username);
				mPublicIdentities.invalidate(key);
			}
		}

		for (SharedSecretKey key : mSharedSecrets.asMap().keySet()) {
			if (key.getOurUsername().equals(mLoggedInUser) && key.getTheirUsername().equals(username)) {
				SurespotLog.v(TAG, "invalidating shared secret cache entry for our username: %s, theirusername: %s", mLoggedInUser, username);
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

	public synchronized void logout(boolean deleted) {
		if (mLoggedInUser != null) {
			SurespotLog.i(TAG, "Logging out: %s", mLoggedInUser);

			if (!deleted) {
				saveSharedSecrets();
			}

			clearIdentityData(mLoggedInUser, true);
			mLoggedInUser = null;
		}
	}

	public class CredentialCachingBinder extends Binder {
		public CredentialCachingService getService() {
			return CredentialCachingService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;

	}

	@Override
	public void onDestroy() {
		SurespotLog.i(TAG, "onDestroy");
		saveSharedSecrets();
	}

	/**
	 * NEeds to be called on a thread
	 * 
	 * @param username
	 * @return
	 */

	private synchronized String getLatestVersionIfPresent(String username) {
		return mLatestVersions.getIfPresent(username);
	}

	public synchronized String getLatestVersion(String username) {
		try {
			if (getLoggedInUser() != null) {
				String version = mLatestVersions.get(username);
				SurespotLog.v(TAG, "getLatestVersion, username: %s, version: %s", username, version);
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

	public synchronized void updateLatestVersion(String username, String version) {
		if (username != null && version != null) {
			String latestVersion = getLatestVersionIfPresent(username);
			if (latestVersion == null || (Integer.parseInt(version) > Integer.parseInt(latestVersion))) {
				mLatestVersions.put(username, version);
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
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof VersionMap))
				return false;
			VersionMap other = (VersionMap) obj;
			if (mUsername == null) {
				if (other.mUsername != null)
					return false;
			}
			else
				if (!mUsername.equals(other.mUsername))
					return false;
			if (mVersion == null) {
				if (other.mVersion != null)
					return false;
			}
			else
				if (!mVersion.equals(other.mVersion))
					return false;
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
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof PublicKeyPairKey))
				return false;
			PublicKeyPairKey other = (PublicKeyPairKey) obj;
			if (mVersionMap == null) {
				if (other.mVersionMap != null)
					return false;
			}
			else
				if (!mVersionMap.equals(other.mVersionMap))
					return false;
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

		public boolean getHashed() { return mHashed; }

		@Override
		public int hashCode() {
			int result = mOurVersionMap != null ? mOurVersionMap.hashCode() : 0;
			result = 31 * result + (mTheirVersionMap != null ? mTheirVersionMap.hashCode() : 0);
			result = 31 * result + (mHashed ? 1 : 0);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof SharedSecretKey))
				return false;
			SharedSecretKey other = (SharedSecretKey) obj;
			if (mOurVersionMap == null) {
				if (other.mOurVersionMap != null)
					return false;
			}
			else
				if (!mOurVersionMap.equals(other.mOurVersionMap))
					return false;
			if (mTheirVersionMap == null) {
				if (other.mTheirVersionMap != null)
					return false;
			}
			else
				if (!mTheirVersionMap.equals(other.mTheirVersionMap))
					return false;

			if (mHashed != other.getHashed()) {
				return false;
			}
			return true;
		}
	}

}
