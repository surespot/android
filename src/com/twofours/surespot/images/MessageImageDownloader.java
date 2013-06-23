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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

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

	public MessageImageDownloader(ChatAdapter chatAdapter) {
		mChatAdapter = chatAdapter;
	}

	/**
	 * Download the specified image from the Internet and binds it to the provided ImageView. The binding is immediate if the image is found in the cache and
	 * will be done asynchronously otherwise. A null bitmap will be associated to the ImageView if an error occurs.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void download(ImageView imageView, SurespotMessage message) {
		Bitmap bitmap = getBitmapFromCache(message.getData());

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

			TextView tvTime = (TextView) ((View) imageView.getParent()).findViewById(R.id.messageTime);
			if (message.getDateTime() != null) {

				tvTime.setText(DateFormat.getDateFormat(MainActivity.getContext()).format(message.getDateTime()) + " "
						+ DateFormat.getTimeFormat(MainActivity.getContext()).format(message.getDateTime()));

			}
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
					SurespotLog.w(TAG, e, "BitmapDownloaderTask");
				}
			}
			else {
				imageStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(), mMessage.getData());
			}

			if (!mCancelled && imageStream != null) {
				PipedOutputStream out = new PipedOutputStream();
				PipedInputStream inputStream;
				try {
					inputStream = new PipedInputStream(out);

					EncryptionController.runDecryptTask(mMessage.getOurVersion(), mMessage.getOtherUser(), mMessage.getTheirVersion(), mMessage.getIv(),
							new BufferedInputStream(imageStream), out);

					if (mCancelled) {
						inputStream.close();
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

					SurespotLog.w(TAG, "BitmapDownloaderTask ioe", ioe);

				}
				catch (IOException e) {
					SurespotLog.w(TAG, "BitmapDownloaderTask e", e);
				}
			}

			if (bitmap != null) {

				final Bitmap finalBitmap = bitmap;

				MessageImageDownloader.addBitmapToCache(mMessage.getData(), bitmap);

				mMessage.setLoaded(true);
				mMessage.setLoading(false);

				if (imageViewReference != null) {
					final ImageView imageView = imageViewReference.get();
					final BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
					// Change bitmap only if this process is still associated with it
					// Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
					if ((BitmapDownloaderTask.this == bitmapDownloaderTask)) {

						mHandler.post(new Runnable() {

							@Override
							public void run() {

								Drawable drawable = imageView.getDrawable();
								if (drawable instanceof DownloadedDrawable) {

									imageView.clearAnimation();
									Animation fadeIn = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_in);// new
																																	// AlphaAnimation(0,
																																	// 1);
									// Animation fadeout = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_out);
									// fadeIn.setDuration(1000);
									imageView.startAnimation(fadeIn);

								}
								else {
									SurespotLog.v(TAG, "clearing uploading flag");
									// mMessage.setPlainData(null);
									ImageViewAnimatedChange(imageView.getContext(), imageView, finalBitmap);
								}

								imageView.setImageBitmap(finalBitmap);
								imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();
								
								TextView tvTime = (TextView) ((View) imageView.getParent()).findViewById(R.id.messageTime);
								ImageView ivShareable = (ImageView) ((View) imageView.getParent()).findViewById(R.id.messageImageShareable);
								ImageView ivNotShareable = (ImageView) ((View) imageView.getParent()).findViewById(R.id.messageImageNotShareable);

								if (mMessage.isShareable()) {
									ivShareable.setVisibility(View.VISIBLE);
									ivNotShareable.setVisibility(View.GONE);
								}
								else {
									ivShareable.setVisibility(View.GONE);
									ivNotShareable.setVisibility(View.VISIBLE);
								}

								// if (mMessage.getErrorStatus() > 0) {
								// UIUtils.setMessageErrorText(tvTime, mMessage);
								// }
								// else {

								if (mMessage.getDateTime() != null) {

									tvTime.setText(DateFormat.getDateFormat(MainActivity.getContext()).format(mMessage.getDateTime()) + " "
											+ DateFormat.getTimeFormat(MainActivity.getContext()).format(mMessage.getDateTime()));
								}

								mChatAdapter.checkLoaded();
								// }
							}
						});
					}
				}
			}

		}
	}

	public static void ImageViewAnimatedChange(Context c, final ImageView v, final Bitmap new_image) {
		SurespotLog.v(TAG, "switching image");
		final Animation anim_out = AnimationUtils.loadAnimation(c, android.R.anim.fade_out);
		final Animation anim_in = AnimationUtils.loadAnimation(c, android.R.anim.fade_in);
		anim_out.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				v.setImageBitmap(new_image);
				anim_in.setAnimationListener(new AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {
					}

					@Override
					public void onAnimationRepeat(Animation animation) {
					}

					@Override
					public void onAnimationEnd(Animation animation) {
					}
				});
				v.startAnimation(anim_in);
			}
		});
		v.startAnimation(anim_out);
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

	/**
	 * @param url
	 *            The URL of the image that will be retrieved from the cache.
	 * @return The cached bitmap or null if it was not found.
	 */
	private static Bitmap getBitmapFromCache(String key) {
		return mBitmapCache.getBitmapFromMemCache(key);
	}

	public static void evictCache() {
		mBitmapCache.evictAll();

	}

	public static void copyAndRemoveCacheEntry(String sourceKey, String destKey) {
		Bitmap bitmap = mBitmapCache.getBitmapFromMemCache(sourceKey);
		if (bitmap != null) {
			mBitmapCache.remove(sourceKey);
			mBitmapCache.addBitmapToMemoryCache(destKey, bitmap);
		}
	}
}
