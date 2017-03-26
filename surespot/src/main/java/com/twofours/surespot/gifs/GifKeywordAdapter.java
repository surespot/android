package com.twofours.surespot.gifs;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twofours.surespot.network.IAsyncCallback;

import java.util.List;

public class GifKeywordAdapter extends RecyclerView.Adapter<GifKeywordAdapter.GifKeywordViewHolder> {
    private final static String TAG = "KeyWordAdapter";
    private List<String> mKeywords;
    private Context mContext;

    private IAsyncCallback<String> mCallback;

    public GifKeywordAdapter(Context context,List<String> keywords, IAsyncCallback<String> callback) {
        mContext = context;
        mCallback = callback;
        mKeywords = keywords;
    }


    @Override
    public GifKeywordViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TextView v = new TextView(mContext);
        GifKeywordViewHolder vh = new GifKeywordViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(final GifKeywordViewHolder holder, final int position) {

        holder.textView.setText(mKeywords.get(position));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(30, 0, 30, 0); //substitute parameters for left, top, right, bottom
        holder.textView.setLayoutParams(params);
        holder.textView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    String keyword = mKeywords.get(holder.getAdapterPosition());
                    mCallback.handleResponse(keyword);
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
        return mKeywords.size();
    }

    public Context getContext() {
        return mContext;
    }

    public static class GifKeywordViewHolder extends RecyclerView.ViewHolder {

        public TextView textView;

        public GifKeywordViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
}
