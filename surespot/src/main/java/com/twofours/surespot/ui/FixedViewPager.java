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

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		// prevent NPE if fake dragging and touching ViewPager
		if (isFakeDragging())
			return false;

		return super.onInterceptTouchEvent(ev);

	}
}
