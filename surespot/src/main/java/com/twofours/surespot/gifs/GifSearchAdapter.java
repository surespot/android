package com.twofours.surespot.gifs;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.UIUtils;

import java.util.List;

public class GifSearchAdapter extends RecyclerView.Adapter<GifSearchAdapter.GifViewHolder> {
    private final static String TAG = "GifSearchAdapter";
    private List<GifDetails> mGifs;
    private GifSearchDownloader mGifSearchDownloader;
    private Context mContext;
    private IAsyncCallback<GifDetails> mCallback;

    public GifSearchAdapter(Context context, List<GifDetails> gifUrls, IAsyncCallback<GifDetails> callback) {
        SurespotLog.d(TAG, "contructor, callback: %s", callback);
        mContext = context;
        mCallback = callback;
        mGifSearchDownloader = new GifSearchDownloader(this);
        mGifs = gifUrls;
    }


    @Override
    public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SurespotGifImageView v = (SurespotGifImageView) parent.inflate(getContext(), R.layout.surespot_gif_image_view, null);
        GifViewHolder vh = new GifViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final GifViewHolder holder, final int position) {
        GifDetails details = mGifs.get(position);
        SurespotLog.d("TAG", "onBindViewHolder url: %s, width %d, height %d", details.getUrl(), details.getWidth(), details.getHeight());


        mGifSearchDownloader.download(holder.imageView, details.getUrl());
        double scale = (double) SurespotConfiguration.GIF_SEARCH_RESULT_HEIGHT_DP / details.getHeight();

        int height = (int) (scale * UIUtils.pxFromDp(getContext(), details.getHeight()));
        int width = (int) (scale * UIUtils.pxFromDp(getContext(), details.getWidth()));

        SurespotLog.d(TAG, "onBindViewHolder url: %s, scale: %f, setting width to %d, setting height to %d", details.getUrl(), scale, width, height);
        ViewGroup.LayoutParams params = holder.imageView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(width, height);
        }
        params.height = height;
        params.width = width;
        SurespotLog.d(TAG, "onBindViewHolder params post url: %s, scale: %f, width to %d, height to %d", details.getUrl(), scale, params.width, params.height);
        holder.imageView.setLayoutParams(params);


        holder.imageView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    GifDetails gd = mGifs.get(holder.getAdapterPosition());
                    mCallback.handleResponse(gd);
                }
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return mGifs.size();
    }

    public Context getContext() {
        return mContext;
    }

    public void setGifs(List<GifDetails> gifUrls) {
        mGifs = gifUrls;
        notifyDataSetChanged();
    }

    public void clearGifs() {
        mGifs.clear();
        notifyDataSetChanged();
    }


    public static class GifViewHolder extends RecyclerView.ViewHolder {

        public SurespotGifImageView imageView;

        public GifViewHolder(SurespotGifImageView v) {
            super(v);
            imageView = v;
        }
    }
}
