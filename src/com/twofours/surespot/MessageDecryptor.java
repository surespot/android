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

package com.twofours.surespot;

import java.lang.ref.WeakReference;
import java.text.DateFormat;

import android.os.AsyncTask;
import android.view.View;
import android.widget.TextView;

import com.twofours.surespot.chat.SurespotMessage;
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
public class MessageDecryptor {
	private static final String TAG = "TextDecryptor";

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
		task.execute();

	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated with this imageView. null if there is no such task.
	 */
	private static DecryptionTask getDecryptionTask(TextView textView) {
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
	class DecryptionTask extends AsyncTask<Void, Void, String> {
		private SurespotMessage mMessage;

		private final WeakReference<TextView> textViewReference;

		public DecryptionTask(TextView textView, SurespotMessage message) {
			textViewReference = new WeakReference<TextView>(textView);
			mMessage = message;
		}

		/**
		 * Actual download method.
		 */
		@Override
		protected String doInBackground(Void... params) {
			return EncryptionController.symmetricDecryptSync(Utils.getOtherUser(mMessage.getFrom(), mMessage.getTo()), mMessage.getIv(),
					mMessage.getCipherData());
		}

		/**
		 * Once the image is downloaded, associates it to the imageView
		 */
		@Override
		protected void onPostExecute(String plainText) {
			if (isCancelled()) {
				plainText = "";
			}

			// set plaintext in message so we don't have to decrypt again
			mMessage.setPlainData(plainText);

			if (textViewReference != null) {
				TextView textView = textViewReference.get();
				DecryptionTask decryptionTask = getDecryptionTask(textView);
				// Change text only if this process is still associated with it
				if ((this == decryptionTask)) {
					// textView.clearAnimation();
					// if (plainText != null) {
					// Animation fadeOut = new AlphaAnimation(1,0);
					// Animation fadeIn = new AlphaAnimation(0, 1);
					// fadeIn.setDuration(1000);
					// fadeOut.setDuration(1000);
					// textView.startAnimation(fadeIn);
					// textView.startAnimation(fadeOut);
					// }

					textView.setText(plainText);

					// TODO put the row in the tag
					TextView tvTime = (TextView) ((View) textView.getParent()).findViewById(R.id.messageTime);
					if (mMessage.getDateTime() == null) {
						tvTime.setText("");
					}
					else {
						tvTime.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(mMessage.getDateTime()));
					}
				}
			}
		}
	}

	/**
	 * makes sure that only the last started decrypt process can bind its result, independently of the finish order. </p>
	 */
	static class DecryptionTaskWrapper {
		private final WeakReference<DecryptionTask> decryptionTaskReference;

		public DecryptionTaskWrapper(DecryptionTask decryptionTask) {
			decryptionTaskReference = new WeakReference<DecryptionTask>(decryptionTask);
		}

		public DecryptionTask getDecryptionTask() {
			return decryptionTaskReference.get();
		}
	}

}
