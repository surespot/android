package com.twofours.surespot.images;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

public class GalleryModeAdapter extends CursorRecyclerViewAdapter<GalleryModeAdapter.GifViewHolder> {
    private final static String TAG = "GalleryModeAdapter";
    private static final int IMAGE_ID_COLUMN = 0;
    private static final int DATA_COLUMN = 1;
    private static final int ORIENTATION_COLUMN = 5;
    private static final int WIDTH_COLUMN = 6;
    private static final int HEIGHT_COLUMN = 7;
    private static final int T_WIDTH_COLUMN = 2;
    private static final int T_HEIGHT_COLUMN = 3;
    private static final int T_ORIG_ID_COLUMN = 4;


    private GalleryModeDownloader mGifSearchDownloader;
    private Context mContext;
    private IAsyncCallback<Uri> mCallback;
    //private Cursor mCursor;
    private int mHeight;

    public GalleryModeAdapter(Context context, IAsyncCallback<Uri> callback, int height) {
        super(context,null);
        mContext = context;
        mGifSearchDownloader = new GalleryModeDownloader(context);
        mCallback = callback;
        //mCursor = getCursor();
        mHeight = height;
        SurespotLog.d(TAG, "height: %d", height);

    }

    @Override
    public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SurespotLog.d(TAG, "onCreateViewHolder");
        ImageView v = (ImageView) parent.inflate(getContext(), R.layout.gallery_mode_item, null);

        GifViewHolder vh = new GifViewHolder(v);
        return vh;
    }


    @Override
    public void onBindViewHolder(GifViewHolder holder, Cursor cursor, int position) {

        cursor.moveToPosition(position);
        //Uri uri = ContentUris.withAppendedId(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, mCursor.getInt(IMAGE_ID_COLUMN));
        final String id = cursor.getString(T_ORIG_ID_COLUMN);
        //    int orientation = mCursor.getInt(ORIENTATION_COLUMN);
        int height = cursor.getInt(T_HEIGHT_COLUMN);
        int width = cursor.getInt(T_WIDTH_COLUMN);

        SurespotLog.d(TAG, "onBindViewHolder url: %s, width %d, height %d", id.toString(), width, height);


        mGifSearchDownloader.download(holder.imageView, id.toString());
//
        //determine image height knowing that there's 2 rows with a gap
        //and figure out the scaled height
        double offsetSide = mContext.getResources().getDimensionPixelSize(R.dimen.item_offset_side);
        double offsetBottom = mContext.getResources().getDimensionPixelSize(R.dimen.item_offset_bottom);
        double scale = ((mHeight - offsetBottom) / 2) / height;
        height = (int) Math.round(scale * height);
        width = (int) Math.round(scale * width);

        //add the same offsets we do for the item decoration
        if (position % 2 == 0) {
            height += offsetBottom;
        }

        width += offsetSide;

        SurespotLog.d(TAG, "onBindViewHolder scaled url: %s, scale: %f, width: %d, height: %d", id.toString(), scale, width, height);
        ViewGroup.LayoutParams params = holder.imageView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(width, height);
        }
        params.height = height;
        params.width = width;
//        SurespotLog.d(TAG, "onBindViewHolder params post url: %s, scale: %f, width to %d, height to %d", details.getUrl(), scale, params.width, params.height);
        holder.imageView.setLayoutParams(params);


        holder.imageView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.handleResponse(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Integer.parseInt(id)));
                }
            }
        });
    }

    public Context getContext() {
        return mContext;
    }


    public static class GifViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;

        public GifViewHolder(ImageView v) {
            super(v);
            imageView = v;
        }
    }
}
