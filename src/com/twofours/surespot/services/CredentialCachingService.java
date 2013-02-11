package com.twofours.surespot.services;

import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;

public class CredentialCachingService extends Service {
	private final IBinder mBinder = new CredentialCachingBinder();

	private Map<String, String> mPasswords = new HashMap<String, String>();
	private Map<String, Cookie> mCookies = new HashMap<String, Cookie>();

	@Override
	public void onCreate() {
		Notification notification = new Notification(R.drawable.ic_launcher, "surespot", System.currentTimeMillis());
		Intent notificationIntent = new Intent(this, CredentialCachingService.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION,
				notificationIntent, 0);
		notification.setLatestEventInfo(this, "surespot", "caching credentials", pendingIntent);
		startForeground(SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION, notification);

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;

	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;

	}

	public String getPassword(String username) {
		return mPasswords.get(username);
	}

	public void setPasswordAndCookie(String username, String password, Cookie cookie) {
		this.mPasswords.put(username, password);
		this.mCookies.put(username, cookie);
	}

	public Cookie getCookie(String username) {
		return mCookies.get(username);
	}

	public void clear(String username) {
		mPasswords.remove(username);
		mCookies.remove(username);

	}

	public class CredentialCachingBinder extends Binder {
		public CredentialCachingService getService() {
			return CredentialCachingService.this;
		}
	}

}
