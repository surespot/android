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
	private static Intent mStartupIntent;

	private static final int CORE_POOL_SIZE = 5;
	private static final int MAXIMUM_POOL_SIZE = 20;
	private static final int KEEP_ALIVE = 1;

	private static final ThreadFactory sThreadFactory = new ThreadFactory() {
		private final AtomicInteger mCount = new AtomicInteger(1);

		public Thread newThread(Runnable r) {
			return new Thread(r, "surespot #" + mCount.getAndIncrement());
		}
	};

	private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<Runnable>(CORE_POOL_SIZE);

	/**
	 * An {@link Executor} that can be used to execute tasks in parallel.
	 */
	public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
			TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

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

	public static Intent getStartupIntent() {
		return mStartupIntent;
	}

	public static void setStartupIntent(Intent startupIntent) {
		mStartupIntent = startupIntent;
	}

}
