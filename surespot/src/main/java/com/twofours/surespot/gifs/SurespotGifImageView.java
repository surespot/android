package com.twofours.surespot.gifs;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.twofours.surespot.R;

import pl.droidsonroids.gif.GifImageView;

/**
 * Created by adam on 2/26/17.
 */

public class SurespotGifImageView extends FrameLayout {

    public SurespotGifImageView(Context context) {
        super(context);
    }

    public SurespotGifImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SurespotGifImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SurespotGifImageView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    private void hideProgress() {
        View progressView = findViewById(R.id.gif_progress_bar);
        View imageView = findViewById(R.id.gif_image_view);

        progressView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
    }

    public void showProgress() {
        View progressView = findViewById(R.id.gif_progress_bar);
        View imageView = findViewById(R.id.gif_image_view);

        progressView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
    }

    public void setImageDrawable(Drawable drawable) {
        GifImageView imageView = (GifImageView) findViewById(R.id.gif_image_view);
        imageView.setImageDrawable(drawable);
        hideProgress();
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        findViewById(R.id.gif_image_view).setOnClickListener(l);
    }
}
