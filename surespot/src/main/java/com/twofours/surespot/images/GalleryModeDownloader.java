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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.utils.ChatUtils;

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
    private Context mContext;


    public GalleryModeDownloader(Context context) {

        mContext = context;
        mContentResolver = context.getContentResolver();

    }

    public void download(ImageView imageView, GalleryData data) {
        if (data == null) {
            return;
        }

        //cache per IV as well so we have a drawable per message
        Bitmap bitmap = getBitmapFromCache(String.valueOf(data.getId()));
        if (bitmap == null) {
            SurespotLog.v(TAG, "bitmap not in memory cache for id %d", data.getId());


            //imageView.showProgress();
            forceDownload(imageView, data);
        }
        else {
            SurespotLog.v(TAG, "loading bitmap from memory cache for id: %d, width: %d, height: %d", data.getId(), bitmap.getWidth(), bitmap.getHeight());

            cancelPotentialDownload(imageView, data);
            //imageView.clearAnimation();
            imageView.setImageBitmap(bitmap);
        }
    }


    private Bitmap loadThumbnail(GalleryData data) {
        SurespotLog.v(TAG, "loading bitmap for id: %d", data.getId());

        String[] args = new String[]{String.valueOf(data.getId())};
        Cursor ct = mContentResolver.query(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Images.Thumbnails._ID}, MediaStore.Images.Thumbnails.IMAGE_ID + "= ?", args, null);
        Bitmap bitmap = null;
        Uri uri = null;
        if (ct.moveToFirst()) {

            //String path = ct.getString(ct.getColumnIndex(MediaStore.Images.Thumbnails.DATA));
            //bitmap = BitmapFactory.decodeFile(path);
            uri = ContentUris.withAppendedId(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, ct.getLong(0));
            bitmap = ChatUtils.decodeSampledBitmapFromUri(mContext, uri, data.getOrientation(), Math.max(data.getWidth(), data.getHeight()));
            if (bitmap != null) {
                SurespotLog.v(TAG, "loaded thumbnail bitmap for id: %d, ratio: %f, width: %d, height: %d, orientation: %d", data.getId(), (double) bitmap.getWidth() / bitmap.getHeight(), bitmap.getWidth(), bitmap.getHeight(), data.getOrientation());
            }
        }

        if (bitmap == null) {
            //no thumbnail, generate our own
            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, data.getId());
            bitmap = ChatUtils.decodeSampledBitmapFromUri(mContext, uri, data.getOrientation(), Math.max(data.getWidth(), data.getHeight()));
            if (bitmap != null) {
                SurespotLog.v(TAG, "generated bitmap for id: %d, ratio: %f, width: %d, height: %d, orientation: %d", data.getId(), (double) bitmap.getWidth() / bitmap.getHeight(), bitmap.getWidth(), bitmap.getHeight(), data.getOrientation());
            }
        }
        ct.close();
        return bitmap;
    }

	/*
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

    /**
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(ImageView imageView, GalleryData data) {
        if (cancelPotentialDownload(imageView, data)) {
            BitmapDownloaderTask task = new BitmapDownloaderTask(imageView, data);
            DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task, SurespotConfiguration.getImageDisplayHeight());
            imageView.setImageDrawable(downloadedDrawable);
            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }
    }

    /**
     * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
     * progress deals with the same url. The download is not stopped in that case.
     */
    private boolean cancelPotentialDownload(ImageView imageView, GalleryData data) {
        BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

        if (bitmapDownloaderTask != null) {
            GalleryData taskMessage = bitmapDownloaderTask.getData();
            if ((taskMessage == null) || (!taskMessage.equals(data))) {
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
        private GalleryData mData;
        private boolean mCancelled;

        public GalleryData getData() {
            return mData;
        }

        private final WeakReference<ImageView> imageViewReference;

        public BitmapDownloaderTask(ImageView imageView, GalleryData message) {
            mData = message;
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

            //if we have unencrypted data
            final GalleryData data = mData;
            if (data != null) {
                SurespotLog.v(TAG, "BitmapDownloaderTask getting %d,", data.getId());
                final Bitmap bitmap = loadThumbnail(data);
                final ImageView imageView = imageViewReference.get();

                if (!mCancelled && bitmap != null && imageView != null) {
                    SurespotLog.v(TAG, "BitmapDownloaderTask, data: %d, width: %d, height: %d", data.getId(), bitmap.getWidth(), bitmap.getHeight());
                    final BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
                    if (BitmapDownloaderTask.this == bitmapDownloaderTask) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {

                                if (!mCancelled) {
                                    SurespotLog.v(TAG, "bitmap downloaded: %d", data.getId());
                                    addBitmapToCache(String.valueOf(data.getId()), bitmap);
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
