package com.twofours.surespot.services;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
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
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.ui.UIUtils;

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
		SurespotLog.v(TAG, "onCreate");

		// in 4.3 and above they decide to fuck us by showing the notification
		// so make the text meaningful at least
		Notification notification = null;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			PendingIntent contentIntent = PendingIntent.getActivity(this, SurespotConstants.IntentRequestCodes.BACKGROUND_CACHE_NOTIFICATION, new Intent(this,
					MainActivity.class), 0);
			notification = UIUtils.generateNotification(new Builder(this), contentIntent, getPackageName(), R.drawable.surespot_logo_grey,
					getString(R.string.caching_service_notification_title).toString(), getString(R.string.caching_service_notification_message));
		}
		else {
			notification = new Notification(0, null, System.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR;
		}

		startForeground(SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION, notification);

		CacheLoader<PublicKeyPairKey, PublicKeys> keyPairCacheLoader = new CacheLoader<PublicKeyPairKey, PublicKeys>() {

			@Override
			public PublicKeys load(PublicKeyPairKey key) throws Exception {
				PublicKeys keys = IdentityController.getPublicKeyPair(key.getUsername(), key.getVersion());
				String version = keys.getVersion();

				SurespotLog.v(TAG, "keyPairCacheLoader getting latest version");
				String latestVersion = getLatestVersionIfPresent(key.getUsername());

				if (latestVersion == null || version.compareTo(latestVersion) > 0) {
					SurespotLog.v(TAG, "keyPairCacheLoader setting latestVersion, username: %s, version: %s", key.getUsername(), version);
					mLatestVersions.put(key.getUsername(), version);
				}

				return keys;
			}
		};

		CacheLoader<SharedSecretKey, byte[]> secretCacheLoader = new CacheLoader<SharedSecretKey, byte[]>() {
			@Override
			public byte[] load(SharedSecretKey key) throws Exception {
				SurespotLog.v(TAG, "secretCacheLoader, ourVersion: %s, theirUsername: %s, theirVersion: %s", key.getOurVersion(), key.getTheirUsername(),
						key.getTheirVersion());

				try {
					PublicKey publicKey = mPublicIdentities.get(new PublicKeyPairKey(new VersionMap(key.getTheirUsername(), key.getTheirVersion()))).getDHKey();
					return EncryptionController.generateSharedSecretSync(IdentityController.getIdentity(key.getOurUsername()).getKeyPairDH(key.getOurVersion())
							.getPrivate(), publicKey);
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
	}

	public synchronized void login(SurespotIdentity identity, Cookie cookie) {
		SurespotLog.v(TAG, "Logging in: %s", identity.getUsername());
		mLoggedInUser = identity.getUsername();
		this.mCookies.put(identity.getUsername(), cookie);
		updateIdentity(identity);
	}

	public void updateIdentity(SurespotIdentity identity) {
		this.mIdentities.put(identity.getUsername(), identity);
		// add all my identity's public keys to the cache

		Iterator<PrivateKeyPairs> iterator = identity.getKeyPairs().iterator();
		while (iterator.hasNext()) {
			PrivateKeyPairs pkp = iterator.next();
			String version = pkp.getVersion();
			this.mPublicIdentities.put(new PublicKeyPairKey(new VersionMap(identity.getUsername(), version)),
					new PublicKeys(version, identity.getKeyPairDH(version).getPublic(), identity.getKeyPairDSA(version).getPublic(), 0));
		}

	}

	public String getLoggedInUser() {
		return mLoggedInUser;
	}

	public Cookie getCookie(String username) {
		return mCookies.get(username);
	}

	public byte[] getSharedSecret(String ourVersion, String theirUsername, String theirVersion) {
		if (getLoggedInUser() != null) {
			// get the cache for this user
			try {
				return mSharedSecrets.get(new SharedSecretKey(new VersionMap(getLoggedInUser(), ourVersion), new VersionMap(theirUsername, theirVersion)));
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

	public SurespotIdentity getIdentity() {
		return getIdentity(mLoggedInUser);
	}

	public SurespotIdentity getIdentity(String username) {
		return mIdentities.get(username);
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
			if (key.getTheirUsername().equals(username)) {
				SurespotLog.v(TAG, "invalidating shared secret cache entry for: %s", username);
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

	public synchronized void logout() {
		if (mLoggedInUser != null) {
			SurespotLog.v(TAG, "Logging out: %s", mLoggedInUser);
			clearIdentityData(mLoggedInUser, false);
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
		SurespotLog.v(TAG, "onDestroy");
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
			if (latestVersion == null || version.compareTo(latestVersion) > 0) {
				mLatestVersions.put(username, version);
			}
		}
	}

	private class VersionMap {
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
			result = prime * result + getOuterType().hashCode();
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
			if (!getOuterType().equals(other.getOuterType()))
				return false;
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

		private CredentialCachingService getOuterType() {
			return CredentialCachingService.this;
		}

	}

	private class PublicKeyPairKey {
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
			result = prime * result + getOuterType().hashCode();
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
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (mVersionMap == null) {
				if (other.mVersionMap != null)
					return false;
			}
			else
				if (!mVersionMap.equals(other.mVersionMap))
					return false;
			return true;
		}

		private CredentialCachingService getOuterType() {
			return CredentialCachingService.this;
		}

	}

	private class SharedSecretKey {
		private VersionMap mOurVersionMap;
		private VersionMap mTheirVersionMap;

		public SharedSecretKey(VersionMap ourVersionMap, VersionMap theirVersionMap) {
			mOurVersionMap = ourVersionMap;
			mTheirVersionMap = theirVersionMap;
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

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((mOurVersionMap == null) ? 0 : mOurVersionMap.hashCode());
			result = prime * result + ((mTheirVersionMap == null) ? 0 : mTheirVersionMap.hashCode());
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
			if (!getOuterType().equals(other.getOuterType()))
				return false;
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
			return true;
		}

		private CredentialCachingService getOuterType() {
			return CredentialCachingService.this;
		}

	}

}
