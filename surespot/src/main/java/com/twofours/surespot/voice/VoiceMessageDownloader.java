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

package com.twofours.surespot.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * <p>
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * <p>
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class VoiceMessageDownloader {
    private static final String TAG = "VoiceMessageDownloader";
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private ChatAdapter mChatAdapter;
    private String mUsername;
    private Context mContext;

    public VoiceMessageDownloader(Context context, String username, ChatAdapter chatAdapter) {
        mContext = context;
        mUsername = username;
        mChatAdapter = chatAdapter;
    }

    /**
     * Download the specified image from the Internet and binds it to the provided ImageView. The binding is immediate if the image is found in the cache and
     * will be done asynchronously otherwise. A null bitmap will be associated to the ImageView if an error occurs.
     *
     * @param url       The URL of the image to download.
     * @param imageView The ImageView to bind the downloaded image to.
     */
    public void download(View parentView, SurespotMessage message) {
        byte[] voiceData = message.getPlainBinaryData();

        if (voiceData == null) {
            SurespotLog.v(TAG, "voice data not ready: " + message.getData());
            forceDownload(parentView, message);
        }
        else {
            SurespotLog.v(TAG, "loading voice data from message");
            cancelPotentialDownload(parentView, message);

            message.setLoaded(true);
            message.setLoading(false);

            updateUI(mChatAdapter.getContext(), message, parentView);

        }
    }

	/*
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

    /**
     * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(View parentView, SurespotMessage message) {
        if (cancelPotentialDownload(parentView, message)) {
            VoiceMessageDownloaderTask task = new VoiceMessageDownloaderTask(parentView, message);
            DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);
            SeekBar seekBar = (SeekBar) parentView.findViewById(R.id.seekBarVoice);
            seekBar.setTag(R.id.tagDownloader, decryptionTaskWrapper);
            message.setLoaded(false);
            message.setLoading(true);
            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }
    }

    /**
     * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
     * progress deals with the same url. The download is not stopped in that case.
     */
    private boolean cancelPotentialDownload(View parentView, SurespotMessage message) {
        VoiceMessageDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(parentView);

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
    public VoiceMessageDownloaderTask getBitmapDownloaderTask(View parentView) {
        if (parentView != null) {
            SeekBar seekBar = (SeekBar) parentView.findViewById(R.id.seekBarVoice);
            Object oDecryptionTaskWrapper = seekBar.getTag(R.id.tagDownloader);
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
    class VoiceMessageDownloaderTask implements Runnable {
        private SurespotMessage mMessage;
        private boolean mCancelled;

        public SurespotMessage getMessage() {
            return mMessage;
        }

        private final WeakReference<View> viewReference;

        public VoiceMessageDownloaderTask(View parentView, SurespotMessage message) {
            mMessage = message;
            viewReference = new WeakReference<View>(parentView);
        }

        public void cancel() {
            mCancelled = true;
        }

        @Override
        public void run() {
            if (mCancelled) {
                return;
            }

            byte[] soundbytes = mMessage.getPlainBinaryData();
            if (soundbytes == null) {

                //see if we have encrypted local file
                final String messageData = mMessage.getData();
                String messageString = null;
                if (!TextUtils.isEmpty(messageData)) {

                    SurespotLog.d(TAG, "no cached file entry, making http call for voice: %s,", messageData);
                    InputStream voiceStream = NetworkManager.getNetworkController(mChatAdapter.getContext(), mUsername).getFileStream(messageData);

                    if (mCancelled) {
                        try {
                            if (voiceStream != null) {
                                voiceStream.close();
                            }
                        }
                        catch (IOException e) {
                            SurespotLog.w(TAG, e, "VoiceMessageDownloaderTask");
                        }
                        return;
                    }

                    if (!mCancelled && voiceStream != null) {
                        PipedOutputStream out = new PipedOutputStream();
                        PipedInputStream inputStream = null;
                        try {
                            inputStream = new PipedInputStream(out);

                            if (mCancelled) {
                                mMessage.setLoaded(true);
                                mMessage.setLoading(false);
                                mChatAdapter.checkLoaded();
                                return;
                            }

                            EncryptionController.runDecryptTask(mContext, mUsername, mMessage.getOurVersion(mUsername), mMessage.getOtherUser(mUsername), mMessage.getTheirVersion(mUsername), mMessage.getIv(), mMessage.isHashed(),
                                    voiceStream, out);

                            soundbytes = Utils.inputStreamToBytes(inputStream);

                            if (mCancelled) {
                                mMessage.setPlainBinaryData(soundbytes);
                                mMessage.setLoaded(true);
                                mMessage.setLoading(false);
                                mChatAdapter.checkLoaded();
                                return;
                            }
                        }
                        catch (InterruptedIOException ioe) {

                            SurespotLog.w(TAG, ioe, "VoiceMessageDownloaderTask");

                        }
                        catch (IOException e) {
                            SurespotLog.w(TAG, e, "VoiceMessageDownloaderTask");
                        }
                        finally {

//                            try {
//                                if (voiceStream != null) {
//                                    voiceStream.close();
//                                }
//                            }
//                            catch (IOException e) {
//                                SurespotLog.w(TAG, e, "VoiceMessageDownloaderTask");
//                            }

                            try {
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                            }
                            catch (IOException e) {
                                SurespotLog.w(TAG, e, "VoiceMessageDownloaderTask");
                            }
                        }
                    }
                }
                else {
                    CharSequence messagePlainSequence = mMessage.getPlainData();
                    if (!TextUtils.isEmpty(messagePlainSequence)) {
                        try {
                            messageString = messagePlainSequence.toString();
                            soundbytes = Utils.inputStreamToBytes(new FileInputStream(Uri.parse(messageString).getPath()));
                            SurespotLog.d(TAG, "loaded unencrypted voice from: %s, null: %b", messageString, soundbytes == null);
                        }
                        catch (IOException e) {
                            SurespotLog.w(TAG, e, "error loading unencrypted voice from disk");
                        }
                    }
                }
            }
            else {
                SurespotLog.v(TAG, "getting voice stream from cache");
            }

            if (soundbytes != null) {

                mMessage.setPlainBinaryData(soundbytes);
                mMessage.setLoaded(true);
                mMessage.setLoading(false);

                if (viewReference != null) {
                    final View view = viewReference.get();
                    final VoiceMessageDownloaderTask bitmapDownloaderTask = getBitmapDownloaderTask(view);
                    if (!mCancelled && (VoiceMessageDownloaderTask.this == bitmapDownloaderTask)) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                updateUI(mChatAdapter.getContext(), mMessage, view);

                            }

                        });

                    }
                }

                mChatAdapter.checkLoaded();

            }

        }
    }

    private void updateUI(Context context, SurespotMessage message, View parentView) {
        UIUtils.updateDateAndSize(context ,message, parentView);

        if (message.isPlayVoice()) {
            SeekBar seekBar = (SeekBar) parentView.findViewById(R.id.seekBarVoice);
            if (!VoiceController.isRecording() && !VoiceController.isPlaying()) {
                VoiceController.playVoiceMessage(context, seekBar, message);
            }
        }
    }

    class DecryptionTaskWrapper {
        private final WeakReference<VoiceMessageDownloaderTask> decryptionTaskReference;

        public DecryptionTaskWrapper(VoiceMessageDownloaderTask decryptionTask) {
            decryptionTaskReference = new WeakReference<VoiceMessageDownloaderTask>(decryptionTask);
        }

        public VoiceMessageDownloaderTask getDecryptionTask() {
            return decryptionTaskReference.get();
        }
    }
}
