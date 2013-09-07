package com.twofours.surespot.voice;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.ref.WeakReference;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.AsyncTask;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class VoiceMessageContainer {
	private WeakReference<SeekBar> mSeekBarReference;
	// private SeekBar mSeekBar;
	private MediaPlayer mPlayer;
	private SurespotMessage mMessage;
	private static final String TAG = "VoiceMessageContainer";
	private NetworkController mNetworkController;
	private File mAudioFile;
	private SeekBarThread mSeekBarThread;
	private int mDuration;
	private boolean mLoaded = false;
	private boolean mLoading = false;
	private boolean mPlaying = false;

	public VoiceMessageContainer(NetworkController networkController, SurespotMessage message) {
		mNetworkController = networkController;
		mMessage = message;
		mDuration = -1;

	}

	public synchronized void prepareAudio(final SeekBar seekBar, final IAsyncCallback<Integer> callback) {
		if (!mLoaded && !mLoading) {
			mLoading = true;

			try {
				// create temp file to save un-encrypted audio data to (MediaPlayer can't stream from uh a Stream (InputStream) for some ass backwards reason).
				// TODO look into proxying this through local http server to be able to use a uri or some other socket/fd based solution
				if (mAudioFile == null) {
					mAudioFile = File.createTempFile("sound", ".m4a");
				}
			}
			catch (IOException e) {
				// TODO tell user
				SurespotLog.w(TAG, e, "playVoiceMessage");
				if (callback != null) {
					callback.handleResponse(-1);
				}
				return;
			}
			// download and decrypt
			new AsyncTask<Void, Void, Integer>() {
				@Override
				protected Integer doInBackground(Void... params) {
					byte[] soundbytes = mMessage.getPlainBinaryData();
					// TODO - progress

					if (soundbytes == null) {
						// see if the data has been sent to us inline
						InputStream voiceStream = null;
						if (mMessage.getInlineData() == null) {
							SurespotLog.v(TAG, "getting voice stream from cloud");
							voiceStream = mNetworkController.getFileStream(MainActivity.getContext(), mMessage.getData());

						}
						else {
							SurespotLog.v(TAG, "getting voice stream from inlineData");
							voiceStream = new ByteArrayInputStream(mMessage.getInlineData());

						}

						if (voiceStream != null) {

							PipedOutputStream out = new PipedOutputStream();
							PipedInputStream inputStream;
							try {
								inputStream = new PipedInputStream(out);

								EncryptionController.runDecryptTask(mMessage.getOurVersion(), mMessage.getOtherUser(), mMessage.getTheirVersion(),
										mMessage.getIv(), voiceStream, out);

								soundbytes = Utils.inputStreamToBytes(inputStream);

							}
							catch (InterruptedIOException ioe) {

								SurespotLog.w(TAG, ioe, "playVoiceMessage");

							}
							catch (IOException e) {
								SurespotLog.w(TAG, e, "playVoiceMessage");
							}
						}
					}
					else {
						SurespotLog.v(TAG, "getting voice stream from cache");
					}
					if (soundbytes != null) {
						FileOutputStream fos;
						try {
							fos = new FileOutputStream(mAudioFile);
							fos.write(soundbytes);
							fos.close();

							mMessage.setPlainBinaryData(soundbytes);

							// figure out duration
							String path = mAudioFile.getAbsolutePath();
							mPlayer = new MediaPlayer();
							mPlayer.setDataSource(path);
							mPlayer.prepare();

							mDuration = mPlayer.getDuration();

							SurespotLog.v(TAG, "duration: %d", mDuration);
							SurespotLog.v(TAG, "seekbar same: %b", seekBar.getTag() == VoiceMessageContainer.this);
							mLoaded = true;
							mLoading = false;
							return mDuration;

						}
						catch (IOException e) {
							SurespotLog.w(TAG, e, "playVoiceMessage");
						}
					}

					return -1;
				}

				@Override
				protected void onPostExecute(Integer duration) {
					if (callback != null) {
						callback.handleResponse(duration);
					}

				}
			}.execute();
		}
		else {
			callback.handleResponse(mDuration);
		}
	}

	private synchronized void prepAndPlay(int progress) {
		if (!mPlaying && mLoaded) {
			mPlaying = true;
			// String path = mAudioFile.getAbsolutePath();
			// try {

			if (mSeekBarThread == null) {

				mSeekBarThread = new SeekBarThread();
			}

			mPlayer.reset();
			try {
				mPlayer.setDataSource(mAudioFile.getAbsolutePath());
				mPlayer.prepare();
			}
			catch (Exception e) {
				SurespotLog.w(TAG, e, "prepAndPlay error");
				return;
			}

			if (progress > 0) {
				mPlayer.seekTo(progress);
			}

			new Thread(mSeekBarThread).start();
			mPlayer.start();

			mPlayer.setOnCompletionListener(new OnCompletionListener() {

				@Override
				public void onCompletion(MediaPlayer mp) {

					playCompleted();
				}
			});
		}
	}

	// catch (IOException e) {
	// SurespotLog.e(TAG, e, "preparePlayer failed");
	// }

	// }

	public void play(SeekBar seekbar, final int progress) {
		prepareAudio(seekbar, new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer duration) {
				if (duration > 0) {
					prepAndPlay(progress);
				}

			}
		});

	}

	private void playCompleted() {
		mSeekBarThread.completed();
		mMessage.setPlayMedia(false);
		mPlaying = false;
	}

	private class SeekBarThread implements Runnable {
		private boolean mRun = true;

		@Override
		public void run() {
			if (mSeekBarReference != null) {
				try {
					SeekBar seekBar = mSeekBarReference.get();
					if (seekBar == null) {
						return;
					}

					if (seekBar.getTag() != VoiceMessageContainer.this) {
						return;
					}
					// VoiceMessageContainer seekContainer = (VoiceMessageContainer) seekBar.getTag();

					// if (VoiceMessageContainer.this == seekContainer) {

					mRun = true;

					seekBar = mSeekBarReference.get();
					if (seekBar == null) {
						return;
					}

					if (seekBar.getTag() != VoiceMessageContainer.this) {
						return;
					}
					seekBar.setMax(100);

					while (mRun) {

						int progress = 0;
						if (mDuration > -1) {
							int currentPosition = mPlayer.getCurrentPosition();

							progress = (int) (((float) currentPosition / (float) mDuration) * 101);
							SurespotLog.v(TAG, "SeekBarThread: %s, currentPosition: %d, duration: %d, percent: %d", this, currentPosition, mDuration, progress);
							if (progress < 0)
								progress = 0;
							if (progress > 90)
								progress = 100;

							seekBar = mSeekBarReference.get();

							if (seekBar != null && seekBar.getTag() == VoiceMessageContainer.this) {

								SurespotLog.v(TAG, "setting seekBar: %s, progress: %d", seekBar, progress);

								if (currentPosition < mDuration) {
									seekBar.setProgress(progress);

								}
								else {
									seekBar.setProgress(0);
								}
							}

						}
						try {
							Thread.sleep(100);
						}
						catch (InterruptedException e) {
							e.printStackTrace();
						}

						if (progress == 100) {
							completed();
						}

					}
				}
				catch (Exception e) {
					SurespotLog.w(TAG, e, "SeekBarThread");
				}
			}

		}

		public void completed() {
			SurespotLog.v(TAG, "SeekBarThread completed");
			mRun = false;
			mSeekBarReference.get().setProgress(100);
		}

		public void reset() {
			SurespotLog.v(TAG, "SeekBarThread reset");
			mRun = false;
			mSeekBarReference.get().setProgress(100);
		}
	}

	private class MyOnSeekBarChangedListener implements OnSeekBarChangeListener {

		@Override
		public synchronized void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
			if (seekBar.getTag() != VoiceMessageContainer.this) {
				return;
			}

			if (fromTouch && mDuration > -1 && mLoaded) {
				SurespotLog.v(TAG, "onProgressChanged, progress: %d, seekBar: %s", progress, seekBar);
				int time = (int) (mPlayer.getDuration() * (float) progress / 101);
				// if (mVoiceController.)

				if (mPlayer.isPlaying()) {
					SurespotLog.v(TAG, "onProgressChanged,  seekingTo: %d", progress);
					mPlayer.seekTo(time);

				}
				else {
					SurespotLog.v(TAG, "onProgressChanged,  playing from: %d", progress);
					play(seekBar, time);

				}
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// Empty method
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// Empty method
		}
	}

	public void attach(SeekBar seekBar) {
		mSeekBarReference = new WeakReference<SeekBar>(seekBar);

		// seekBar.setOnSeekBarChangeListener(new MyOnSeekBarChangedListener());
	}

	public int getDuration() {
		return mDuration;
	}
};
