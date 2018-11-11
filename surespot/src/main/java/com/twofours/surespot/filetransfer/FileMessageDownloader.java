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

package com.twofours.surespot.filetransfer;

import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.images.BitmapCache;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
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
public class FileMessageDownloader {
    private static final String TAG = "FileMessageDownloader";
    private static BitmapCache mBitmapCache = new BitmapCache();
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private ChatAdapter mChatAdapter;
    private String mUsername;


    public FileMessageDownloader(String username, ChatAdapter chatAdapter) {
        mUsername = username;
        mChatAdapter = chatAdapter;
    }

    public void download(ImageView imageView, SurespotMessage message) {
        String uri = TextUtils.isEmpty(message.getData()) ? message.getPlainData().toString() : message.getData();

        if (uri == null) {
            return;
        }
//
//        switch (message.getState()) {
//            case SurespotMessage.STATE_DOWNLOADING:
//
//                break;
//
//
//            case SurespotMessage.STATE_CREATED:
//                //download the fucker
//
//
//        }


//
//        if (bitmap == null) {
//            SurespotLog.d(TAG, "bitmap not in memory cache: " + uri);
//            forceDownload(imageView, message);
//            //imageView.setImageDrawable(null);
//        }
//        else {
//            SurespotLog.d(TAG, "loading bitmap from memory cache: " + uri);
//            cancelPotentialDownload(imageView, message);
//            //     imageView.clearAnimation();
//            imageView.setImageBitmap(bitmap);
//
//            ChatUtils.setImageViewLayout(imageView, bitmap.getWidth(), bitmap.getHeight());
//            message.setLoaded(true);
//            message.setLoading(false);
//
//            UIUtils.updateDateAndSize(mChatAdapter.getContext(), message, (View) imageView.getParent());
//
//        }
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
     * @param imageView Any imageView
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
            if (mCancelled) {
                return;
            }

            Bitmap bitmap = null;

            //if we have encrypted url (local or not)
            final String messageData = getMessage().getData();
            String messageString = null;
            if (!TextUtils.isEmpty(messageData)) {

                SurespotLog.d(TAG, "MessageImageDownloaderTask getting %s,", messageData);

                InputStream encryptedImageStream = NetworkManager.getNetworkController(mChatAdapter.getContext(), mUsername).getFileStream(messageData);

                if (mCancelled) {
                    try {
                        if (encryptedImageStream != null) {
                            encryptedImageStream.close();
                        }
                    }
                    catch (IOException e) {
                        SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
                    }
                    return;
                }

                if (!mCancelled && encryptedImageStream != null) {
                    PipedOutputStream out = new PipedOutputStream();
                    PipedInputStream inputStream = null;
                    try {
                        inputStream = new PipedInputStream(out);

                        EncryptionController.runDecryptTask(mChatAdapter.getContext(), mUsername, mMessage.getOurVersion(mUsername), mMessage.getOtherUser(mUsername), mMessage.getTheirVersion(mUsername), mMessage.getIv(), mMessage.isHashed(),
                                new BufferedInputStream(encryptedImageStream), out);

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

//                        try {
//                            if (encryptedImageStream != null) {
//                                encryptedImageStream.close();
//                            }
//                        }
//                        catch (IOException e) {
//                            SurespotLog.w(TAG, e, "MessageImage DownloaderTask");
//                        }

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
            }
            else {
                CharSequence messagePlainSequence = mMessage.getPlainData();
                if (!TextUtils.isEmpty(messagePlainSequence)) {

                    //load unencrypted image from disk
                    try {
                        messageString = messagePlainSequence.toString();
                        bitmap = ChatUtils.getSampledImage(Utils.inputStreamToBytes(new FileInputStream(Uri.parse(messageString).getPath())));
                        SurespotLog.d(TAG, "loaded unencrypted bitmap from: %s, null: %b", messageString, bitmap == null);
                    }

                    catch (IOException e) {
                        SurespotLog.w(TAG, e, "MessageImageDownloaderTask loading unencrypted image from disk");
                    }
                }

            }

            mMessage.setLoaded(true);
            mMessage.setLoading(false);

            final Bitmap finalBitmap = bitmap;
            final String finalMessageString = messageString;

            if (imageViewReference != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
                // Change bitmap only if this process is still associated with it
                // Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
                if ((BitmapDownloaderTask.this == bitmapDownloaderTask)) {
                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {

                            if (!mCancelled) {
                                if (finalBitmap != null) {

                                    if (!TextUtils.isEmpty(messageData)) {
                                        FileMessageDownloader.addBitmapToCache(messageData, finalBitmap);
                                    }

                                    if (!TextUtils.isEmpty(finalMessageString)) {
                                        FileMessageDownloader.addBitmapToCache(finalMessageString, finalBitmap);
                                    }

                                    //    Drawable drawable = imageView.getDrawable();
                                    //       if (drawable instanceof DownloadedDrawable) {

                                    //   imageView.clearAnimation();
                                    //   Animation fadeIn = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_in);// new
                                    // imageView.startAnimation(fadeIn);
                                    //       }

                                    imageView.setImageBitmap(finalBitmap);
                                    ChatUtils.setImageViewLayout(imageView, finalBitmap.getWidth(), finalBitmap.getHeight());
                                    UIUtils.updateDateAndSize(mChatAdapter.getContext(), mMessage, (View) imageView.getParent());
                                    mChatAdapter.checkLoaded();
                                }
                                else {
                                    //TODO set error image
                                    imageView.setImageDrawable(null);
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * A fake Drawable that will be attached to the imageView while the download is in progress.
     * <p/>
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
