package com.twofours.surespot;

import java.security.Security;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Intent;

import com.google.android.gcm.GCMRegistrar;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.services.CredentialCachingService;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ")
public class SurespotApplication extends Application {
	private static final String TAG = "SurespotApplication";
	private static CredentialCachingService mCredentialCachingService;
	private static StateController mStateController = null;

	public void onCreate() {
		super.onCreate();
		ACRA.init(this);

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		SurespotConfiguration.LoadConfigProperties(getApplicationContext());
		mStateController = new StateController();
		try {
			// device without GCM throws exception
			GCMRegistrar.checkDevice(this);
			GCMRegistrar.checkManifest(this);

			// final String regId = GCMRegistrar.getRegistrationId(this);
			boolean registered = GCMRegistrar.isRegistered(this);
			boolean registeredOnServer = GCMRegistrar.isRegisteredOnServer(this);
			if (!registered || !registeredOnServer) {
				SurespotLog.v(TAG, "Registering for GCM.");
				GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
			}
			else {
				SurespotLog.v(TAG, "GCM already registered.");
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "onCreate", e);
		}

		// NetworkController.unregister(this, regId);

		SurespotLog.v(TAG, "starting cache service");
		Intent cacheIntent = new Intent(this, CredentialCachingService.class);

		startService(cacheIntent);
	}

	public static CredentialCachingService getCachingService() {
		return mCredentialCachingService;
	}

	public static void setCachingService(CredentialCachingService credentialCachingService) {
		SurespotApplication.mCredentialCachingService = credentialCachingService;
	}

	public static StateController getStateController() {
		return mStateController;
	}

}
