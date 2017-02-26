package com.twofours.surespot.gifs;

import android.util.LruCache;

import pl.droidsonroids.gif.GifDrawable;

public class GifLruCache extends LruCache<String, GifDrawable> {
	public GifLruCache(int maxSize) {
		super(maxSize);
	}
}
