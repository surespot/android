package com.twofours.surespot.images;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

import com.twofours.surespot.common.SurespotLog;

public class BitmapLruCache extends LruCache<String, Bitmap> {

	private static final String TAG = "BitmapLruCache";

	// specialized to hold bitmaps so we can call recycle to purge memory on GB devices
	public BitmapLruCache(int maxSize) {
		super(maxSize);
	}

	protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {

		SurespotLog.v(TAG, "entryRemoved, %s", key);
		if (evicted) {
			SurespotLog.v(TAG, "evicted, recycling bitmap");
			oldValue.recycle();
		}
	}
}
