package com.twofours.surespot.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.RelativeLayout;

import com.twofours.surespot.activities.MainActivity;

public class MainActivityLayout extends RelativeLayout {
	MainActivity mMainActivity;

	// thanks to http://stackoverflow.com/questions/7300497/adjust-layout-when-soft-keyboard-is-on
	public MainActivityLayout(Context context) {
		super(context);
	}

	public MainActivityLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	public void setMainActivity(MainActivity mainActivity) {
		mMainActivity = mainActivity;
	}

	private OnMeasureListener onSoftKeyboardListener;

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		if (onSoftKeyboardListener != null) {
			onSoftKeyboardListener.onLayoutMeasure();
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	public final void setOnSoftKeyboardListener(final OnMeasureListener listener) {
		this.onSoftKeyboardListener = listener;
	}

	public interface OnMeasureListener {

		public void onLayoutMeasure();

	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if (mMainActivity != null) {

			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
				return mMainActivity.backButtonPressed();
			}
		}

		return super.dispatchKeyEventPreIme(event);
	}
}
