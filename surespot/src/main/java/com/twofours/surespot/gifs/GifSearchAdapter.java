package com.twofours.surespot.gifs;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.twofours.surespot.network.IAsyncCallback;

import java.util.List;

import pl.droidsonroids.gif.GifImageView;

public class GifSearchAdapter extends RecyclerView.Adapter<GifSearchAdapter.GifViewHolder> {
    private final static String TAG = "GifSearchAdapter";
    private List<String> mGifs;
    private GifSearchDownloader mGifSearchDownloader;
    private Context mContext;

    private IAsyncCallback<String> mCallback;

    public GifSearchAdapter(Context context, List<String> gifUrls, IAsyncCallback<String> callback) {
        mContext = context;
        mCallback = callback;
        mGifSearchDownloader = new GifSearchDownloader(this);
        mGifs = gifUrls;
    }


    @Override
    public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        GifImageView v = new GifImageView(mContext);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(2, 0, 2, 0); //substitute parameters for left, top, right, bottom
        v.setLayoutParams(params);
        GifViewHolder vh = new GifViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final GifViewHolder holder, final int position) {
        mGifSearchDownloader.download(holder.imageView, mGifs.get(position));
        holder.imageView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    String url = mGifs.get(holder.getAdapterPosition());
                    mCallback.handleResponse(url);
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

    public void setGifs(List<String> gifUrls) {
        mGifs = gifUrls;
        notifyDataSetChanged();
    }

    public void clearGifs() {
        mGifs.clear();
        notifyDataSetChanged();
    }


    public static class GifViewHolder extends RecyclerView.ViewHolder {

        public GifImageView imageView;

        public GifViewHolder(GifImageView v) {
            super(v);
            imageView = v;
        }
    }
}
