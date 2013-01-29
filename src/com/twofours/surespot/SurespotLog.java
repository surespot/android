package com.twofours.surespot;

import org.acra.ACRA;

import android.util.Log;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

public class SurespotLog {

	public static void w(String tag, String msg) {
		Log.w(tag, msg);

	}

	public static void w(String tag, String msg, Throwable tr) {
		Log.w(tag, msg, tr);

	}

	public static void v(String tag, String msg) {
		Log.v(tag, msg);

	}

	public static void d(String tag, String msg) {
		Log.d(tag, msg);

	}

	public static void e(String tag, String msg, Throwable tr) {
		Log.e(tag, msg, tr);

		if (tr instanceof HttpResponseException) {
			HttpResponseException error = (HttpResponseException) tr;
			int statusCode = error.getStatusCode();

			// no need to report these
			switch (statusCode) {
			case 401:
			case 403:
			case 404:
			case 409:
				return;
			}
		}

		ACRA.getErrorReporter().handleException(tr);

	}

	public static void i(String tag, String msg) {
		Log.i(tag, msg);

	}

}
