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

package com.twofours.surespot.gifs;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.images.BitmapCache;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifDrawableBuilder;
import pl.droidsonroids.gif.GifImageView;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * <p/>
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * <p/>
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class GifSearchDownloader {
    private static final String TAG = "GifSearchDownloader";
    private static BitmapCache mBitmapCache = new BitmapCache();
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private GifSearchAdapter mChatAdapter;
    private String mUsername;


    public GifSearchDownloader(GifSearchAdapter chatAdapter) {
        mChatAdapter = chatAdapter;
    }

    public void download(GifImageView imageView, String uri) {

        if (uri == null) {
            return;
        }
        Bitmap bitmap = getBitmapFromCache(uri);


        if (bitmap == null) {
            SurespotLog.d(TAG, "bitmap not in memory cache: " + uri);
            forceDownload(imageView, uri);
        }
        else {
            SurespotLog.d(TAG, "loading bitmap from memory cache: " + uri);
            cancelPotentialDownload(imageView, uri);
         //   imageView.clearAnimation();
            imageView.setImageBitmap(bitmap);

            //      UIUtils.updateDateAndSize(mChatAdapter.getContext(), message, (View) imageView.getParent());

        }
    }

	/*
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

    /**
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(GifImageView imageView, String uri) {
        if (cancelPotentialDownload(imageView, uri)) {
            GifDownloaderTask task = new GifDownloaderTask(imageView, uri);
            //          DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task, SurespotConfiguration.getImageDisplayHeight());
//            imageView.setImageDrawable(downloadedDrawable);

            DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);

            imageView.setTag(R.id.tagGifDownloader, decryptionTaskWrapper);

            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }
    }

    /**
     * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
     * progress deals with the same url. The download is not stopped in that case.
     */
    private boolean cancelPotentialDownload(GifImageView imageView, String url) {
        GifDownloaderTask GifDownloaderTask = getGifDownloaderTask(imageView);

        if (GifDownloaderTask != null) {
            String taskMessage = GifDownloaderTask.getUrl();
            if ((taskMessage == null) || (!taskMessage.equals(url))) {
                GifDownloaderTask.cancel();
            }
            else {
                // The same URL is already being downloaded.
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
     */
    public GifDownloaderTask getGifDownloaderTask(GifImageView imageView) {
        if (imageView != null) {


            Object oDecryptionTaskWrapper = imageView.getTag(R.id.tagGifDownloader);
            if (oDecryptionTaskWrapper instanceof DecryptionTaskWrapper) {
                DecryptionTaskWrapper decryptionTaskWrapper = (DecryptionTaskWrapper) oDecryptionTaskWrapper;
                return decryptionTaskWrapper.getDecryptionTask();
            }
        }
        return null;
    }

    /**
     * The actual AsyncTask that will asynchronously download the image.
     */
    class GifDownloaderTask implements Runnable {
        private String mUrl;
        private boolean mCancelled;

        public String getUrl() {
            return mUrl;
        }

        private final WeakReference<GifImageView> imageViewReference;

        public GifDownloaderTask(GifImageView imageView, String message) {
            mUrl = message;
            imageViewReference = new WeakReference<GifImageView>(imageView);
        }

        public void cancel() {
            mCancelled = true;
        }

        @Override
        public void run() {
            if (mCancelled) {
                return;
            }


            SurespotLog.d(TAG, "GifDownloaderTask getting %s,", mUrl);

            InputStream gifImageStream = NetworkManager.getNetworkController(mChatAdapter.getContext()).getFileStream(getUrl());
            GifDrawable gifDrawable = null;
            if (mCancelled) {
                try {
                    if (gifImageStream != null) {
                        gifImageStream.close();
                    }
                }
                catch (IOException e) {
                    SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
                }
                return;
            }

            if (!mCancelled && gifImageStream != null) {

                try {
                    byte[] bytes = Utils.inputStreamToBytes(gifImageStream);
                    //save in file cache
                    //add encrypted local file to file cache
                    FileCacheController fcc = SurespotApplication.getFileCacheController();
                    if (fcc != null) {
                        fcc.putEntry(getUrl(), new ByteArrayInputStream(bytes));
                    }
                    gifDrawable = new GifDrawableBuilder().from(bytes).build();
                }
                catch (Exception ioe) {

                    SurespotLog.w(TAG, ioe, "MessageImage exception");

                }
//                catch (IOException e) {
//                    SurespotLog.w(TAG, e, "MessageImage e");
//                }
                //         finally {

//                        try {
//                            if (encryptedImageStream != null) {
//                                encryptedImageStream.close();
//                            }
//                        }
//                        catch (IOException e) {
//                            SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
//                        }

//                    try {
//                        if (gifInputStream != null) {
//                            gifInputStream.close();
//                        }
//                    }
//                    catch (IOException e) {
//                        SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
//                    }
                //     }


                final GifImageView imageView = imageViewReference.get();
                if (imageView != null && gifDrawable != null) {
                    final GifDownloaderTask gifDownloaderTask = getGifDownloaderTask(imageView);

                    // Change bitmap only if this process is still associated with it
                    // Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
                    if ((GifDownloaderTask.this == gifDownloaderTask)) {
                        final GifDrawable finalGifDrawable = gifDrawable;
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {

                                //   if (finalGifDrawable != null) {
//
//                                if (!TextUtils.isEmpty(messageData)) {
//                                    GifSearchDownloader.addBitmapToCache(messageData, finalBitmap);
//                                }
//
//                                if (!TextUtils.isEmpty(finalMessageString)) {
//                                    GifSearchDownloader.addBitmapToCache(finalMessageString, finalBitmap);
//                                }


                                imageView.setImageDrawable(finalGifDrawable);
                            }
//
//                            else
//
//                            {
//                                //TODO set error image
//                                imageView.setImageDrawable(null);
//                            }

                        });
                    }

                }
            }

        }
    }

    class DecryptionTaskWrapper {
        private final WeakReference<GifDownloaderTask> decryptionTaskReference;

        public DecryptionTaskWrapper(GifDownloaderTask decryptionTask) {
            decryptionTaskReference = new WeakReference<GifDownloaderTask>(decryptionTask);
        }

        public GifDownloaderTask getDecryptionTask() {
            return decryptionTaskReference.get();
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
