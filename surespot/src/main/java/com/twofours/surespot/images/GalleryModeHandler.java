package com.twofours.surespot.images;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.gifs.GifDetails;
import com.twofours.surespot.network.IAsyncCallback;

public class GalleryModeHandler {
    private static final String TAG = "GalleryModeHandler";

    private RecyclerView mRecyclerView;
    private StaggeredGridLayoutManager mLayoutManager;
    private GalleryModeAdapter mGalleryModeAdapter;
    private IAsyncCallback<GifDetails> mGifSelectedCallback;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    //   private TextView mTvLastSearch;
    private IAsyncCallback<String> mGifSearchTextCallback;
    private Context mContext;
    private String mUsername;

    public GalleryModeHandler(Context context, String username) {
        mContext = context;
        mUsername = username;
    }

    public class ItemOffsetDecoration extends RecyclerView.ItemDecoration {

        private int mItemOffset;

        public ItemOffsetDecoration(int itemOffset) {
            mItemOffset = itemOffset;
        }

        public ItemOffsetDecoration(@NonNull Context context, @DimenRes int itemOffsetId) {
            this(context.getResources().getDimensionPixelSize(itemOffsetId));
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            //show bottom offset on top row
            if (parent.getChildAdapterPosition(view) % 2 == 0) {
                outRect.bottom = mItemOffset;
            }

            //don't show right margin on last row
            outRect.right = parent.getChildAdapterPosition(view) == parent.getChildCount() - 1 ? 0 : mItemOffset;
            outRect.left = 0;

        }
    }

    public void refreshContextAndViews(Context context, View parentView) {
        mContext = context;
        SharedPreferences settings = context.getSharedPreferences("surespot_preferences", android.content.Context.MODE_PRIVATE);
        boolean black = settings.getBoolean("pref_black", false);
        parentView.setBackgroundColor(black ? Color.BLACK : Color.WHITE);
        mRecyclerView = (RecyclerView) parentView.findViewById(R.id.rvGallery);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if(newState == RecyclerView.SCROLL_STATE_IDLE){
                    mRecyclerView.invalidateItemDecorations();
                }
            }
        });

        mLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.HORIZONTAL);
        mLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);

        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new ItemOffsetDecoration(context, R.dimen.item_offset));
        mProgressBar = (ProgressBar) parentView.findViewById(R.id.gallery_progress_bar);
        mEmptyView = parentView.findViewById(R.id.tv_no_images);
        mGalleryModeAdapter = null;

        ViewGroup.LayoutParams lp = mRecyclerView.getLayoutParams();
        int heightSpec = View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.AT_MOST);
        mRecyclerView.measure(WindowManager.LayoutParams.MATCH_PARENT, heightSpec);
        final int initialHeight = mRecyclerView.getHeight();
        final int targetheight = mRecyclerView.getMeasuredHeight();

        SurespotLog.d(TAG, "gallery view height: %d, measured height: %d", initialHeight, targetheight);
        populateImages();
    }


    public void populateImages() {
        SurespotLog.d(TAG, "populate Images");

        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        if (mGalleryModeAdapter == null) {
            mGalleryModeAdapter = new GalleryModeAdapter(mContext, mGifSelectedCallback);
            mRecyclerView.setAdapter(mGalleryModeAdapter);
            mGalleryModeAdapter.notifyDataSetChanged();
        }


        mRecyclerView.setVisibility(View.VISIBLE);
        mRecyclerView.scrollToPosition(0);
        mProgressBar.setVisibility(View.GONE);
        //mEmptyView.setVisibility(mC.size() > 0 ? View.GONE : View.VISIBLE);
    }


    public void setGifSelectedCallback(final IAsyncCallback<GifDetails> callback) {
        mGifSelectedCallback = new IAsyncCallback<GifDetails>() {
            @Override
            public void handleResponse(GifDetails result) {
                //update recently used
                callback.handleResponse(result);
            }
        };
    }
}
