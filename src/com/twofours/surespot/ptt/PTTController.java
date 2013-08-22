package com.twofours.surespot.ptt;

import java.io.IOException;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.Environment;

import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.NetworkController;

public class PTTController {
	private static final String TAG = "PTTController";

	private static String mFileName = null;

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

	private void startPlaying() {
		mPlayer = new MediaPlayer();
		try {
			mPlayer.setDataSource(mFileName);
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
//					//startPlaying();
//				}
//			};

			
			startPlaying();
			mRecording = false;

		}
	}
}
