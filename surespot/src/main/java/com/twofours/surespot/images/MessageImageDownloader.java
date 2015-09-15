/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twofours.surespot.images;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.ui.UIUtils;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * 
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * 
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class MessageImageDownloader {
	private static final String TAG = "MessageImageDownloader";
	private static BitmapCache mBitmapCache = new BitmapCache();
	private static Handler mHandler = new Handler(MainActivity.getContext().getMainLooper());
	private ChatAdapter mChatAdapter;
	private static HashMap<ImageView, Object> mImageViews;

	static {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mImageViews = new HashMap<ImageView, Object>();
		}
	}

	public MessageImageDownloader(ChatAdapter chatAdapter) {
		mChatAdapter = chatAdapter;
	}

	public void download(ImageView imageView, SurespotMessage message) {
		Bitmap bitmap = getBitmapFromCache(message.getData());

		// keep a handle on the image view so we can purge the bitmap later
		if (mImageViews != null) {
			mImageViews.put(imageView, null);
		}

		if (bitmap == null) {
			SurespotLog.v(TAG, "bitmap not in cache: " + message.getData());
			forceDownload(imageView, message);
		}
		else {
			SurespotLog.v(TAG, "loading bitmap from cache: " + message.getData());
			cancelPotentialDownload(imageView, message);
			imageView.clearAnimation();
			imageView.setImageBitmap(bitmap);
			message.setLoaded(true);
			message.setLoading(false);

			UIUtils.updateDateAndSize(message, (View) imageView.getParent());

		}
	}

	/*
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

	/**
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
	 */
	private void forceDownload(ImageView imageView, SurespotMessage message) {
		if (cancelPotentialDownload(imageView, message)) {
			BitmapDownloaderTask task = new BitmapDownloaderTask(imageView, message);
			DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task, SurespotConfiguration.getImageDisplayHeight());
			imageView.setImageDrawable(downloadedDrawable);
			message.setLoaded(false);
			message.setLoading(true);
			SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
		}
	}

	/**
	 * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
	 * progress deals with the same url. The download is not stopped in that case.
	 */
	private boolean cancelPotentialDownload(ImageView imageView, SurespotMessage message) {
		BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

		if (bitmapDownloaderTask != null) {
			SurespotMessage taskMessage = bitmapDownloaderTask.mMessage;
			if ((taskMessage == null) || (!taskMessage.equals(message))) {
				bitmapDownloaderTask.cancel();
			}
			else {
				// The same URL is already being downloaded.
				return false;
			}
		}
		return true;
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
	 */
	public BitmapDownloaderTask getBitmapDownloaderTask(ImageView imageView) {
		if (imageView != null) {
			Drawable drawable = imageView.getDrawable();
			if (drawable instanceof DownloadedDrawable) {
				DownloadedDrawable downloadedDrawable = (DownloadedDrawable) drawable;
				return downloadedDrawable.getBitmapDownloaderTask();
			}
		}
		return null;
	}

	/**
	 * The actual AsyncTask that will asynchronously download the image.
	 */
	class BitmapDownloaderTask implements Runnable {
		private SurespotMessage mMessage;
		private boolean mCancelled;

		public SurespotMessage getMessage() {
			return mMessage;
		}

		private final WeakReference<ImageView> imageViewReference;

		public BitmapDownloaderTask(ImageView imageView, SurespotMessage message) {
			mMessage = message;
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		public void cancel() {
			mCancelled = true;
		}

		@Override
		public void run() {
			Bitmap bitmap = null;
			InputStream imageStream = null;

			if (mMessage.getData().startsWith("file")) {
				try {
					imageStream = MainActivity.getContext().getContentResolver().openInputStream(Uri.parse(mMessage.getData()));
				}
				catch (FileNotFoundException e) {
					SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
				}
			}
			else {
				imageStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(), mMessage.getData());
			}

			if (mCancelled) {
				try {
					if (imageStream != null) {
						imageStream.close();
					}
				}
				catch (IOException e) {
					SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
				}
				return;
			}

			if (!mCancelled && imageStream != null) {
				PipedOutputStream out = new PipedOutputStream();
				PipedInputStream inputStream = null;
				try {
					inputStream = new PipedInputStream(out);

					EncryptionController.runDecryptTask(mMessage.getOurVersion(), mMessage.getOtherUser(), mMessage.getTheirVersion(), mMessage.getIv(), mMessage.isHashed(),
							new BufferedInputStream(imageStream), out);

					if (mCancelled) {
						mMessage.setLoaded(true);
						mMessage.setLoading(false);
						mChatAdapter.checkLoaded();
						return;
					}

					byte[] bytes = Utils.inputStreamToBytes(inputStream);
					if (mCancelled) {
						mMessage.setLoaded(true);
						mMessage.setLoading(false);
						mChatAdapter.checkLoaded();
						return;
					}

					bitmap = ChatUtils.getSampledImage(bytes);
				}
				catch (InterruptedIOException ioe) {

					SurespotLog.w(TAG, ioe, "MessageImage ioe");

				}
				catch (IOException e) {
					SurespotLog.w(TAG, e, "MessageImage e");
				}
				finally {

					try {
						if (imageStream != null) {
							imageStream.close();
						}
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
					}

					try {
						if (inputStream != null) {
							inputStream.close();
						}
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
					}
				}
			}

			mMessage.setLoaded(true);
			mMessage.setLoading(false);

			final Bitmap finalBitmap = bitmap;

			if (imageViewReference != null) {
				final ImageView imageView = imageViewReference.get();
				final BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
				// Change bitmap only if this process is still associated with it
				// Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
				if ((BitmapDownloaderTask.this == bitmapDownloaderTask)) {
					mHandler.post(new Runnable() {

						@Override
						public void run() {

							if (finalBitmap != null) {

								MessageImageDownloader.addBitmapToCache(mMessage.getData(), finalBitmap);

								Drawable drawable = imageView.getDrawable();
								if (drawable instanceof DownloadedDrawable) {

									imageView.clearAnimation();
									Animation fadeIn = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_in);// new
																																	// AlphaAnimation(0,
																																	// 1);
									imageView.startAnimation(fadeIn);
								}

								imageView.setImageBitmap(finalBitmap);
								imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();

								UIUtils.updateDateAndSize(mMessage, (View) imageView.getParent());
								mChatAdapter.checkLoaded();
							}
							else {
								//TODO set error image
								imageView.setImageDrawable(null);
							}
						}
					});
				}

			}

		}
	}

	/**
	 * A fake Drawable that will be attached to the imageView while the download is in progress.
	 * 
	 * <p>
	 * Contains a reference to the actual download task, so that a download task can be stopped if a new binding is required, and makes sure that only the last
	 * started download process can bind its result, independently of the download finish order.
	 * </p>
	 */
	public static class DownloadedDrawable extends ColorDrawable {
		private final WeakReference<BitmapDownloaderTask> bitmapDownloaderTaskReference;
		private int mHeight;

		public DownloadedDrawable(BitmapDownloaderTask bitmapDownloaderTask, int height) {
			mHeight = height;
			bitmapDownloaderTaskReference = new WeakReference<BitmapDownloaderTask>(bitmapDownloaderTask);
		}

		public BitmapDownloaderTask getBitmapDownloaderTask() {
			return bitmapDownloaderTaskReference.get();
		}

		/**
		 * Force ImageView to be a certain height
		 */
		@Override
		public int getIntrinsicHeight() {

			return mHeight;
		}

	}

	/**
	 * Adds this bitmap to the cache.
	 * 
	 * @param bitmap
	 *            The newly downloaded bitmap.
	 */
	public static void addBitmapToCache(String key, Bitmap bitmap) {
		if (bitmap != null) {
			mBitmapCache.addBitmapToMemoryCache(key, bitmap);
		}
	}

	private static Bitmap getBitmapFromCache(String key) {
		return mBitmapCache.getBitmapFromMemCache(key);
	}

	public static void evictCache() {
		// evict cache on gingerbread because bitmap garbage collection is fucked
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB && mImageViews != null) {

			ArrayList<Bitmap> preserve = new ArrayList<Bitmap>();

			// make sure we're not using the bitmaps before we recycle
			for (ImageView view : mImageViews.keySet()) {
				// don't evict visible bitmaps
				if (!view.isShown()) {
					view.setImageDrawable(null);
				}
				else {
					Drawable drawable = view.getDrawable();
					if (drawable instanceof BitmapDrawable) {
						Bitmap bmp = ((BitmapDrawable) view.getDrawable()).getBitmap();
						preserve.add(bmp);
					}
				}
			}

			mImageViews.clear();
			mBitmapCache.evictExcept(preserve);
			preserve.clear();
		}
		else {
			// otherwise just trim it
			mBitmapCache.trimToSize(10);
		}
	}

	public static void copyAndRemoveCacheEntry(String sourceKey, String destKey) {
		Bitmap bitmap = mBitmapCache.getBitmapFromMemCache(sourceKey);
		if (bitmap != null) {
			mBitmapCache.remove(sourceKey);
			mBitmapCache.addBitmapToMemoryCache(destKey, bitmap);
		}
	}
}
