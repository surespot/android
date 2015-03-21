package com.twofours.surespot.ui;

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;

//import android.app.AlertDialog;

public class SingleProgressDialog {
	private static final String TAG = "SingleProgressDialog";
	private AlertDialog mSingleProgressDialog;
	private Context mContext;
	private String mMessage;
	private int mDelay;
	private ImageView mImageView;
	private Animation mAnimation;
	private Timer mTimer;
	private TimerTask mTimerTask;

	public SingleProgressDialog(Context context, String message, int delay) {
		mTimer = new Timer();
		mContext = context;
		mMessage = message;
		mDelay = delay;
	}

	public synchronized void show() {
		SurespotLog.v(TAG, "show");

		if (mSingleProgressDialog == null) {

			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_progress, null, false);

			TextView text = (TextView) layout.findViewById(R.id.text);
			text.setText(mMessage);

			mImageView = (ImageView) layout.findViewById(R.id.image);
			mAnimation = AnimationUtils.loadAnimation(mContext, R.anim.progress_anim);
			mAnimation.setDuration(1000);
			//
			// mSingleProgressDialog.setIndeterminate(true);
			// mSingleProgressDialog.setIcon(R.drawable.surespot_logo);

			AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

			mSingleProgressDialog = builder.create();
			mSingleProgressDialog.setView(layout, 0, 0, 0, 0);
			mSingleProgressDialog.setCanceledOnTouchOutside(false);
		
		}


		mImageView.clearAnimation();
		mImageView.startAnimation(mAnimation);

		// only show the dialog if we haven't loaded within 500 ms

		if (mTimerTask != null) {
			mTimerTask.cancel();
		}

		mTimerTask = new TimerTask() {

			@Override
			public void run() {

				new Handler(mContext.getMainLooper()).post(new Runnable() {

					@Override
					public void run() {

						mSingleProgressDialog.show();

					}
				});

			}
		};

		mTimer.schedule(mTimerTask, mDelay);

	}

	
	public synchronized void hide() {

		
		if (mTimerTask != null) {
			mTimerTask.cancel();
			mTimerTask = null;
		}
		SurespotLog.v(TAG, "hide");

		if (mSingleProgressDialog != null && mSingleProgressDialog.isShowing()) {
			try {
				mSingleProgressDialog.dismiss();
			}
			catch (Exception e) {
				SurespotLog.w(TAG, e, "hide");
			}
		}

	}

}
