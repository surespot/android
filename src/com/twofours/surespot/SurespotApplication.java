package com.twofours.surespot;

import java.security.Security;

import android.app.Application;
import android.content.Context;

public class SurespotApplication extends Application {
	private static Context context;

	public void onCreate() {
		super.onCreate();

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());

		//TODO froyo only
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

		SurespotApplication.context = getApplicationContext();

	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

}
