package com.twofours.surespot.gifs;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

import static android.content.ContentValues.TAG;

public class GifSearchView extends RelativeLayout {

    public static final String RECENTLY_USED_GIFS = "recently_used_gifs";
    public static final String RECENT_GIFS = "recent_gifs";
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private GifSearchAdapter mGifsAdapter;
    private IAsyncCallback<GifDetails> mCallback;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    private TextView mTvLastSearch;


    public GifSearchView(Context context) {
        super(context);
    }

    public GifSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public GifSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifSearchView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();


        mRecyclerView = (RecyclerView) findViewById(R.id.rvGifs);
        mLayoutManager = new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mProgressBar = (ProgressBar) findViewById(R.id.gif_progress_bar);
        mEmptyView = findViewById(R.id.tv_no_gifs);
        mTvLastSearch = (TextView) findViewById(R.id.tvLastSearch);

        RecyclerView keywordView = (RecyclerView) findViewById(R.id.rvGifKeywords);
        keywordView.setLayoutManager(new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false));

        String sRecentlyUsed = getContext().getString(R.string.recently_used);
        String gifTerms = sRecentlyUsed + ":" + getContext().getString(R.string.gif_terms);
        String[] terms = gifTerms.split(":");
        List<String> keywords = Arrays.asList(terms);

        keywordView.setAdapter(new GifKeywordAdapter(this.getContext(), keywords, new IAsyncCallback<String>() {
            @Override
            public void handleResponse(String result) {
                String sRecentlyUsed = getContext().getString(R.string.recently_used);
                if (result.equals(sRecentlyUsed)) {
                    showRecentlyUsed();
                }
                else {
                    searchGifs(result);
                }
            }
        }));


    }



    public void searchGifs(final String terms) {
        SurespotLog.d(TAG, "searchGifs terms: %s", terms);
        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);


        NetworkManager.getNetworkController(GifSearchView.this.getContext()).searchGiphy(terms, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
            @Override
            public void onFailure(Call call, IOException e) {
                clearGifs();
                mTvLastSearch.setText(R.string.giphy_search_failed);
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                List<GifDetails> gifs = getGifDetails(responseString);
                setGifs(terms, gifs);
            }
        }));
    }

    private void clearGifs() {
        mRecyclerView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.VISIBLE);
        if (mGifsAdapter == null) {
            mGifsAdapter = new GifSearchAdapter(GifSearchView.this.getContext(), new ArrayList<GifDetails>(0), mCallback);
            mRecyclerView.setAdapter(mGifsAdapter);
            mRecyclerView.scrollToPosition(0);
            mGifsAdapter.notifyDataSetChanged();
        }
        else {
            mGifsAdapter.clearGifs();
        }
    }

    private void setGifs(String terms, List<GifDetails> gifs) {
        mTvLastSearch.setText(terms);
        if (mGifsAdapter != null) {
            mGifsAdapter.clearGifs();
        }

        if (gifs == null) {
            gifs = new ArrayList<GifDetails>(0);
        }

        if (mGifsAdapter == null) {
            mGifsAdapter = new GifSearchAdapter(GifSearchView.this.getContext(), gifs, mCallback);
            mRecyclerView.setAdapter(mGifsAdapter);
            mGifsAdapter.notifyDataSetChanged();
        }
        else {
            mGifsAdapter.setGifs(gifs);
        }
        mRecyclerView.setVisibility(View.VISIBLE);
        mRecyclerView.scrollToPosition(0);
        mProgressBar.setVisibility(View.GONE);
        mEmptyView.setVisibility(gifs.size() > 0 ? View.GONE : View.VISIBLE);

    }

    public void setCallback(final IAsyncCallback<GifDetails> callback) {
        mCallback = new IAsyncCallback<GifDetails>() {
            @Override
            public void handleResponse(GifDetails result) {
                //update recently used
                addRecentlyUsed(result);
                callback.handleResponse(result);
            }
        };
        showRecentlyUsed();
    }

    private List<GifDetails> getGifDetails(String result) {
        ArrayList<GifDetails> gifURLs = new ArrayList<>();
        try {

            JSONObject json = new JSONObject(result);
            JSONArray data = json.getJSONArray("data");

            for (int i = 0; i < data.length(); i++) {
                JSONObject orig = data.getJSONObject(i).getJSONObject("images").getJSONObject("fixed_height");
                String url = orig.getString("url");
                if (url.toLowerCase().startsWith("https")) {
                    int height = orig.getInt("height");
                    int width = orig.getInt("width");

                    gifURLs.add(new GifDetails(url, width, height));
                }
            }
        }
        catch (JSONException e) {
            SurespotLog.e(TAG, e, "getGifDetails JSON error");
        }
        return gifURLs;
    }

    private void addRecentlyUsed(GifDetails gifDetails) {
        List<GifDetails> recentlyUsed = getRecentlyUsed();
        int index = recentlyUsed.indexOf(gifDetails);
        GifDetails gifToAdd = gifDetails;
        //move it to the front if it's already there
        if (index > -1) {
            gifToAdd = recentlyUsed.remove(index);
        }

        recentlyUsed.add(0, gifToAdd);
        setRecentlyUsed(recentlyUsed);
        String sRecentlyUsed = getContext().getString(R.string.recently_used);
        if (sRecentlyUsed.equals(mTvLastSearch.getText())) {
            setGifs(sRecentlyUsed, recentlyUsed);
        }
    }

    private List<GifDetails> getRecentlyUsed() {
        String recentJSON = Utils.getUserSharedPrefsString(getContext(), IdentityController.getLoggedInUser(), RECENTLY_USED_GIFS);
        ArrayList<GifDetails> gifs = new ArrayList<GifDetails>();
        JSONObject jRecentGifs = null;
        if (!TextUtils.isEmpty(recentJSON)) {
            try {
                jRecentGifs = new JSONObject(recentJSON);
            }
            catch (JSONException e) {
                SurespotLog.w(TAG, e, "could not parse recent gifs json");
            }
        }
        if (jRecentGifs == null) {
            Utils.putUserSharedPrefsString(getContext(), IdentityController.getLoggedInUser(), RECENTLY_USED_GIFS, null);
        }
        else {
            try {
                JSONArray gifJSONArray = jRecentGifs.getJSONArray(RECENT_GIFS);
                for (int i = 0; i < gifJSONArray.length(); i++) {
                    JSONObject gifO = gifJSONArray.getJSONObject(i);
                    GifDetails gd = new GifDetails(gifO);
                    gifs.add(gd);
                }
            }
            catch (JSONException e) {
                Utils.putUserSharedPrefsString(getContext(), IdentityController.getLoggedInUser(), RECENTLY_USED_GIFS, null);
            }
        }
        return gifs;
    }

    private void setRecentlyUsed(List<GifDetails> gifs) {
        JSONObject o = new JSONObject();
        JSONArray a = new JSONArray();
        try {
            int count = 0;
            for (GifDetails gd : gifs) {
                //save 25 recently used
                if (count++ < 25) {
                    a.put(gd.toJSONObject());
                    o.put(RECENT_GIFS, a);
                }
            }
            Utils.putUserSharedPrefsString(getContext(), IdentityController.getLoggedInUser(), RECENTLY_USED_GIFS, o.toString());

        }
        catch (JSONException e) {
            SurespotLog.w(TAG, e, "could not set recently used GIFs");
            Utils.putUserSharedPrefsString(getContext(), IdentityController.getLoggedInUser(), RECENTLY_USED_GIFS, null);
        }
    }

    private void showRecentlyUsed() {
        String sRecentlyUsed = getContext().getString(R.string.recently_used);
        List<GifDetails> gifs = getRecentlyUsed();
        setGifs(sRecentlyUsed, gifs);
    }
}
