package com.twofours.surespot.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class SoftKeyboardLayout extends RelativeLayout {


	// thanks to http://stackoverflow.com/questions/7300497/adjust-layout-when-soft-keyboard-is-on
	public SoftKeyboardLayout(Context context) {
		super(context);
	}

	public SoftKeyboardLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

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

		void onLayoutMeasure();

	}
}
