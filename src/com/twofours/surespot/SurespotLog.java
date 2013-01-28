package com.twofours.surespot;

import org.acra.ACRA;

import android.util.Log;

public class SurespotLog {

	public static void w(String tag, String msg) {
		Log.w(tag, msg);

	}

	public static void v(String tag, String msg) {
		Log.v(tag, msg);

	}

	public static void d(String tag, String msg) {
		Log.v(tag, msg);

	}

	public static void e(String tag, String msg, Throwable tr) {
		Log.v(tag, msg);
		ACRA.getErrorReporter().handleException(tr);

	}

	public static void i(String tag, String msg) {
		Log.v(tag, msg);

	}

}
