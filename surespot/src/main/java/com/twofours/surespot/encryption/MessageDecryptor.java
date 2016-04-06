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

package com.twofours.surespot.encryption;

import java.lang.ref.WeakReference;

import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.EmojiParser;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotLog;
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
public class MessageDecryptor {
	private static final String TAG = "TextDecryptor";
	private static Handler mHandler = new Handler(MainActivity.getContext().getMainLooper());
	private ChatAdapter mChatAdapter;

	public MessageDecryptor(ChatAdapter chatAdapter) {
		mChatAdapter = chatAdapter;
	}

	/**
	 * Download the specified image from the Internet and binds it to the provided ImageView. The binding is immediate if the image is found
	 * in the cache and will be done asynchronously otherwise. A null bitmap will be associated to the ImageView if an error occurs.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 */
	public void decrypt(TextView textView, SurespotMessage message) {

		DecryptionTask task = new DecryptionTask(textView, message);
		DecryptionTaskWrapper decryptionTaskWrapper = new DecryptionTaskWrapper(task);
		textView.setTag(decryptionTaskWrapper);
		message.setLoading(true);
		message.setLoaded(false);
		SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);

	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
	 */
	private DecryptionTask getDecryptionTask(TextView textView) {
		if (textView != null) {
			Object oDecryptionTaskWrapper = textView.getTag();
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
	class DecryptionTask implements Runnable {
		private SurespotMessage mMessage;

		private final WeakReference<TextView> textViewReference;

		public DecryptionTask(TextView textView, SurespotMessage message) {
			textViewReference = new WeakReference<TextView>(textView);
			mMessage = message;
		}

		@Override
		public void run() {
			final CharSequence plainText = EncryptionController.symmetricDecrypt(mMessage.getOurVersion(), mMessage.getOtherUser(),
					mMessage.getTheirVersion(), mMessage.getIv(), mMessage.isHashed(), mMessage.getData());

			CharSequence plainData = null;
			if (plainText != null) {
				// set plaintext in messageso we don't have to decrypt again
				plainData = EmojiParser.getInstance().addEmojiSpans(plainText.toString());
				mMessage.setPlainData(plainData);
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
				
			

			if (textViewReference != null) {

				final TextView textView = textViewReference.get();

				DecryptionTask decryptionTask = getDecryptionTask(textView);
				// Change text only if this process is still associated with it
				if ((this == decryptionTask)) {

					final CharSequence finalPlainData = plainData;
					mHandler.post(new Runnable() {

						@Override
						public void run() {
							textView.setText(finalPlainData);
							UIUtils.updateDateAndSize(mMessage, (View) textView.getParent());
						}
					});
				}
			}
		}
	}

	/**
	 * makes sure that only the last started decrypt process can bind its result, independently of the finish order. </p>
	 */
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
