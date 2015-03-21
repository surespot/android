package com.twofours.surespot.voice;

import java.util.LinkedList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.twofours.surespot.R;

public class VolumeEnvelopeView extends View {
	private static final String TAG = null;
	private static int mMaxVolume;

	/**
	 * Constructor. This version is only needed if you will be instantiating the object manually (not from a layout XML file).
	 * 
	 * @param context
	 */
	public VolumeEnvelopeView(Context context) {
		super(context);
		initVolumeEnvelopeView();
	}

	/**
	 * Construct object, initializing with any attributes we understand from a layout file. These attributes are defined in SDK/assets/res/any/classes.xml.
	 * 
	 * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
	 */
	public VolumeEnvelopeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initVolumeEnvelopeView();

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.VolumeEnvelopeView);

		// Retrieve the color(s) to be used for this view and apply them.
		// Note, if you only care about supporting a single color, that you
		// can instead call a.getColor() and pass that to setTextColor().
		setColor(a.getColor(R.styleable.VolumeEnvelopeView_color, 0xFF000000));

		a.recycle();
	}

	private final void initVolumeEnvelopeView() {
		mEnvelopePaint = new Paint();
		mEnvelopePaint.setAntiAlias(false);
		mEnvelopePaint.setColor(0xFF000000);
	}

	/**
	 * Sets the text color for this label.
	 * 
	 * @param color
	 *            ARGB value for the text
	 */
	public void setColor(int color) {
		mEnvelopePaint.setColor(color);
		invalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mSize = (w - this.getPaddingLeft() - this.getPaddingRight()) / 2;
	}

	public void setNewVolume(int value, boolean redraw) {

		if (value != 0) {
			mEnvelope.add(value);
			if (value > mMaxVolume) {
				mMaxVolume = value;
			}
		}
		else
			if (!mEnvelope.isEmpty())
				mEnvelope.add(mEnvelope.getLast());

		while (mEnvelope.size() >= mSize && !mEnvelope.isEmpty())
			mEnvelope.remove();
		if (redraw) {
			invalidate();
		}
	}

	public void clearVolume() {
		mMaxVolume = 0;
		mEnvelope.clear();
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		int size = mEnvelope.size();
		int width = canvas.getWidth() / (VoiceController.MAX_TIME / VoiceController.INTERVAL);
		int x = canvas.getWidth() - width * size;

		int height = (this.getHeight() - this.getPaddingBottom() - this.getPaddingTop()) / 2;
		int mid = this.getHeight() / 2;
		
		for (Integer i : mEnvelope) {			
			int offset = (int) ((i.floatValue() / 32768) * height);
			canvas.drawLine(x, mid - offset, x, mid + offset + 1, mEnvelopePaint);
			x += width;			
		}
	}

	private Paint mEnvelopePaint;
	private LinkedList<Integer> mEnvelope = new LinkedList<Integer>();
	int mSize = 0;
	
	public int getMaxVolume() {
		return mMaxVolume;
	}
}
