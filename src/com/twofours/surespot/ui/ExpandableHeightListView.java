package com.twofours.surespot.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class ExpandableHeightListView extends ListView {
	public ExpandableHeightListView(Context context) {
		super(context);
	}

	public ExpandableHeightListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ExpandableHeightListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	boolean expanded = false;

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// HACK! TAKE THAT ANDROID!
		if (isExpanded()) {
			// Calculate entire height by providing a very large height hint.
			// But do not use the highest 2 bits of this integer; those are
			// reserved for the MeasureSpec mode.
			int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
			super.onMeasure(widthMeasureSpec, expandSpec);

			android.view.ViewGroup.LayoutParams params = getLayoutParams();
			params.height = getMeasuredHeight();
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}
}