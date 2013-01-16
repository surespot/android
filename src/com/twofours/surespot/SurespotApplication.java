package com.twofours.surespot;

import java.security.Security;

import android.app.Application;
import android.content.Context;

public class SurespotApplication extends Application {
	private static Context context;

	public void onCreate() {
		super.onCreate();

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		SurespotApplication.context = getApplicationContext();

	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

}
