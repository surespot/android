package com.twofours.surespot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.Security;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class SurespotApplication extends Application {
	protected static final String TAG = "SurespotApplication";
	private static Context context;

	public void onCreate() {
		super.onCreate();

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		SurespotApplication.context = getApplicationContext();
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				Log.e(TAG, "ERROR: Uncaught exception: " + ex.toString());
			}
		});
	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

}
