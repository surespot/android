package com.twofours.surespot.chat;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.view.KeyEvent;

import com.twofours.surespot.activities.MainActivity;

public class SurespotDrawerLayout extends DrawerLayout {
	MainActivity mMainActivity;

	// thanks to http://stackoverflow.com/questions/7300497/adjust-layout-when-soft-keyboard-is-on
	public SurespotDrawerLayout(Context context) {
		super(context);
	}

	public SurespotDrawerLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	public void setMainActivity(MainActivity mainActivity) {
		mMainActivity = mainActivity;
	}


	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if (mMainActivity != null) {
			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
				return mMainActivity.backButtonPressed();
			}
		}

		return super.dispatchKeyEventPreIme(event);
	}
}
