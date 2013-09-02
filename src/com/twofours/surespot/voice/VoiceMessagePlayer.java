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

import java.lang.ref.WeakReference;

import android.os.Handler;
import android.widget.SeekBar;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotMessage;

/**
 * This helper class download images from the Internet and binds those with the provided ImageView.
 * 
 * <p>
 * It requires the INTERNET permission, which should be added to your application's manifest file.
 * </p>
 * 
 * A local cache of downloaded images is maintained internally to improve performance.
 */
public class VoiceMessagePlayer {
	private static final String TAG = "VoiceMessagePlayer";
//	private static BitmapCache mBitmapCache = new BitmapCache();
	private static Handler mHandler = new Handler(MainActivity.getContext().getMainLooper());
	private ChatAdapter mChatAdapter;
	private VoiceController mVoiceController;

	public VoiceMessagePlayer(VoiceController voiceController, ChatAdapter chatAdapter) {
		mChatAdapter = chatAdapter;
		mVoiceController = voiceController;
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
	public void play(VoiceController voiceController, SeekBar seekBar, SurespotMessage message) {
		VoicePlayerTask task = new VoicePlayerTask(voiceController, seekBar, message);
		seekBar.setTag(task);
//		byte[] plainAudio = message.getPlainBinaryData();
//		
//
//		if (plainAudio == null) {
//			SurespotLog.v(TAG, "audio not decrypted yet: %s", message.getIv());
//
//			forceDownload(seekBar, message);
//		}
//		else {
//			SurespotLog.v(TAG, "playing cached audio: %s", message.getIv());
//			cancelPotentialDownload(seekBar, message);
//			seekBar.setProgress(0);
//			
//			
//			message.setLoaded(true);
//			message.setLoading(false);
//
//			TextView tvTime = (TextView) ((View) seekBar.getParent()).findViewById(R.id.messageTime);
//			if (message.getDateTime() != null) {
//
//				tvTime.setText(DateFormat.getDateFormat(mChatAdapter.getContext()).format(message.getDateTime()) + " "
//						+ DateFormat.getTimeFormat(mChatAdapter.getContext()).format(message.getDateTime()));
//
//			}
//		}
	}

	/*
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear. private void
	 * forceDownload(String url, ImageView view) { forceDownload(url, view, null); }
	 */

	/**
	 * Same as download but the image is always downloaded and the cache is not used. Kept private at the moment as its interest is not clear.
	 */
//	private void forceDownload(SeekBar seekBar, SurespotMessage message) {
//		if (cancelPotentialDownload(seekBar, message)) {
//			VoicePlayerTask task = new VoicePlayerTask(seekBar, message);
//			
////			DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task, SurespotConfiguration.getImageDisplayHeight());
//	//		imageView.setImageDrawable(downloadedDrawable);
//			message.setLoaded(false);
//			message.setLoading(true);
//			SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
//		}
//	}

	/**
	 * Returns true if the current download has been canceled or if there was no download in progress on this image view. Returns false if the download in
	 * progress deals with the same url. The download is not stopped in that case.
	 */
	private boolean cancelPotentialDownload(SeekBar imageView, SurespotMessage message) {
		VoicePlayerTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

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
	public VoicePlayerTask getBitmapDownloaderTask(SeekBar imageView) {
		if (imageView != null) {
			Object oDecryptionTaskWrapper = imageView.getTag();
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
	class VoicePlayerTask implements Runnable {
		private SurespotMessage mMessage;
		private boolean mCancelled;

		public SurespotMessage getMessage() {
			return mMessage;
		}

		private final WeakReference<SeekBar> imageViewReference;
		private final WeakReference<VoiceController> voiceControllerReference;

		public VoicePlayerTask(VoiceController voiceController, SeekBar imageView, SurespotMessage message) {
			mMessage = message;
			voiceControllerReference = new WeakReference<VoiceController>(voiceController);
			imageViewReference = new WeakReference<SeekBar>(imageView);
		}

		public void cancel() {
			mCancelled = true;
		}

		@Override
		public void run() {
			voiceControllerReference.get().playVoiceMessage(imageViewReference.get(), mMessage);
			
//			
//			Bitmap bitmap = null;
//			InputStream imageStream = null;
//
//			if (mMessage.getData().startsWith("file")) {
//				try {
//					imageStream = MainActivity.getContext().getContentResolver().openInputStream(Uri.parse(mMessage.getData()));
//				}
//				catch (FileNotFoundException e) {
//					SurespotLog.w(TAG, e, "BitmapDownloaderTask");
//				}
//			}
//			else {
//				imageStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(), mMessage.getData());
//			}
//
//			if (!mCancelled && imageStream != null) {
//				PipedOutputStream out = new PipedOutputStream();
//				PipedInputStream inputStream;
//				try {
//					inputStream = new PipedInputStream(out);
//
//					EncryptionController.runDecryptTask(mMessage.getOurVersion(), mMessage.getOtherUser(), mMessage.getTheirVersion(), mMessage.getIv(),
//							new BufferedInputStream(imageStream), out);
//
//					if (mCancelled) {
//						inputStream.close();
//						mMessage.setLoaded(true);
//						mMessage.setLoading(false);
//						mChatAdapter.checkLoaded();
//						return;
//					}
//
//					byte[] bytes = Utils.inputStreamToBytes(inputStream);
//					if (mCancelled) {
//						mMessage.setLoaded(true);
//						mMessage.setLoading(false);
//						mChatAdapter.checkLoaded();
//						return;
//					}
//
//					bitmap = ChatUtils.getSampledImage(bytes);
//				}
//				catch (InterruptedIOException ioe) {
//
//					SurespotLog.w(TAG, "BitmapDownloaderTask ioe", ioe);
//
//				}
//				catch (IOException e) {
//					SurespotLog.w(TAG, "BitmapDownloaderTask e", e);
//				}
//			}
//
//			if (bitmap != null) {
//
//				mMessage.setLoaded(true);
//				mMessage.setLoading(false);
//
//				if (imageViewReference != null) {
//					final SeekBar imageView = imageViewReference.get();
//					final VoicePlayerTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
//					// Change bitmap only if this process is still associated with it
//					// Or if we don't use any bitmap to task association (NO_DOWNLOADED_DRAWABLE mode)
//					if ((VoicePlayerTask.this == bitmapDownloaderTask)) {
//
//						mHandler.post(new Runnable() {
//
//							@Override
//							public void run() {
//
//								Drawable drawable = imageView.getDrawable();
//								if (drawable instanceof DownloadedDrawable) {
//
//									imageView.clearAnimation();
//									Animation fadeIn = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_in);// new
//																																	// AlphaAnimation(0,
//																																	// 1);
//									// Animation fadeout = AnimationUtils.loadAnimation(imageView.getContext(), android.R.anim.fade_out);
//									// fadeIn.setDuration(1000);
//									imageView.startAnimation(fadeIn);
//
//								}
//								else {
//									SurespotLog.v(TAG, "clearing uploading flag");
//									// mMessage.setPlainData(null);
//									ImageViewAnimatedChange(imageView.getContext(), imageView, finalBitmap);
//								}
//
//								imageView.setImageBitmap(finalBitmap);
//								imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();
//								
//								TextView tvTime = (TextView) ((View) imageView.getParent()).findViewById(R.id.messageTime);
//								ImageView ivShareable = (ImageView) ((View) imageView.getParent()).findViewById(R.id.messageImageShareable);
//								ImageView ivNotShareable = (ImageView) ((View) imageView.getParent()).findViewById(R.id.messageImageNotShareable);
//
//								if (mMessage.isShareable()) {
//									ivShareable.setVisibility(View.VISIBLE);
//									ivNotShareable.setVisibility(View.GONE);
//								}
//								else {
//									ivShareable.setVisibility(View.GONE);
//									ivNotShareable.setVisibility(View.VISIBLE);
//								}
//
//								// if (mMessage.getErrorStatus() > 0) {
//								// UIUtils.setMessageErrorText(tvTime, mMessage);
//								// }
//								// else {
//
//								if (mMessage.getDateTime() != null) {
//
//									tvTime.setText(DateFormat.getDateFormat(MainActivity.getContext()).format(mMessage.getDateTime()) + " "
//											+ DateFormat.getTimeFormat(MainActivity.getContext()).format(mMessage.getDateTime()));
//								}
//
//								mChatAdapter.checkLoaded();
//								// }
//							}
//						});
//					}
//				}
//			}
//
		}
	}

	/**
	 * makes sure that only the last started decrypt process can bind its result, independently of the finish order. </p>
	 */
	class DecryptionTaskWrapper {
		private final WeakReference<VoicePlayerTask> decryptionTaskReference;

		public DecryptionTaskWrapper(VoicePlayerTask decryptionTask) {
			decryptionTaskReference = new WeakReference<VoicePlayerTask>(decryptionTask);
		}

		public VoicePlayerTask getDecryptionTask() {
			return decryptionTaskReference.get();
		}
	}
}
