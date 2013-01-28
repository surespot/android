package com.twofours.surespot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.Security;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Context;
import android.util.Log;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ") 
public class SurespotApplication extends Application {
	protected static final String TAG = "SurespotApplication";
	private static Context context;

	public void onCreate() {
		super.onCreate();
		
		ACRA.init(this);
			
		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		SurespotApplication.context = getApplicationContext();
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				Log.e(TAG, "ERROR: Uncaught exception: " + ex.toString());
				ex.printStackTrace();
			}
		});
	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

}
