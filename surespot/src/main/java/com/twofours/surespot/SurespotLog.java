package com.twofours.surespot;

import android.util.Log;

public class SurespotLog {
	private static boolean mLogging = SurespotConstants.LOGGING;

	public static void setLogging(boolean logging) {
		v("SurespotLog", "setting logging to: %b", logging);
		mLogging = logging;
	}


	public static void v(String tag, String msg) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.v(tag, msg);
		}

	}

	public static void v(String tag, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.v(tag, String.format(msg, msgArgs));
		}

	}

	public static void v(String tag, Throwable tr, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.v(tag, String.format(msg, msgArgs), tr);
		}
	}

	public static void d(String tag, String msg) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.d(tag, msg);
		}

	}


	public static void d(String tag, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.d(tag, String.format(msg, msgArgs));
		}

	}


	public static void i(String tag, String msg) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.i(tag, msg);
		}
	}


	public static void i(String tag, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.i(tag, String.format(msg, msgArgs));
		}
	}

	public static void i(String tag, Throwable tr, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.i(tag, String.format(msg, msgArgs), tr);
		}
	}

	public static void w(String tag, String msg) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.w(tag, msg);
		}
	}

	// by using string.format we avoid string concat overhead when logging is disabled
	public static void w(String tag, String msg, Object... msgArgs) {
		if (mLogging) {
			if (msg == null) msg = "";
			Log.w(tag, String.format(msg, msgArgs));
		}
	}

	public static void w(String tag, Throwable tr, String msg, Object... msgArgs) {
		String message = null;
		if (mLogging) {
			if (msg == null) msg = "";
			message = String.format(msg, msgArgs);
			// Log.w(tag, msg +", " + tr.getMessage());
			Log.w(tag, message, tr);
		}
	}

	public static void e(String tag, Throwable tr, String msg, Object... msgArgs) {
		String message = null;
		if (mLogging) {
			if (msg == null) msg = "";
			message = String.format(msg, msgArgs);
			Log.e(tag, message, tr);
		}

//		if (tr instanceof HttpResponseException) {
//			HttpResponseException error = (HttpResponseException) tr;
//			int statusCode = error.getStatusCode();
//
//			// no need to report these
//			switch (statusCode) {
//			case 400:
//			case 401:
//			case 403:
//			case 404:
//			case 409:
//				return;
//			}
//		}
	}




	public static boolean isLogging() {
		return mLogging;
	}
}
