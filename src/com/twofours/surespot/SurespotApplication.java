package com.twofours.surespot;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.Intent;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ")
public class SurespotApplication extends Application {
	private static Intent mStartupIntent;

	public void onCreate() {
		super.onCreate();

		ACRA.init(this);

		

	}
	

	public static Intent getStartupIntent() {
		return mStartupIntent;
	}

	public static void setStartupIntent(Intent startupIntent) {
		mStartupIntent = startupIntent;
	}

}
