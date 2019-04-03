package com.twofours.surespot;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.twofours.surespot.utils.UIUtils;

import java.io.InputStream;
import java.util.Properties;

public class SurespotConfiguration {
	private final static int IMAGE_DISPLAY_HEIGHT_MULT = 200;
	public final static int MESSAGE_IMAGE_DIMENSION = 800;
	public final static int FRIEND_IMAGE_DIMENSION = 200;

	public final static int MAX_USERNAME_LENGTH = 20;
	public final static int MAX_PASSWORD_LENGTH = 256;
	public final static int SAVE_MESSAGE_MINIMUM = 100;
	public final static int MAX_MESSAGE_LENGTH = 1024;
	public final static int MAX_SEARCH_LENGTH = 128;

	public final static int GIF_SEARCH_RESULT_HEIGHT_DP = 150;

	public final static String DRIVE_IDENTITY_FOLDER = "surespot identity backups";
	// private final static int QR_DISPLAY_SIZE = 200;

	private static final String TAG = "Configuration";
	private static Properties mConfigProperties;
	private static boolean mStrictSsl;
	private static String mBaseUrl;


	private static String mGoogleApiLicenseKey;
	private static String mGoogleApiKey;
	private static String mGiphyApiKey;
	private static String mBitlyToken;

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
			mGiphyApiKey = (String) properties.get("giphyApiKey");
			mBitlyToken = (String) properties.get("bitlyToken");

			// figure out image and QR display size based on screen size
			Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
			DisplayMetrics metrics = new DisplayMetrics();
			display.getMetrics(metrics);

			// if (metrics.densityDpi == DisplayMetrics.DENSITY_XXHIGH) {
			mImageDisplayHeight = (int) UIUtils.pxFromDp(context, IMAGE_DISPLAY_HEIGHT_MULT);
			mQRDisplaySize = (int) UIUtils.pxFromDp(context, IMAGE_DISPLAY_HEIGHT_MULT);
			//
			SurespotLog.v(TAG, "density: %f, densityDpi: %d, imageHeight: %d", metrics.density, metrics.densityDpi, mImageDisplayHeight);

			SurespotLog.v(TAG, "ssl_strict: %b", SurespotConfiguration.isSslCheckingStrict());
			SurespotLog.v(TAG, "baseUrl: %s", SurespotConfiguration.getBaseUrl());
		}
		catch (Exception e) {
			SurespotLog.e(TAG, e, "could not load configuration properties");
		}
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
	public static String getGiphyApiKey() {
		return mGiphyApiKey;
	}
	public static String getBitlyToken() {
		return mBitlyToken;
	}

}
