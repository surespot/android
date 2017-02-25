package com.twofours.surespot.gifs;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatUtils;

import java.util.List;

import pl.droidsonroids.gif.GifImageView;

public class GifsAdapter extends RecyclerView.Adapter<GifsAdapter.GifViewHolder> {
    private final static String TAG = "GifsAdapter";
    private List<String> mGifs;
    private GifSearchDownloader mGifSearchDownloader;
    private Context mContext;


    private String mOurUsername;
    private String mTheirUsername;

    public GifsAdapter(Context context, String ourUsername, String theirUsername, List<String> gifUrls) {
        SurespotLog.d(TAG, "Constructor, ourUsername: %s, theirUsername: %s", ourUsername, theirUsername);

        mContext = context;
        mGifSearchDownloader = new GifSearchDownloader(ourUsername, this);
        mOurUsername = ourUsername;
        mTheirUsername = theirUsername;
        mGifs = gifUrls;

    }


    @Override
    public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        GifImageView v = new GifImageView(mContext);
        GifViewHolder vh = new GifViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final GifViewHolder holder, final int position) {
        //Picasso.with(mContext).load(mGifs.get(position)).into(holder.imageView);
        mGifSearchDownloader.download(holder.imageView, mGifs.get(position));
        holder.imageView.setOnClickListener(new View.OnClickListener() {

                                                @Override
                                                public void onClick(View v) {
                                                    String url = mGifs.get(holder.getAdapterPosition());
                                                    SurespotLog.d(TAG, "Sending gif message from: %s, to %s, url: %s", mOurUsername,mTheirUsername,url);
                                                    ChatUtils.sendGifMessage(mOurUsername, mTheirUsername, url);
                                                }
                                            }

        );
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
