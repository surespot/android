package com.twofours.surespot;

import java.security.Security;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;

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
		SurespotLog.v(TAG, "starting cache service");
		startService(intent);

		mStateController = new StateController();
	}

	public static CredentialCachingService getCachingService() {

		return mCredentialCachingService;
	}

	public static void setCachingService(CredentialCachingService cachingService) {
		mCredentialCachingService = cachingService;
	}

	public static NetworkController getNetworkController() {
		return mNetworkController;
	}

	public static void setNetworkController(NetworkController networkController) {
		mNetworkController = networkController;
	}

	public static StateController getStateController() {
		return mStateController;
	}

	public static Context getContext() {
		return mContext;
	}

}
