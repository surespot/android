package com.twofours.surespot.images;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.widget.ProgressBar;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

import java.io.File;

import static com.twofours.surespot.images.GalleryModeAdapter.DATA_COLUMN;
import static com.twofours.surespot.images.GalleryModeAdapter.IMAGE_ID_COLUMN;

public class GalleryModeHandler implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "GalleryModeHandler";

    private RecyclerView mRecyclerView;
    private StaggeredGridLayoutManager mLayoutManager;
    private GalleryModeAdapter mGalleryModeAdapter;
    private IAsyncCallback<Uri> mImageSelectedCallback;
    private IAsyncCallback<Object> mLaunchGalleryCallback;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    private Activity mContext;
    private String mUsername;
    private int mHeight;
    private final int mOffset = 100;
    private int mPage = 0;
    private boolean loadingMore = false;
    private final String[] mProjection = new String[]{
            MediaStore.Images.Thumbnails._ID,
            MediaStore.Images.Thumbnails.DATA,
            MediaStore.Images.Thumbnails.WIDTH,
            MediaStore.Images.Thumbnails.HEIGHT,
            MediaStore.Images.Thumbnails.IMAGE_ID
    };
    String[] projection = new String[]{
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.MINI_THUMB_MAGIC,
            MediaStore.Images.ImageColumns.DATE_MODIFIED,
            MediaStore.Images.ImageColumns.MIME_TYPE,
            MediaStore.Images.ImageColumns.ORIENTATION,
            MediaStore.Images.ImageColumns.WIDTH,
            MediaStore.Images.ImageColumns.HEIGHT};
    private Handler mHandler = new Handler();

    public GalleryModeHandler(Activity context, String username, int height, IAsyncCallback<Uri> imageSelectedCallback, IAsyncCallback<Object> launchGalleryCallback) {
        mContext = context;
        mUsername = username;
        mHeight = height;
        mImageSelectedCallback = imageSelectedCallback;
        mLaunchGalleryCallback = launchGalleryCallback;
    }


    public class ItemOffsetDecoration extends RecyclerView.ItemDecoration {

        private int mItemOffsetSide;
        private int mItemOffsetBottom;

        public ItemOffsetDecoration(int itemOffsetSide, int itemOffsetBottom) {
            mItemOffsetSide = itemOffsetSide;
            mItemOffsetBottom = itemOffsetBottom;

        }

        public ItemOffsetDecoration(@NonNull Context context, @DimenRes int itemOffsetIdSide, @DimenRes int itemOffsetIdBottom) {
            this(context.getResources().getDimensionPixelSize(itemOffsetIdSide), context.getResources().getDimensionPixelOffset(itemOffsetIdBottom));
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            //show bottom offset on top row
            if (parent.getChildAdapterPosition(view) % 2 == 0) {
                outRect.bottom = mItemOffsetBottom;
            }

            //don't show right margin on last row
            outRect.right = //parent.getChildAdapterPosition(view) == parent.getChildCount() - 1 ? 0 : mItemOffset;
                    mItemOffsetSide;
            outRect.left = 0;

        }
    }

    public void refreshContextAndViews(Activity context, View parentView) {
        mContext = context;
        mPage = 0;
        SharedPreferences settings = context.getSharedPreferences("surespot_preferences", android.content.Context.MODE_PRIVATE);
        boolean black = settings.getBoolean("pref_black", false);
        parentView.setBackgroundColor(black ? Color.BLACK : Color.WHITE);
        mRecyclerView = (RecyclerView) parentView.findViewById(R.id.rvGallery);

        mLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.HORIZONTAL);
        mLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);

        mRecyclerView.setVisibility(View.VISIBLE);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new ItemOffsetDecoration(context, R.dimen.item_offset_side, R.dimen.item_offset_bottom));
        mProgressBar = (ProgressBar) parentView.findViewById(R.id.gallery_progress_bar);
        mEmptyView = parentView.findViewById(R.id.tv_no_images);
        mGalleryModeAdapter = null;

        View launchView = parentView.findViewById(R.id.launch_gallery);
        launchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLaunchGalleryCallback.handleResponse(null);
            }
        });
        populateImages();
    }


    public void populateImages() {
        SurespotLog.d(TAG, "populate Images");

        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        if (mGalleryModeAdapter == null) {
            mGalleryModeAdapter = new GalleryModeAdapter(mContext, mImageSelectedCallback, mHeight);
            mRecyclerView.setAdapter(mGalleryModeAdapter);
        }


        mRecyclerView.setVisibility(View.VISIBLE);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                StaggeredGridLayoutManager layoutManager = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                int[] lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null);
                int maxPositions = layoutManager.getItemCount();

                if (lastVisibleItemPositions[0] == maxPositions - 1 || lastVisibleItemPositions[1] == maxPositions - 1) {
                    if (loadingMore)
                        return;

                    loadingMore = true;
                    mPage++;
                    SurespotLog.d(TAG, "restarting loader, page: %d", mPage);
                    mContext.getLoaderManager().restartLoader(0, null, GalleryModeHandler.this);
                }
            }
        });

        loadingMore = true;
        mContext.getLoaderManager().restartLoader(0, null, this);
        mProgressBar.setVisibility(View.GONE);
        //mEmptyView.setVisibility(mC.size() > 0 ? View.GONE : View.VISIBLE);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case 0:
                SurespotLog.d(TAG, "onCreateLoader: mpage: %d, mPage");
                return new CursorLoader(mContext, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, String.format("date_modified desc limit %d offset %d", mOffset, mPage * mOffset));
            default:
                throw new IllegalArgumentException("no id handled!");
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        switch (loader.getId()) {
            case 0:
                SurespotLog.d(TAG, "onLoadFinished: loading more, mPage: %d, count; %d", mPage, data.getCount());
                Cursor cursor = ((GalleryModeAdapter) mRecyclerView.getAdapter()).getCursor();
                MatrixCursor mx = new MatrixCursor(projection);
                fillFull(cursor, mx, false);
                fillFull(data, mx, true);
                ((GalleryModeAdapter) mRecyclerView.getAdapter()).swapCursor(mx);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadingMore = false;
                    }
                }, 800);

                break;
            default:
                throw new IllegalArgumentException("no loader id handled!");
        }
    }


    private void fillFull(Cursor data, MatrixCursor mx, boolean validate) {
        if (data == null)
            return;

        data.moveToPosition(-1);
        while (data.moveToNext()) {
            if (validate) {
                //see if we can even image, bro
                final String path = data.getString(DATA_COLUMN);
                long id = data.getLong(IMAGE_ID_COLUMN);

                File file = new File(path);
                if (!file.exists() || file.length() == 0) {
                    SurespotLog.v(TAG, "skipping image for id: %d", id);
                    continue;
                }

                SurespotLog.v(TAG, "adding image for id: %d, path: %s", id, path);
            }


            mx.addRow(new Object[]{
                    data.getString(0),
                    data.getString(1),
                    data.getString(2),
                    data.getString(3),
                    data.getString(4),
                    data.getString(5),
                    data.getString(6),
                    data.getString(7)

            });
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

}
