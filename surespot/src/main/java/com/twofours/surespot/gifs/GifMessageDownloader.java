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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.UIUtils;
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
public class GifMessageDownloader {
    private static final String TAG = "GifMessageDownloader";
    private static GifCache mGifCache = new GifCache();
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private ChatAdapter mChatAdapter;
    private String mUsername;
    private Context mContext;


    public GifMessageDownloader(Context context, String username, ChatAdapter chatAdapter) {
        mContext = context;
        mUsername = username;
        mChatAdapter = chatAdapter;
    }

    public void download(GifImageView imageView, SurespotMessage message) {
        if (message == null) {
            return;
        }

        if (!message.isDownloadGif()) return;

        //cache per IV as well so we have a drawable per message
        GifDrawable gifDrawable = getGifDrawableFromCache(message.getIv());
        if (gifDrawable == null) {
            SurespotLog.v(TAG, "gif not in memory cache for iv: %s", message.getIv());
            forceDownload(imageView, message);
            imageView.setImageDrawable(null);
        }
        else {
            SurespotLog.v(TAG, "loading gif from memory cache for iv: %s", message.getIv());

            cancelPotentialDownload(imageView, message);
            //imageView.clearAnimation();
            imageView.setImageDrawable(gifDrawable);
            //ChatUtils.setImageViewLayout(imageView, gifDrawable.getIntrinsicWidth(), gifDrawable.getIntrinsicHeight());
            message.setLoaded(true);
            message.setLoading(false);

            UIUtils.updateDateAndSize(mChatAdapter.getContext(), message, (View) imageView.getParent());
        }
    }

	/*
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

    /**
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(GifImageView imageView, SurespotMessage message) {
        if (cancelPotentialDownload(imageView, message)) {
            GifDownloaderTask task = new GifDownloaderTask(imageView, message);
            DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);
            imageView.setTag(R.id.tagGifDownloader, decryptionTaskWrapper);
            message.setLoaded(false);
            message.setLoading(true);
            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }
    }

    /**
     * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
     * progress deals with the same url. The download is not stopped in that case.
     */
    private boolean cancelPotentialDownload(GifImageView imageView, SurespotMessage message) {
        GifDownloaderTask GifDownloaderTask = getGifDownloaderTask(imageView);

        if (GifDownloaderTask != null) {
            SurespotMessage taskMessage = GifDownloaderTask.getMessage();
            if ((taskMessage == null) || (!taskMessage.equals(message))) {
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
        private SurespotMessage mMessage;
        private boolean mCancelled;

        public SurespotMessage getMessage() {
            return mMessage;
        }

        private final WeakReference<GifImageView> imageViewReference;

        public GifDownloaderTask(GifImageView imageView, SurespotMessage message) {
            mMessage = message;
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

            //decrypt the url
            if (TextUtils.isEmpty(mMessage.getPlainData())) {
                CharSequence plainText = EncryptionController.symmetricDecrypt(mContext, mUsername, mMessage.getOurVersion(mUsername), mMessage.getOtherUser(mUsername),
                        mMessage.getTheirVersion(mUsername), mMessage.getIv(), mMessage.isHashed(), mMessage.getData());
                mMessage.setPlainData(plainText);
            }

            //error decrypting
            if (TextUtils.isEmpty(mMessage.getPlainData())) {
                SurespotLog.d(TAG, "could not decrypt message");
                String plainData = mChatAdapter.getContext().getString(R.string.message_error_decrypting_message);
                mMessage.setPlainData(plainData);
                return;
            }

            //if we have unencrypted url
            String url = mMessage.getPlainData().toString();
            if (!TextUtils.isEmpty(url)) {
                if (!url.startsWith("https")) {
                    SurespotLog.w(TAG, "GifDownloaderTask url does not start with https: %s,", url);
                    return;
                }

                SurespotLog.v(TAG, "GifDownloaderTask getting %s,", url);

                InputStream gifImageStream = NetworkManager.getNetworkController(mChatAdapter.getContext(), mUsername).getFileStream(url);
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

                GifDrawable gifDrawable = null;
                if (!mCancelled && gifImageStream != null) {

                    try {
                        byte[] bytes = Utils.inputStreamToBytes(gifImageStream);
                        //save in file cache
                        //add encrypted local file to file cache
                        FileCacheController fcc = SurespotApplication.getFileCacheController();
                        if (fcc != null) {
                            fcc.putEntry(url, new ByteArrayInputStream(bytes));
                        }
                        gifDrawable = new GifDrawableBuilder().from(bytes).build();
                    }
                    catch (Exception ioe) {
                        SurespotLog.w(TAG, ioe, "MessageImage exception");
                    }


                    if (gifDrawable != null) {
                        addGifDrawableToCache(getMessage().getIv(), gifDrawable);
                        final GifImageView imageView = imageViewReference.get();
                        if (imageView != null) {
                            final GifDownloaderTask gifDownloaderTask = getGifDownloaderTask(imageView);


                            // Change bitmap only if this process is still associated with it
                            // Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
                            if ((GifDownloaderTask.this == gifDownloaderTask)) {
                                final GifDrawable finalGifDrawable = gifDrawable;
                                mHandler.post(new Runnable() {

                                    @Override
                                    public void run() {
                                        if (!mCancelled) {
//                                        imageView.clearAnimation();
//                                        Animation fadeIn = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_in);// new
//                                        imageView.startAnimation(fadeIn);
                                            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                            imageView.setImageDrawable(finalGifDrawable);
                                            //ChatUtils.setImageViewLayout(imageView, finalGifDrawable.getIntrinsicWidth(), finalGifDrawable.getIntrinsicHeight());
                                            UIUtils.updateDateAndSize(mChatAdapter.getContext(), mMessage, (View) imageView.getParent());
                                            mChatAdapter.checkLoaded();
                                        }
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
    public static void addGifDrawableToCache(String key, GifDrawable bitmap) {
        if (key != null && bitmap != null) {
            mGifCache.addGifDrawableToMemoryCache(key, bitmap);
        }
    }

    private static GifDrawable getGifDrawableFromCache(String key) {
        if (key != null) {
            return mGifCache.getGifDrawableFromMemCache(key);
        }

        return null;
    }

    public static void moveCacheEntry(String sourceKey, String destKey) {
        if (sourceKey != null && destKey != null) {
            GifDrawable bitmap = mGifCache.getGifDrawableFromMemCache(sourceKey);
            if (bitmap != null) {
                mGifCache.remove(sourceKey);
                mGifCache.addGifDrawableToMemoryCache(destKey, bitmap);
            }
        }
    }
}
