package com.twofours.surespot;

import java.security.Security;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.app.backup.BackupManager;
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
	public static BackupManager mBackupManager;
	
	public static final int CORE_POOL_SIZE = 16;
	public static final int MAXIMUM_POOL_SIZE = Integer.MAX_VALUE;
	public static final int KEEP_ALIVE = 1;

	// create our own thread factory to handle message decryption where we have potentially hundreds of messages to decrypt
	// we need a tall queue and a slim pipe
	public static final ThreadFactory sThreadFactory = new ThreadFactory() {
		private final AtomicInteger mCount = new AtomicInteger(1);

		public Thread newThread(Runnable r) {
			return new Thread(r, "surespot #" + mCount.getAndIncrement());
		}
	};

	public static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<Runnable>();

	/**
	 * An {@link Executor} that can be used to execute tasks in parallel.
	 */
	public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
			TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

	public void onCreate() {
		super.onCreate();
		ACRA.init(this);
		
		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		mBackupManager = new BackupManager(this);
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
