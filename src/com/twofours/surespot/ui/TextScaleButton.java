//inpired by https://github.com/catchthecows/BigTextButton/blob/master/src/com/sample/BigTextButton.java and http://stackoverflow.com/questions/4794484/calculate-text-size-according-to-width-of-text-area

package com.twofours.surespot.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.ImageButton;

import com.twofours.surespot.common.SurespotLog;

public class TextScaleButton extends ImageButton {
	private static final String TAG = "TextScaleButton";
	String mText = "";
	Paint mTextPaint;

	private static final int MAX_TEXT_SIZE=100;
	int mViewWidth;
	int mViewHeight;
	int mTextBaseline;

	public TextScaleButton(Context context) {
		super(context);
		init();
	}

	public TextScaleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		parseAttrs(attrs);
		init();
	}

	public TextScaleButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		parseAttrs(attrs);
		init();
	}

	/**
	 * Dig out Attributes to find text setting
	 * 
	 * This could be expanded to pull out settings for textColor, etc if desired
	 * 
	 * @param attrs
	 */

	private void parseAttrs(AttributeSet attrs) {
		for (int i = 0; i < attrs.getAttributeCount(); i++) {
			String s = attrs.getAttributeName(i);
			if (s.equalsIgnoreCase("text")) {
				mText = attrs.getAttributeValue(i);
			}
		}
	}

	public void setText(CharSequence text) {
		mText = text.toString();
		onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
	}

	/**
	 * initialize Paint for text, it will be modified when the view size is set
	 */
	private void init() {
		mTextPaint = new TextPaint();
		mTextPaint.setTextAlign(Paint.Align.CENTER);
		mTextPaint.setAntiAlias(true);
	}

	int getTextSize() {
		int incr_text_size = 1;
		int text_width = mViewWidth - getPaddingLeft() - getPaddingRight();

		int text_check_w = 0;
		Rect bounds = new Rect();
		// ask the paint for the bounding rect if it were to draw this
		
		while (text_width > text_check_w && incr_text_size < MAX_TEXT_SIZE) {
			mTextPaint.setTextSize(incr_text_size);// have this the same as your text size
			mTextPaint.getTextBounds(mText, 0, mText.length(), bounds);

			text_check_w = bounds.width();
			incr_text_size++;

		}

		int text_h = bounds.bottom - bounds.top;
		mTextBaseline = bounds.bottom + ((mViewHeight - text_h) / 2);

		//round down to nearest 2
		return incr_text_size/4*4;
	}

	/**
	 * When the view size is changed, recalculate the paint settings to have the text on the fill the view area
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		// save view size
		mViewWidth = w;
		mViewHeight = h;

		int textSize = getTextSize();
		SurespotLog.v(TAG, "setting text size to: %d", textSize);
		mTextPaint.setTextSize(textSize);

	}

	@Override
	protected void onDraw(Canvas canvas) {
		// let the ImageButton paint background as normal
		super.onDraw(canvas);

		// draw the text
		// position is centered on width
		// and the baseline is calculated to be positioned from the
		// view bottom
		canvas.drawText(mText, mViewWidth / 2, mViewHeight - mTextBaseline, mTextPaint);

	}
}