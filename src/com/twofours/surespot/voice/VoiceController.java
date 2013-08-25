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

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;

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

	private MediaRecorder mRecorder = null;

	private MediaPlayer mPlayer = null;

	private ChatController mChatController;
	private NetworkController mNetworkController;
	boolean mRecording = false;

	public VoiceController(ChatController chatController, NetworkController networkController) {
		mChatController = chatController;
		mNetworkController = networkController;
		// mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
		// mFileName += "/audiorecordtest.m4a";
	}

	private void startRecording() {

		try {
			mFileName = File.createTempFile("ptt_rec_", ".m4a").getAbsolutePath();
			SurespotLog.v(TAG, "recording to: %s", mFileName);
			mRecorder = new MediaRecorder();
			mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			//FileOutputStream fos = new FileOutputStream(mFileName);
			// File file = new File(mFileName);
			// file.setReadable(true, false);
			mRecorder.setOutputFile(mFileName);
			//fos.close();
			mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			mRecorder.prepare();
			mRecorder.start();
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}

	}

	private void stopRecordingInternal() {
		try {
			mRecorder.stop();
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
		}
		catch (RuntimeException stopException) {

		}
		
	}

	private void startPlaying(final String path, final boolean delete) {
		mPlayer = new MediaPlayer();
		try {
			File file = new File(path);
			// file.setReadable(true, false);
			FileInputStream fis = new FileInputStream(path);
			mPlayer.setDataSource(fis.getFD(), 0, file.length());
			fis.close();
			mPlayer.prepare();
			mPlayer.start();
			mPlayer.setOnCompletionListener(new OnCompletionListener() {

				@Override
				public void onCompletion(MediaPlayer mp) {

					//mp.
					stopPlaying(path, delete);
				}
			});
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "prepare() failed");
		}
	}

	private void stopPlaying(String path, boolean delete) {
		mPlayer.release();
		mPlayer = null;

		if (delete) {
			new File(path).delete();
		}

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
//			new AsyncTask<Void, Void, Void>() {
//				@Override
//				protected Void doInBackground(Void... params) {
//					try {
//						Thread.sleep(250);
//					}
//					catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					return null;
//				}
//
//				@Override
//				protected void onPostExecute(Void result) {
//					startPlaying(mFileName, false);
//					mRecording = false;
//				}
//			};

			//startPlaying(mFileName, false);
			mRecording = false;

		}
	}

	public void sendPTT(Activity activity, IAsyncCallback<Boolean> callback) {

		ChatUtils.uploadPTTAsync(activity, mChatController, mNetworkController, Uri.fromFile(new File(mFileName)), mUsername, callback);
	}

	public void playPTT(final SurespotMessage message) {
		final File tempFile;
		try {
			tempFile = File.createTempFile("sound", ".m4a");
			// tempFile.setReadable(true, false);
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
