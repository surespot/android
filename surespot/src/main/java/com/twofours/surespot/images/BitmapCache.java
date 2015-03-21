package com.twofours.surespot.images;

import java.util.ArrayList;

import android.graphics.Bitmap;

import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class BitmapCache {
	private BitmapLruCache mMemoryCache;
	private final static String TAG = "BitMapCache";

	public BitmapCache() {

		// Get max available VM memory, exceeding this amount will throw an
		// OutOfMemory exception. Stored in kilobytes as LruCache takes an
		// int in its constructor.
		// final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

		// Use 1/8th of the available memory for this memory cache.
		// final int cacheSize = maxMemory / 2;

		mMemoryCache = new BitmapLruCache(30);
		// {
		//
		// @Override
		// protected int sizeOf(String key, Bitmap bitmap) {
		// // The cache size will be measured in kilobytes rather than
		// // number of items.
		// return (bitmap.getRowBytes() * bitmap.getHeight()) / 1024;
		// }
		// };

	}

	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		String md5Key = Utils.md5(key);
		mMemoryCache.put(md5Key, bitmap);
	}

	public Bitmap getBitmapFromMemCache(String key) {
		String md5Key = Utils.md5(key);
		return mMemoryCache.get(md5Key);
	}

	public void evictAll() {
		SurespotLog.v(TAG, "evicting bitmap cache");
		mMemoryCache.evictAll();
	}

	public void remove(String key) {
		String md5Key = Utils.md5(key);
		mMemoryCache.remove(md5Key);
	}

	public void evictExcept(ArrayList<Bitmap> preserve) {
		mMemoryCache.evictExcept(preserve);

	}

	public void trimToSize(int size) {
		mMemoryCache.trimToSize(size);
		
	}
}
