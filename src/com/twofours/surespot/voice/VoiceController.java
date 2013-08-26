package com.twofours.surespot.voice;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.HashMap;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder.AudioSource;
import android.net.Uri;
import android.os.AsyncTask;

import com.todoroo.aacenc.AACEncoder;
import com.todoroo.aacenc.AACToM4A;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class VoiceController {
	private static final String TAG = "PTTController";

	private static String mFileName = null;
	private static String mUsername = null;

	private RehearsalAudioRecorder mRecorder = null;

	private ChatController mChatController;
	private NetworkController mNetworkController;
	private HashMap<String, MediaPlayer> mMediaPlayers;

	boolean mRecording = false;

	enum State {
		INITIALIZING, READY, STARTED, RECORDING
	};

	private final static int[] sampleRates = { 44100, 22050, 11025, 8000 };

	private State mState;
	private AACEncoder mEncoder;

	public VoiceController(ChatController chatController, NetworkController networkController) {
		mChatController = chatController;
		mNetworkController = networkController;
		mState = State.STARTED;
		mEncoder = new AACEncoder();
		mMediaPlayers = new HashMap<String, MediaPlayer>();
		// mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
		// mFileName += "/audiorecordtest.m4a";
	}

	private void startRecording(Activity context) {
		if (mState != State.STARTED)
			return;

		try {
			/*
			 * Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION); context.startActivityForResult(intent, 30);
			 */

			mFileName = File.createTempFile("ptt_rec_", ".wav").getAbsolutePath();
			SurespotLog.v(TAG, "recording to: %s", mFileName);

			int i = 0;
			int sampleRate = 44100;

			do {
				if (mRecorder != null)
					mRecorder.release();
				sampleRate = sampleRates[i];
				mRecorder = new RehearsalAudioRecorder(true, AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
						AudioFormat.ENCODING_PCM_16BIT);
			}
			while ((++i < sampleRates.length) & !(mRecorder.getState() == RehearsalAudioRecorder.State.INITIALIZING));

			//
			// mRecorder = new RehearsalAudioRecorder(true, AudioSource.MIC, 8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
			// AudioFormat.ENCODING_PCM_8BIT);
			//
			// mRecorder = new MediaRecorder();
			// mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			// mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			// FileOutputStream fos = new FileOutputStream(mFileName);
			// File file = new File(mFileName);
			// file.setReadable(true, false);
			mRecorder.setOutputFile(mFileName);
			// fos.close();
			// mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			mRecorder.prepare();
			mRecorder.start();
			mState = State.RECORDING;
			Utils.makeToast(context, "sample rate: " + sampleRate);
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}

	}

	private void stopRecordingInternal() {
		// state must be RECORDING
		if (mState != State.RECORDING)
			return;
		try {

			mRecorder.stop();
			// mRecorder.reset();
			mRecorder.release();
			// mRecorder = null;
			mState = State.STARTED;
		}
		catch (RuntimeException stopException) {

		}

	}

	// Play unencrypted audio file from path and optionally delete it

	private synchronized void startPlaying(final String path, final boolean delete) {
		// see if we're already running

		if (mMediaPlayers.containsKey(path)) {
			return;
		}
		MediaPlayer mPlayer = new MediaPlayer();
		mMediaPlayers.put(path, mPlayer);
		try {
			mPlayer.setDataSource(path);
			mPlayer.prepare();
			mPlayer.start();
			mPlayer.setOnCompletionListener(new OnCompletionListener() {

				@Override
				public void onCompletion(MediaPlayer mp) {

					stopPlaying(path, delete);
				}
			});
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}
	}

	private synchronized void stopPlaying(String path, boolean delete) {
		MediaPlayer mp = mMediaPlayers.remove(path);
		mp.stop();
		mp.release();
		mp = null;

		if (delete) {
			new File(path).delete();
		}

	}

	public synchronized void destroy() {
		if (mRecorder != null) {
			mRecorder.release();
			mRecorder = null;
		}

		for (MediaPlayer mp : mMediaPlayers.values()) {
			mp.stop();
			mp.release();
		}

		mMediaPlayers.clear();

		mChatController = null;
		mNetworkController = null;
	}

	public void startRecording(Activity context, String username) {

		if (!mRecording) {
			mUsername = username;
			startRecording(context);

			mRecording = true;
		}

	}

	public void stopRecording() {
		if (mRecording) {
			stopRecordingInternal();
			//
			// new AsyncTask<Void, Void, Void>() {
			// @Override
			// protected Void doInBackground(Void... params) {
			// try {
			// Thread.sleep(250);
			// }
			// catch (InterruptedException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
			// return null;
			// }
			//
			// @Override
			// protected void onPostExecute(Void result) {
			// startPlaying(mFileName, false);
			// mRecording = false;
			// }
			// };

			// startPlaying(mFileName, false);
			mRecording = false;

		}
	}

	public void sendPTT(Activity activity, IAsyncCallback<Boolean> callback) {
		// convert to AAC
		FileInputStream fis;
		try {
			fis = new FileInputStream(mFileName);
			Date start = new Date();

			String outFile = File.createTempFile("voice", ".aac").getAbsolutePath();
			mEncoder.init(16000, 1, 44100, 16, outFile);

			mEncoder.encode(Utils.inputStreamToBytes(fis));
			mEncoder.uninit();

			String m4aFile = File.createTempFile("voice", ".m4a").getAbsolutePath();
			new AACToM4A().convert(activity, outFile, m4aFile);

			SurespotLog.v(TAG, "AAC encoding end, time: %d ms", (new Date().getTime() - start.getTime()));

			ChatUtils.uploadPTTAsync(activity, mChatController, mNetworkController, Uri.fromFile(new File(m4aFile)), mUsername, callback);
		}

		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void playPTT(final SurespotMessage message) {
		final File tempFile;
		try {
			// create temp file to save un-encrypted data to (MediaPlayer can't stream from uh a Stream (InputStream) for some ass backwards reason.)
			tempFile = File.createTempFile("sound", ".m4a");
		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "playPTT");
			return;
		}
		// download and decrypt
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				InputStream imageStream = mNetworkController.getFileStream(MainActivity.getContext(), message.getData());

				PipedOutputStream out = new PipedOutputStream();
				PipedInputStream inputStream;
				try {
					inputStream = new PipedInputStream(out);

					EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(), message.getTheirVersion(), message.getIv(),
							new BufferedInputStream(imageStream), out);

					byte[] soundbytes = Utils.inputStreamToBytes(inputStream);

					//
					FileOutputStream fos = new FileOutputStream(tempFile);
					fos.write(soundbytes);
					fos.close();

					return true;

				}
				catch (InterruptedIOException ioe) {

					SurespotLog.w(TAG, ioe, "playPTT");

				}
				catch (IOException e) {
					SurespotLog.w(TAG, e, "playPTT");
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				if (result) {
					startPlaying(tempFile.getAbsolutePath(), true);
				}
			}
		}.execute();

	}
}
