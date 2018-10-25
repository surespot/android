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

public class GalleryModeAdapter extends CursorRecyclerViewAdapter<GalleryModeAdapter.ImageViewHolder> {
    private final static String TAG = "GalleryModeAdapter";
    public static final int IMAGE_ID_COLUMN = 0;
    public static final int DATA_COLUMN = 1;
    private static final int MINI_THUMB_MAGIC = 2;
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
        super(context, null);
        mContext = context;
        mGifSearchDownloader = new GalleryModeDownloader(context);
        mCallback = callback;
        //mCursor = getCursor();
        mHeight = height;
        SurespotLog.v(TAG, "height: %d", height);

    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SurespotLog.v(TAG, "onCreateViewHolder");
        ImageView v = (ImageView) parent.inflate(getContext(), R.layout.gallery_mode_item, null);

        ImageViewHolder vh = new ImageViewHolder(v);
        return vh;
    }


    @Override
    public void onBindViewHolder(ImageViewHolder holder, Cursor cursor, int position) {

        cursor.moveToPosition(position);

        final int id = cursor.getInt(IMAGE_ID_COLUMN);
        final Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        //final String data = cursor.getString(DATA_COLUMN);
        int orientation = cursor.getInt(ORIENTATION_COLUMN);
        int height = cursor.getInt(HEIGHT_COLUMN);
        int width = cursor.getInt(WIDTH_COLUMN);

        SurespotLog.v(TAG, "onBindViewHolder id: %d, ratio: %f, width %d, height: %d, orientation: %d", id, (double) width / height, width, height, orientation);

        //rotate dimensions for scaling
        if (orientation == 90 || orientation == 270) {
            int oldWidth = width;
            width = height;
            height = oldWidth;

            SurespotLog.v(TAG, "onBindViewHolder id: %d, rotated, new ratio: %f, new width %d, new height: %d, orientation: %d", id, (double) width / height, width, height, orientation);
        }

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

        final GalleryData gd = new GalleryData();
        gd.setId(id);
        gd.setWidth(width);
        gd.setHeight(height);
        gd.setOrientation(orientation);

        mGifSearchDownloader.download(holder.imageView, gd);

        SurespotLog.v(TAG, "onBindViewHolder scaled id: %d, ratio: %f, scale: %f, width: %d, height: %d", id, (double) width / height, scale, width, height);
        ViewGroup.LayoutParams params = holder.imageView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(width, height);
        }
        params.height = height;
        params.width = width;
//        SurespotLog.d(TAG, "onBindViewHolder params post url: %s, scale: %f, width to %d, height to %d", details.getData(), scale, params.width, params.height);
        holder.imageView.setLayoutParams(params);


        holder.imageView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    SurespotLog.d(TAG, "selected id: %d", gd.getId());
                    mCallback.handleResponse(uri);
                }
            }
        });
    }

    public Context getContext() {
        return mContext;
    }


    public static class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;

        public ImageViewHolder(ImageView v) {
            super(v);
            imageView = v;
        }
    }
}
