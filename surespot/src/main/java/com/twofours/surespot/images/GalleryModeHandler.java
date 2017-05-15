package com.twofours.surespot.images;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.widget.ProgressBar;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

public class GalleryModeHandler {
    private static final String TAG = "GalleryModeHandler";

    private RecyclerView mRecyclerView;
    private StaggeredGridLayoutManager mLayoutManager;
    private GalleryModeAdapter mGalleryModeAdapter;
    private IAsyncCallback<Uri> mGallerySelectedCallback;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    private Context mContext;
    private String mUsername;
    private int mHeight;

    public GalleryModeHandler(Context context, String username, int height) {
        mContext = context;
        mUsername = username;
        mHeight = height;
    }

    public class ItemOffsetDecoration extends RecyclerView.ItemDecoration {

        private int mItemOffsetSide;
        private int mItemOffsetBottom;

        public ItemOffsetDecoration(int itemOffsetSide, int itemOffsetBottom) {
            mItemOffsetSide = itemOffsetSide;
            mItemOffsetBottom = itemOffsetBottom;

        }

        public ItemOffsetDecoration(@NonNull Context context, @DimenRes int itemOffsetIdSide , @DimenRes int itemOffsetIdBottom) {
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

    public void refreshContextAndViews(Context context, View parentView) {
        mContext = context;
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

        populateImages();
    }


    public void populateImages() {
        SurespotLog.d(TAG, "populate Images");

        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        if (mGalleryModeAdapter == null) {
            mGalleryModeAdapter = new GalleryModeAdapter(mContext, mGallerySelectedCallback, mHeight);
            mRecyclerView.setAdapter(mGalleryModeAdapter);
            mGalleryModeAdapter.notifyDataSetChanged();
        }


        mRecyclerView.setVisibility(View.VISIBLE);
        mRecyclerView.scrollToPosition(0);
        mProgressBar.setVisibility(View.GONE);
        //mEmptyView.setVisibility(mC.size() > 0 ? View.GONE : View.VISIBLE);
    }


    public void setGallerySelectedCallback(final IAsyncCallback<Uri> callback) {
        mGallerySelectedCallback = new IAsyncCallback<Uri>() {
            @Override
            public void handleResponse(Uri result) {
                //update recently used
                callback.handleResponse(result);
            }
        };
    }
}
