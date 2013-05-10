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

public class MultiProgressDialog {
	private static final String TAG = "MultiProgressDialog";
	private int mProgressCounter;
	private AlertDialog mMultiProgressDialog;
	private Context mContext;
	private String mMessage;
	private int mDelay;
	private ImageView mImageView;
	private Animation mAnimation;

	public MultiProgressDialog(Context context, String message, int delay) {
		mProgressCounter = 0;
		mContext = context;
		mMessage = message;
		mDelay = delay;
	}

	public synchronized void incrProgress() {
		mProgressCounter++;
		SurespotLog.v(TAG, "incr, progress counter: " + mProgressCounter);
		if (mProgressCounter == 1) {

			if (mMultiProgressDialog == null) {
				
				
				LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				View layout = inflater.inflate(R.layout.dialog_progress, null, false);
				
				
				TextView text = (TextView) layout.findViewById(R.id.text);
				text.setText(mMessage);
				
								
				mImageView = (ImageView) layout.findViewById(R.id.image);
				mAnimation = AnimationUtils.loadAnimation(mContext, R.anim.progress_anim);
				mAnimation.setDuration(1000);
//					
			//	mMultiProgressDialog.setIndeterminate(true);
			//	mMultiProgressDialog.setIcon(R.drawable.surespot_logo);
				
				AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
				
				
				mMultiProgressDialog = builder.create();
				mMultiProgressDialog.setView(layout, 0, 0, 0, 0);
				mMultiProgressDialog.setCanceledOnTouchOutside(false);
							
				
//		
				// progressDialog.setTitle("loading");
			//	mMultiProgressDialog.setMessage(mMessage);
			}
			
			
			mImageView.clearAnimation();
			mImageView.startAnimation(mAnimation);

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

	public synchronized void decrProgress() {
		mProgressCounter--;
		SurespotLog.v(TAG, "decr, progress counter: " + mProgressCounter);
		if (mProgressCounter == 0) {
			if (mMultiProgressDialog.isShowing()) {
				try {
					mMultiProgressDialog.dismiss();
				}
				catch (Exception e) {
					SurespotLog.w(TAG, "decrProgress", e);
				}
			}
		}
	}

}
