package com.twofours.surespot.voice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder.AudioSource;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.todoroo.aacenc.AACEncoder;
import com.todoroo.aacenc.AACToM4A;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class VoiceController {
	private static final String TAG = "VoiceController";

	private static String mFileName = null;
	private static String mUsername = null;

	private static RehearsalAudioRecorder mRecorder = null;

	static TimerTask mCurrentTimeTask;
	static boolean mRecording = false;
	private static SeekBarThread mSeekBarThread;
	private static SurespotMessage mMessage;

	static Timer mTimer;
	private static File mAudioFile;
	static MediaPlayer mPlayer;
	static SeekBar mSeekBar;
	static boolean mPlaying = false;
	private static int mSampleRate;
	private static VolumeEnvelopeView mEnvelopeView;
	private static View mVoiceHeaderView;
	private static TextView mVoiceRecTimeLeftView;
	private static float mTimeLeft;

	private static Activity mActivity;

	enum State {
		INITIALIZING, READY, STARTED, RECORDING
	};

	private final static int[] sampleRates = { 44100, 22050 };
	private static State mState;
	private static AACEncoder mEncoder;
	private static int mDuration;

	static {
		mState = State.STARTED;
		mEncoder = new AACEncoder();

	}

	static int getMaxAmplitude() {
		if (mRecorder == null || mState != State.RECORDING)
			return 0;
		return mRecorder.getMaxAmplitude();
	}

	private static void startTimer(final Activity activity) {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer.purge();
		}

		final int rate = 50;
		mTimeLeft = 10000;
		mTimer = new Timer();
		mCurrentTimeTask = new TimerTask() {
			public void run() {
				activity.runOnUiThread(new Runnable() {
					public void run() {

						if (mState == State.RECORDING) {

							mTimeLeft -= rate;

							final float currentTimeLeft = mTimeLeft;

							if (currentTimeLeft < 0) {
								stopRecording(mActivity);
								return;
							}
							mEnvelopeView.setNewVolume(getMaxAmplitude(), true);

							// if we're at a half second boundary, update time display
							if (currentTimeLeft % 500 == 0) {

								mVoiceRecTimeLeftView.post(new Runnable() {

									@Override
									public void run() {
										mVoiceRecTimeLeftView.setText(Float.toString(currentTimeLeft / 1000));
									}
								});
							}

							return;
						}

						mEnvelopeView.clearVolume();
					}
				});
			}
		};
		mTimer.scheduleAtFixedRate(mCurrentTimeTask, 0, rate);

	}

	private synchronized static void startRecordingInternal(final Activity activity) {
		if (mState != State.STARTED)
			return;

		try {
			// MediaRecorder has major delay issues on gingerbread so we record raw PCM then convert natively to m4a
			if (mFileName != null) {
				 new File(mFileName).delete();
			}

			// create a temp file to hold the uncompressed audio data
			mFileName = File.createTempFile("voice", ".wav").getAbsolutePath();
			SurespotLog.v(TAG, "recording to: %s", mFileName);

			int i = 0;
			mSampleRate = sampleRates[0];

			do {
				if (mRecorder != null)
					mRecorder.release();
				mSampleRate = sampleRates[i];
				mRecorder = new RehearsalAudioRecorder(true, AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT);
			}
			while ((++i < sampleRates.length) & !(mRecorder.getState() == RehearsalAudioRecorder.State.INITIALIZING));

			SurespotLog.v(TAG, "sampleRate: %d", mSampleRate);
			mEnvelopeView.setVisibility(View.VISIBLE);
			mVoiceHeaderView.setVisibility(View.VISIBLE);
			mVoiceRecTimeLeftView.setText("10.0");
			mEnvelopeView.clearVolume();
			mRecorder.setOutputFile(mFileName);
			mRecorder.prepare();
			mRecorder.start();

			startTimer(activity);
			mState = State.RECORDING;
			// Utils.makeToast(activity, "sample rate: " + mSampleRate);
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}

	}

	private synchronized static void stopRecordingInternal() {
		// state must be RECORDING
		if (mState != State.RECORDING)
			return;
		try {

			mTimer.cancel();
			mTimer.purge();
			mTimer = null;
			mCurrentTimeTask = null;
			mRecorder.stop();
			mRecorder.release();
			mRecorder = null;

			mState = State.STARTED;
		}
		catch (RuntimeException stopException) {

		}

	}

	// Play is over, cleanup

	private synchronized static void playCompleted() {

		mSeekBarThread.completed();
		mMessage.setPlayMedia(false);

		if (mPlayer != null) {
			mPlayer.setOnCompletionListener(null);
			mPlayer.release();
			mPlayer = null;
		}

		mMessage = null;
		if (mAudioFile != null) {
			mAudioFile.delete();
		}

		mPlaying = false;
		updatePlayControls();

	}

	public synchronized void destroy() {
		if (mRecorder != null) {
			mRecorder.release();
			mRecorder = null;
		}

		playCompleted();

	}

	public static synchronized void startRecording(Activity context, String username) {
		if (!mRecording) {
			stopPlaying();

			mActivity = context;
			mUsername = username;
			mEnvelopeView = (VolumeEnvelopeView) context.findViewById(R.id.volume_envelope);
			mVoiceHeaderView = (View) context.findViewById(R.id.voiceHeader);
			mVoiceRecTimeLeftView = (TextView) context.findViewById(R.id.voiceRecTimeLeft);
			Utils.makeToast(context, "recording");
			startRecordingInternal(context);
			mRecording = true;
		}

	}

	public synchronized static void stopRecording(Activity activity) {
		if (mRecording) {
			stopRecordingInternal();
			sendVoiceMessage(activity);
			Utils.makeToast(activity, "encrypting and transmitting");
			VolumeEnvelopeView mEnvelopeView = (VolumeEnvelopeView) activity.findViewById(R.id.volume_envelope);
			mEnvelopeView.setVisibility(View.GONE);
			mVoiceHeaderView.setVisibility(View.GONE);
			mRecording = false;
		}
	}

	private synchronized static void sendVoiceMessage(final Activity activity) {
		new AsyncTask<Void, Void, byte[]>() {

			@Override
			protected byte[] doInBackground(Void... params) {
				// convert to AAC
				FileInputStream fis;
				try {
					fis = new FileInputStream(mFileName);

					String outFile = File.createTempFile("voice", ".aac").getAbsolutePath();
					mEncoder.init(44100, 1, mSampleRate, 16, outFile);

					mEncoder.encode(Utils.inputStreamToBytes(fis));
					mEncoder.uninit();

					
					// convert to m4a (gingerbread can't play the AAC for some bloody reason).
					final String m4aFile = File.createTempFile("voice", ".m4a").getAbsolutePath();
					new AACToM4A().convert(activity, outFile, m4aFile);


					FileInputStream m4aStream = new FileInputStream(m4aFile);
					byte[] data = Utils.inputStreamToBytes(m4aStream);
					
					// delete files
					new File(outFile).delete();					
					new File(mFileName).delete();
					new File(m4aFile).delete();
				
					return data;
				}

				catch (IOException e) {
					SurespotLog.w(TAG, e, "sendVoiceMessage");
				}
				return null;
			}

			protected void onPostExecute(byte[] data) {
				if (data != null) {

					MainActivity.getChatController().sendVoiceMessage(mUsername, data, SurespotConstants.MimeTypes.M4A);

				}
				else {
					Utils.makeToast(activity, "error sending message");
				}

			};
		}.execute();

	}

	public synchronized static void playVoiceMessage(Context context, final SeekBar seekBar, final SurespotMessage message) {
		if (mRecording) {
			return;
		}

		SurespotLog.v(TAG, "playVoiceMessage");

		if (message.getPlainBinaryData() == null) {
			return;
		}

		boolean differentMessage = !message.equals(mMessage);

		stopPlaying();

		if (!mPlaying && differentMessage) {
			mPlaying = true;
			mSeekBar = seekBar;
			mMessage = message;
			mSeekBar.setMax(100);

			if (mSeekBarThread == null) {

				mSeekBarThread = new SeekBarThread();
			}

			mPlayer = new MediaPlayer();
			try {
				if (mAudioFile != null) {
					mAudioFile.delete();
				}

				mAudioFile = File.createTempFile("sound", ".m4a");

				FileOutputStream fos = new FileOutputStream(mAudioFile);
				fos.write(message.getPlainBinaryData());
				fos.close();

				mPlayer.setDataSource(mAudioFile.getAbsolutePath());
				mPlayer.prepare();
				mDuration = mPlayer.getDuration();
			}

			catch (Exception e) {
				SurespotLog.w(TAG, e, "playVoiceMessage error");
				playCompleted();
				return;
			}

			new Thread(mSeekBarThread).start();
			mPlayer.setOnCompletionListener(new OnCompletionListener() {

				@Override
				public void onCompletion(MediaPlayer mp) {

					playCompleted();
				}
			});

			mPlayer.start();
			updatePlayControls();
		}

	}

	private static void stopPlaying() {
		if (mPlaying) {
			if (mPlayer != null) {
				mPlayer.stop();
			}
			playCompleted();
			if (mSeekBar != null) {
				setProgress(mSeekBar, 0);
			}

		}
	}

	public static synchronized void attach(final SeekBar seekBar) {
		if (isCurrentMessage(seekBar)) {
			mSeekBar = seekBar;
			updatePlayControls();
		}
		else {
			setProgress(seekBar, 0);
			updatePlayControls();
		}
	}

	private synchronized static void updatePlayControls() {

		ImageView voicePlay = null;
		ImageView voiceStop = null;

		if (mSeekBar != null) {
			voicePlay = (ImageView) ((View) mSeekBar.getParent()).findViewById(R.id.voicePlay);
			voiceStop = (ImageView) ((View) mSeekBar.getParent()).findViewById(R.id.voiceStop);
		}

		if (voicePlay != null && voiceStop != null) {
			if (isCurrentMessage()) {

				voicePlay.setVisibility(View.GONE);
				voiceStop.setVisibility(View.VISIBLE);
			}
			else {
				voicePlay.setVisibility(View.VISIBLE);
				voiceStop.setVisibility(View.GONE);
			}
		}
	}

	private static void setProgress(final SeekBar seekBar, final int progress) {
		if (seekBar == null)
			return;
		seekBar.post(new Runnable() {

			@Override
			public void run() {
				// SurespotLog.v(TAG, "Setting progress to %d", progress);
				seekBar.setProgress(progress);

			}

		});
	}

	private static class SeekBarThread implements Runnable {
		private boolean mRun = true;

		@Override
		public void run() {
			mRun = true;
			while (mRun) {
				int progress = 0;

				if (mDuration > -1) {

					if (isCurrentMessage()) {

						int currentPosition = mPlayer.getCurrentPosition();

						progress = (int) (((float) currentPosition / (float) mDuration) * 101);
						// SurespotLog.v(TAG, "SeekBarThread: %s, currentPosition: %d, duration: %d, percent: %d", mSeekBar, currentPosition, mDuration,
						// progress);

						// TODO weight by length
						if (progress < 0)
							progress = 0;
						if (progress > 95)
							progress = 100;

						// SurespotLog.v(TAG, "setting seekBar: %s, progress: %d", mSeekBar, progress);

						if (currentPosition < mDuration) {
							if (!mRun) {
								break;
							}

						}
					}

					setProgress(mSeekBar, progress);
				}

				try {
					Thread.sleep(30);
				}
				catch (InterruptedException e) {
					mRun = false;
					SurespotLog.w(TAG, e, "SeekBarThread interrupted");
				}
			}

			setProgress(mSeekBar, 0);
		}

		public void completed() {
			SurespotLog.v(TAG, "SeekBarThread completed");
			mRun = false;

		}
	}

	private static boolean isCurrentMessage() {
		if (mSeekBar != null) {
			return isCurrentMessage(mSeekBar);
		}
		return false;

	}

	private static boolean isCurrentMessage(SeekBar seekBar) {
		if (seekBar == null) {
			return false;
		}

		// if the message is attached to the seekbar
		WeakReference<SurespotMessage> ref = (WeakReference<SurespotMessage>) seekBar.getTag(R.id.tagMessage);

		SurespotMessage seekBarMessage = null;
		if (ref != null) {
			seekBarMessage = ref.get();
		}

		if (seekBarMessage != null && seekBarMessage.equals(mMessage) && mPlaying) { //
			return true;
		}
		else {
			return false;
		}
	}

	public static void pause() {
		stopPlaying();

	}

}
