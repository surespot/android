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
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Intent;

import com.google.android.gcm.GCMRegistrar;
import com.twofours.surespot.chat.EmojiParser;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.services.CredentialCachingService;

@ReportsCrashes(
mode = ReportingInteractionMode.DIALOG,
formKey = "", // will not be used
formUri = "https://www.surespot.me:3000/logs/surespot",
resToastText = R.string.crash_toast_text,
resDialogText = R.string.crash_dialog_text,
resDialogOkToast = R.string.crash_dialog_ok_toast,
resDialogCommentPrompt = R.string.crash_dialog_comment_prompt) // optional
public class SurespotApplication extends Application {
	private static final String TAG = "SurespotApplication";
	private static CredentialCachingService mCredentialCachingService;
	private static StateController mStateController = null;

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

//		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
//
//			@Override
//			public void uncaughtException(Thread thread, Throwable ex) {
//
//				StringWriter stackTrace = new StringWriter();
//				ex.printStackTrace(new PrintWriter(stackTrace));
//				System.err.println(stackTrace);
//
//				new Thread() {
//					@Override
//					public void run() {
//						Looper.prepare();
//						Toast.makeText(SurespotApplication.this, "surespot just crashed. :(", Toast.LENGTH_SHORT).show();
//						Looper.loop();
//					};
//				}.start();
//				
//				
//				System.exit(1);
//
//			}
//		});

//		String lastUser = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.LAST_USER);
//		if (lastUser != null) {
//			SurespotLog.v(TAG, "using shared prefs for user %s for ACRA", lastUser);
//			ACRAConfiguration config = ACRA.getNewDefaultConfig(this);
//			config.setSharedPreferenceName(lastUser);
//			config.setSharedPreferenceMode(Context.MODE_PRIVATE);
//			ACRA.setConfig(config);
//
//		}
		//
		// boolean enableACRA = ACRA.getACRASharedPreferences().getBoolean(ACRA.PREF_ENABLE_ACRA, false);
		// if (!enableACRA) {
		//
		// }

		ACRA.init(this);
		EmojiParser.init(this);
				
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
