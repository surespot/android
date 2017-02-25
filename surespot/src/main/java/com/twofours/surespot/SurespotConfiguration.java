package com.twofours.surespot;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.InputStream;
import java.util.Properties;

public class SurespotConfiguration {
	private final static int IMAGE_DISPLAY_HEIGHT_MULT = 200;
	// private final static int QR_DISPLAY_SIZE = 200;

	private static final String TAG = "Configuration";
	private static Properties mConfigProperties;
	private static boolean mStrictSsl;
	private static String mBaseUrl;


	private static String mGoogleApiLicenseKey;
	private static String mGoogleApiKey;

	private static int mImageDisplayHeight;
	private static int mQRDisplaySize;
	
	private static boolean mBackgroundImageSet;

	public static void LoadConfigProperties(Context context) {
		// Read from the /res/raw directory
		try {
			InputStream rawResource = context.getResources().openRawResource(com.twofours.surespot.R.raw.configuration);
			Properties properties = new Properties();
			properties.load(rawResource);
			mConfigProperties = properties;
			mStrictSsl = SurespotConstants.SSL_STRICT;
			mBaseUrl = SurespotConstants.PRODUCTION ? (String) properties.get("baseUrlProd") : (String) properties.get("baseUrlLocal");
			mGoogleApiLicenseKey = (String) properties.get("googleApiLicenseKey");
			mGoogleApiKey = (String) properties.get("googleApiKey");

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

	public static String getGoogleApiLicenseKey() {
		return mGoogleApiLicenseKey;
	}

	public static String getGoogleApiKey() {
		return mGoogleApiKey;
	}

}
