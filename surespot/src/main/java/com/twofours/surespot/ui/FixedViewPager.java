package com.twofours.surespot.ui;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class FixedViewPager extends ViewPager {
	// hopefully fix RM#314..https://github.com/JakeWharton/Android-ViewPagerIndicator/pull/257
	public FixedViewPager(Context context) {
		super(context);
	}

	public FixedViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	//fix from https://github.com/chrisbanes/PhotoView/issues/31
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		try {
			return super.onTouchEvent(ev);
		} catch (IllegalArgumentException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		// prevent NPE if fake dragging and touching ViewPager
		if (isFakeDragging())
			return false;

		try {
			return super.onInterceptTouchEvent(ev);
		}
		catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return false;
	}
}
