package com.twofours.surespot;

import java.security.Security;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Context;

import com.twofours.surespot.common.SurespotConfiguration;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ")
public class SurespotApplication extends Application {
	protected static final String TAG = "SurespotApplication";
	private static Context context;

	public void onCreate() {
		super.onCreate();

		ACRA.init(this);

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		SurespotApplication.context = getApplicationContext();
		SurespotConfiguration.LoadConfigProperties(getAppContext());
	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

}
