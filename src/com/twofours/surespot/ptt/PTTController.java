package com.twofours.surespot.ptt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class PTTController {
	private static final String TAG = "PTTController";

	private static String mFileName = null;
	private static String mUsername = null;

	private MediaRecorder mRecorder = null;

	private MediaPlayer mPlayer = null;

	private ChatController mChatController;
	private NetworkController mNetworkController;
	boolean mRecording = false;

	public PTTController(ChatController chatController, NetworkController networkController) {
		mChatController = chatController;
		mNetworkController = networkController;
		mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
		mFileName += "/audiorecordtest.m4a";
	}

	private void startRecording() {
		mRecorder = new MediaRecorder();
		mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		mRecorder.setOutputFile(mFileName);
		mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

		try {
			mRecorder.prepare();
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}

		mRecorder.start();
	}

	private void stopRecordingInternal() {
		mRecorder.stop();
		// mRecorder.reset();
		// mRecorder.release();
		// mRecorder = null;
	}

	private void startPlaying(String path) {
		mPlayer = new MediaPlayer();
		try {
			mPlayer.setDataSource(path);
			mPlayer.prepare();
			mPlayer.start();
			mPlayer.setOnCompletionListener(new OnCompletionListener() {

				@Override
				public void onCompletion(MediaPlayer mp) {
					// encrypt and send

				}
			});
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}
	}

	private void stopPlaying() {
		mPlayer.release();
		mPlayer = null;
	}

	public void destroy() {
		if (mRecorder != null) {
			mRecorder.release();
			mRecorder = null;
		}

		if (mPlayer != null) {
			mPlayer.release();
			mPlayer = null;
		}

		mChatController = null;
		mNetworkController = null;
	}

	public void startRecording(String username) {

		if (!mRecording) {
			mUsername = username;
			startRecording();

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
			// //startPlaying();
			// }
			// };

			// startPlaying();
			mRecording = false;

		}
	}

	public void sendPTT(Activity activity, IAsyncCallback<Boolean> callback) {

		ChatUtils.uploadPTTAsync(activity, mChatController, mNetworkController, Uri.fromFile(new File(mFileName)), mUsername, callback);
	}

	public void playPTT(SurespotMessage message) {
		// download and decrypt
		InputStream imageStream = mNetworkController.getFileStream(MainActivity.getContext(), message.getData());

		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream inputStream;
		try {
			inputStream = new PipedInputStream(out);

			EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(), message.getTheirVersion(), message.getIv(),
					new BufferedInputStream(imageStream), out);

			byte[] soundbytes = Utils.inputStreamToBytes(inputStream);
			File tempFile = File.createTempFile("sound", "m4a");
			FileOutputStream fos = new FileOutputStream(tempFile);
			fos.write(soundbytes);
			fos.close();
			startPlaying(tempFile.getAbsolutePath());

		}
		catch (InterruptedIOException ioe) {

			SurespotLog.w(TAG, ioe, "handleMessage");

		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "handleMessage");
		}

	}
}
