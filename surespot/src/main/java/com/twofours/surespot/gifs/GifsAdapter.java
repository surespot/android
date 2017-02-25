package com.twofours.surespot.gifs;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.twofours.surespot.SurespotLog;

import java.util.List;

import pl.droidsonroids.gif.GifImageView;

public class GifsAdapter extends RecyclerView.Adapter<GifsAdapter.GifViewHolder> {
    private final static String TAG = "GifsAdapter";
    private List<String> mGifs;
    private GifSearchDownloader mGifSearchDownloader;
    private Context mContext;


    private String mOurUsername;

    public GifsAdapter(Context context, String ourUsername, List<String> gifUrls) {
        SurespotLog.d(TAG, "Constructor, ourUsername: %s", ourUsername);

        mContext = context;
        mGifSearchDownloader = new GifSearchDownloader(ourUsername, this);
        mOurUsername = ourUsername;
        mGifs = gifUrls;

    }



    @Override
    public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        GifImageView v = new GifImageView(mContext);
        GifViewHolder vh = new GifViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(GifViewHolder holder, int position) {
        //Picasso.with(mContext).load(mGifs.get(position)).into(holder.imageView);
        mGifSearchDownloader.download(holder.imageView,mGifs.get(position));
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

    public static class GifViewHolder extends RecyclerView.ViewHolder {

        public GifImageView imageView;
        public GifViewHolder(GifImageView v) {
            super(v);
            imageView = v;
        }
    }
}
