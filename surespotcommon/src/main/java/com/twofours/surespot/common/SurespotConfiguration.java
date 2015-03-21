package com.twofours.surespot.common;

import java.io.InputStream;
import java.util.Properties;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public class SurespotConfiguration {
	public static final int SSL_NOT_STRICT = 0;
	private final static int IMAGE_DISPLAY_HEIGHT_MULT = 200;
	// private final static int QR_DISPLAY_SIZE = 200;

	private static final String TAG = "Configuration";
	private static Properties mConfigProperties;
	private static boolean mStrictSsl;
	private static String mBaseUrl;

	private static int mImageDisplayHeight;
	private static int mQRDisplaySize;
	
	private static boolean mBackgroundImageSet;

	public static void LoadConfigProperties(Context context) {
		// Read from the /res/raw directory
		try {
			InputStream rawResource = context.getResources().openRawResource(R.raw.configuration);
			Properties properties = new Properties();
			properties.load(rawResource);
			mConfigProperties = properties;
			mStrictSsl = Boolean.parseBoolean((String) properties.get("ssl_strict"));
			mBaseUrl = (String) properties.get("baseUrl");

			// figure out image and QR display size based on screen size
			Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
			DisplayMetrics metrics = new DisplayMetrics();
			display.getMetrics(metrics);

			// if (metrics.densityDpi == DisplayMetrics.DENSITY_XXHIGH) {
			mImageDisplayHeight = (int) (metrics.density * IMAGE_DISPLAY_HEIGHT_MULT);
			mQRDisplaySize = (int) (metrics.density * IMAGE_DISPLAY_HEIGHT_MULT);
			//
			SurespotLog.v(TAG, "density: %f, densityDpi: %d, imageHeight: %d", metrics.density, metrics.densityDpi, mImageDisplayHeight);

			SurespotLog.v(TAG, "ssl_strict: %b", SurespotConfiguration.isSslCheckingStrict());
			SurespotLog.v(TAG, "baseUrl: %s", SurespotConfiguration.getBaseUrl());
		}
		catch (Exception e) {
			SurespotLog.e(TAG, e, "could not load configuration properties");
		}
	}

	public static Properties GetConfigProperties() {
		return mConfigProperties;
	}

	public static boolean isSslCheckingStrict() {
		return mStrictSsl;
	}

	public static String getBaseUrl() {
		return mBaseUrl;
	}

	public static int getImageDisplayHeight() {
		return mImageDisplayHeight;

	}

	public static int getQRDisplaySize() {
		return mQRDisplaySize;
	}
	
	public static void setBackgroundImageSet(boolean set) {
		mBackgroundImageSet = set;
	}
	
	public static boolean isBackgroundImageSet() {
		return mBackgroundImageSet;
	}	
}
