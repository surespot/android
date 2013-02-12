package com.twofours.surespot;

import java.security.Security;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ")
public class SurespotApplication extends Application {

	protected static final String TAG = "SurespotApplication";
	private static CredentialCachingService mCredentialCachingService;
	private static NetworkController mNetworkController;
	private static StateController mStateController;
	private static Context mContext;

	public void onCreate() {
		super.onCreate();

		ACRA.init(this);

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		mContext = getApplicationContext();

		SurespotConfiguration.LoadConfigProperties(mContext);

		Intent intent = new Intent(this, CredentialCachingService.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {

			CredentialCachingBinder binder = (CredentialCachingBinder) service;
			mCredentialCachingService = binder.getService();
			mNetworkController = new NetworkController();
			mStateController = new StateController();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	public static CredentialCachingService getCachingService() {

		return mCredentialCachingService;
	}

	public static NetworkController getNetworkController() {
		return mNetworkController;
	}

	public static StateController getStateController() {
		return mStateController;
	}

	public static Context getContext() {
		return mContext;
	}

}
