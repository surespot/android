package com.twofours.surespot;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import android.util.Log;

public class BitmapCache {
	private LruCache<String, Bitmap> mMemoryCache;
	private final static String TAG = "BitMapCache";

	public BitmapCache() {
		
		
		
	    // Get max available VM memory, exceeding this amount will throw an
	    // OutOfMemory exception. Stored in kilobytes as LruCache takes an
	    // int in its constructor.
	    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

	    // Use 1/8th of the available memory for this memory cache.
	    final int cacheSize = maxMemory / 8;

	    mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
	    	
	        @Override
	        protected int sizeOf(String key, Bitmap bitmap) {
	            // The cache size will be measured in kilobytes rather than
	            // number of items.
	            return (bitmap.getRowBytes() * bitmap.getHeight()) / 1024;
	        }
	    };
	    
	}

	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
	    if (getBitmapFromMemCache(key) == null) {
	        mMemoryCache.put(key, bitmap);
	    }
	}

	public Bitmap getBitmapFromMemCache(String key) {
	    return mMemoryCache.get(key);
	}
	
	public void evictAll() {
		Log.v(TAG,"evicting bitmap cache");
		mMemoryCache.evictAll();
	}
}
