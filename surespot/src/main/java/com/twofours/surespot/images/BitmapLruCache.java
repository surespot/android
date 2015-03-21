package com.twofours.surespot.images;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.util.LruCache;

import com.twofours.surespot.common.SurespotLog;

public class BitmapLruCache extends LruCache<String, Bitmap> {

	private static final String TAG = "BitmapLruCache";
	private ArrayList<Bitmap> mEvictionExceptions;

	// specialized to hold bitmaps so we can call recycle to purge memory on GB devices
	public BitmapLruCache(int maxSize) {
		super(maxSize);
	}

	protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {

		SurespotLog.v(TAG, "entryRemoved, %s", key);
		if (evicted && (mEvictionExceptions == null || !mEvictionExceptions.contains(oldValue)) && Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			SurespotLog.v(TAG, "evicted, recycling bitmap");
			oldValue.recycle();
		}
	}

	public void evictExcept(ArrayList<Bitmap> preserve) {
		mEvictionExceptions = preserve;
		evictAll();
		mEvictionExceptions = null;
	}
}
