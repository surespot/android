package com.twofours.surespot.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import com.twofours.surespot.common.SurespotLog;

public class ChatEditText extends EditText {

	private static final String TAG = "ChatEditText";

	public ChatEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	public ChatEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public ChatEditText(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		SurespotLog.v(TAG, "onKeyPreIme");
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			SurespotLog.v(TAG, "back pressed");
			((View) getParent()).requestFocus();
			//return false;
		}
		return false;
		//return super.dispatchKeyEvent(event);
	}
}
