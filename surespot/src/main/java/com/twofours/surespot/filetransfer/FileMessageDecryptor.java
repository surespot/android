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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.TextView;

import com.rockerhieu.emojicon.EmojiconHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.utils.UIUtils;

import java.lang.ref.WeakReference;

public class FileMessageDecryptor {
    private static final String TAG = "FileMessageDecryptor";
    private static Handler mHandler = new Handler(Looper.getMainLooper());
    private ChatAdapter mChatAdapter;
    private String mUsername;
    private Context mContext;

    public FileMessageDecryptor(Context context, String username, ChatAdapter chatAdapter) {
        mContext = context;
        mUsername = username;
        mChatAdapter = chatAdapter;
    }

    public void decrypt(View view, SurespotMessage message) {

        DecryptionTask task = new DecryptionTask(view, message);
        DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);
        view.setTag(decryptionTaskWrapper);
        message.setLoading(true);
        message.setLoaded(false);
        SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);

    }

    private DecryptionTask getDecryptionTask(View view) {
        if (view != null) {
            Object oDecryptionTaskWrapper = view.getTag();
            if (oDecryptionTaskWrapper instanceof DecryptionTaskWrapper) {
                DecryptionTaskWrapper decryptionTaskWrapper = (DecryptionTaskWrapper) oDecryptionTaskWrapper;
                return decryptionTaskWrapper.getDecryptionTask();
            }
        }
        return null;
    }


    class DecryptionTask implements Runnable {
        private SurespotMessage mMessage;

        private final WeakReference<View> viewReference;

        public DecryptionTask(View view, SurespotMessage message) {
            viewReference = new WeakReference<View>(view);
            mMessage = message;
        }

        @Override
        public void run() {
            final CharSequence plainText = EncryptionController.symmetricDecrypt(mContext, mUsername, mMessage.getOurVersion(mUsername), mMessage.getOtherUser(mUsername),
                    mMessage.getTheirVersion(mUsername), mMessage.getIv(), mMessage.isHashed(), mMessage.getData());
            //SurespotLog.d(TAG, "decryption Task, decrypted plainText: %s", plainText);
            CharSequence plainData = null;
            if (plainText != null) {
                if (mMessage.getMimeType().equals(SurespotConstants.MimeTypes.FILE)) {
                    SurespotMessage.FileMessageData fmd = SurespotMessage.FileMessageData.fromJSONString(plainText.toString());
                    SurespotLog.d(TAG, "decryption Task, decrypted FileMessageData: %s", fmd);
                    if (mMessage.getFileMessageData() == null) {
                        mMessage.setFileMessageData(new SurespotMessage.FileMessageData());
                    }
                    mMessage.getFileMessageData().setCloudUrl(fmd.getCloudUrl());
                    mMessage.getFileMessageData().setFilename(fmd.getFilename());
                    mMessage.getFileMessageData().setSize(fmd.getSize());
                    mMessage.getFileMessageData().setMimeType(fmd.getMimeType());
                    SurespotLog.d(TAG, "decryption task, message after FileMessageData: %s", mMessage.getFileMessageData());
                }
                else {


                    // set plaintext in messageso we don't have to decrypt again
                    SpannableStringBuilder builder = new SpannableStringBuilder(plainText);
                    EmojiconHandler.addEmojis(mChatAdapter.getContext(), builder, 30);
                    plainData = builder.toString();
                    mMessage.setPlainData(plainData);
                }
            }
            else {
                //error decrypting
                SurespotLog.d(TAG, "could not decrypt message");
                plainData = mChatAdapter.getContext().getString(R.string.message_error_decrypting_message);
                mMessage.setPlainData(plainData);
            }

            mMessage.setLoading(false);
            mMessage.setLoaded(true);
            mChatAdapter.checkLoaded();


            if (viewReference != null) {

                final View view = viewReference.get();

                DecryptionTask decryptionTask = getDecryptionTask(view);
                // Change text only if this process is still associated with it
                if ((this == decryptionTask)) {


                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            SurespotLog.d(TAG, "setting filename: %s", mMessage.getFileMessageData().getFilename());
                            TextView filenameView = (TextView) view.findViewById(R.id.fileFilename);
                            filenameView.setText(mMessage.getFileMessageData().getFilename());
                            UIUtils.updateDateAndSize(mChatAdapter.getContext(), mMessage, view);
                        }
                    });
                }
            }
        }
    }


    class DecryptionTaskWrapper {
        private final WeakReference<DecryptionTask> decryptionTaskReference;

        public DecryptionTaskWrapper(DecryptionTask decryptionTask) {
            decryptionTaskReference = new WeakReference<DecryptionTask>(decryptionTask);
        }

        public DecryptionTask getDecryptionTask() {
            return decryptionTaskReference.get();
        }
    }

}
