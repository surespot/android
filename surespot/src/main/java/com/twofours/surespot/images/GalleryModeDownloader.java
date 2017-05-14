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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ImageView;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotLog;

import java.lang.ref.WeakReference;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * <p/>
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * <p/>
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class GalleryModeDownloader {
    private static final String TAG = "GalleryModeDownloader";
    private static BitmapCache mBitmapCache = new BitmapCache();
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private ContentResolver mContentResolver;


    public GalleryModeDownloader(Context context) {

        mContentResolver = context.getContentResolver();
    }

    public void download(ImageView imageView, String message) {
        if (message == null) {
            return;
        }

        //cache per IV as well so we have a drawable per message
        Bitmap bitmap = getBitmapFromCache(message);
        if (bitmap == null) {
            SurespotLog.d(TAG, "bitmap not in memory cache for url: %s", message);


            //imageView.showProgress();
            forceDownload(imageView, message);
        }
        else {
            SurespotLog.d(TAG, "loading bitmap from memory cache for url: %s, width: %d, height: %d", message, bitmap.getWidth(), bitmap.getHeight());

            cancelPotentialDownload(imageView, message);
            //imageView.clearAnimation();
            imageView.setImageBitmap(bitmap);
        }
    }

	/*
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

    /**
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(ImageView imageView, String message) {
        if (cancelPotentialDownload(imageView, message)) {
            BitmapDownloaderTask task = new BitmapDownloaderTask(imageView, message);
            DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task, SurespotConfiguration.getImageDisplayHeight());
            imageView.setImageDrawable(downloadedDrawable);
            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }
    }

    /**
     * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
     * progress deals with the same url. The download is not stopped in that case.
     */
    private boolean cancelPotentialDownload(ImageView imageView, String message) {
        BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

        if (bitmapDownloaderTask != null) {
            String taskMessage = bitmapDownloaderTask.getUrl();
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
     * The actual AsyncTask that will asynchronously download the image.
     */
    class BitmapDownloaderTask implements Runnable {
        private String mMessage;
        private boolean mCancelled;

        public String getUrl() {
            return mMessage;
        }

        private final WeakReference<ImageView> imageViewReference;

        public BitmapDownloaderTask(ImageView imageView, String message) {
            mMessage = message;
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        public void cancel() {
            mCancelled = true;
        }

        @Override
        public void run() {
            if (mCancelled) {
                return;
            }


            //if we have unencrypted url
            final String url = mMessage;
            if (!TextUtils.isEmpty(url)) {


                SurespotLog.v(TAG, "BitmapDownloaderTask getting %s,", url);

                final Bitmap bitmap = loadThumbnail(url);




                final ImageView imageView = imageViewReference.get();

                if (!mCancelled && bitmap != null && imageView != null) {
                    SurespotLog.d(TAG, "BitmapDownloaderTask, url: %s, width: %d, height: %d", url, bitmap.getWidth(), bitmap.getHeight());
                    final BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);


                    if (BitmapDownloaderTask.this == bitmapDownloaderTask) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {

                                if (!mCancelled) {
                                    SurespotLog.d(TAG, "bitmap downloaded: %s", url);
                                    addBitmapToCache(url, bitmap);
                                    imageView.setImageBitmap(bitmap);
                              //      ChatUtils.setScaledImageViewLayout(imageView, bitmap.getWidth(), bitmap.getHeight());
                                }
                            }
                        });
                    }
                }
            }
        }
    }


    private Bitmap loadThumbnail(String uri) {


        //int id = (int) ContentUris.parseId(Uri.parse(uri));
//        int orientation = getOrientation(cr, id);
//        Matrix matrix = new Matrix();
//        matrix.postRotate(orientation);
//        Log.d("Orientation", String.valueOf(orientation));
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                mContentResolver, Integer.parseInt(uri), 1, options);
        //bitmap = crop(bitmap, matrix);
        return bitmap;
    }

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
     * @param bitmap The newly downloaded bitmap.
     */
    public static void addBitmapToCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            mBitmapCache.addBitmapToMemoryCache(key, bitmap);
        }
    }

    private static Bitmap getBitmapFromCache(String key) {
        if (key != null) {
            return mBitmapCache.getBitmapFromMemCache(key);
        }

        return null;
    }

    public static void moveCacheEntry(String sourceKey, String destKey) {
        if (sourceKey != null && destKey != null) {
            Bitmap bitmap = mBitmapCache.getBitmapFromMemCache(sourceKey);
            if (bitmap != null) {
                mBitmapCache.remove(sourceKey);
                mBitmapCache.addBitmapToMemoryCache(destKey, bitmap);
            }
        }
    }
}
