package com.twofours.surespot;

import java.util.Timer;
import java.util.TimerTask;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;

public class MultiProgressDialog {
	private static final String TAG = "MultiProgressDialog";
	private int mProgressCounter;
	private ProgressDialog mMultiProgressDialog;
	private Context mContext;
	private String mMessage;
	private int mDelay;

	public MultiProgressDialog(Context context, String message, int delay) {
		mProgressCounter = 0;
		mContext = context;
		mMessage = message;
		mDelay = delay;
	}

	public void incrProgress() {
		mProgressCounter++;
		SurespotLog.v(TAG,"incr, progress counter: " + mProgressCounter);
		if (mProgressCounter == 1) {

			if (mMultiProgressDialog == null) {
				mMultiProgressDialog = new ProgressDialog(mContext);
				mMultiProgressDialog.setIndeterminate(true);
				// progressDialog.setTitle("loading");
				mMultiProgressDialog.setMessage(mMessage);
			}

			// only show the dialog if we haven't loaded within 500 ms
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {

				@Override
				public void run() {

					new Handler(mContext.getMainLooper()).post(new Runnable() {

						@Override
						public void run() {
							if (mProgressCounter > 0) {
								mMultiProgressDialog.show();
							}
						}
					});

				}
			}, mDelay);

		}
	}

	public void decrProgress() {
		mProgressCounter--;
		SurespotLog.v(TAG,"decr, progress counter: " + mProgressCounter);
		if (mProgressCounter == 0) {
			if (mMultiProgressDialog.isShowing()) {
				mMultiProgressDialog.dismiss();
			}
		}
	}

}
