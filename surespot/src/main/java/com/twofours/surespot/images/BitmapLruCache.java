package com.twofours.surespot.images;

import android.graphics.Bitmap;
import android.util.LruCache;

public class BitmapLruCache extends LruCache<String, Bitmap> {
	public BitmapLruCache(int maxSize) {
		super(maxSize);
	}
}
