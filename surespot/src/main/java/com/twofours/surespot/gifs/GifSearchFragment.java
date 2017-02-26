package com.twofours.surespot.gifs;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.twofours.surespot.R;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class GifSearchFragment extends RelativeLayout {

    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private GifSearchAdapter mGifsAdapter;
    private IAsyncCallback<String> mCallback;


    public GifSearchFragment(Context context) {
        super(context);
    }

    public GifSearchFragment(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public GifSearchFragment(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifSearchFragment(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();


        mRecyclerView = (RecyclerView) findViewById(R.id.rvGifs);
        mLayoutManager = new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);

        RecyclerView keywordView = (RecyclerView) findViewById(R.id.rvGifKeywords);
        keywordView.setLayoutManager(new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false));
        ArrayList<String> keywords = new ArrayList<>();
        keywords.add("HIGH FIVE");
        keywords.add("CLAPPING");
        keywords.add("THUMBS UP");
        keywords.add("NO");
        keywords.add("YES");
        keywords.add("SHRUG");
        keywords.add("MIC DROP");
        keywords.add("SORRY");
        keywords.add("CHEERS");
        keywords.add("THANK YOU");
        keywords.add("WINK");
        keywords.add("ANGRY");
        keywords.add("NERVOUS");
        keywords.add("DUH");
        keywords.add("OOPS");
        keywords.add("HUNGRY");
        keywords.add("HUGS");
        keywords.add("WOW");
        keywords.add("BORED");
        keywords.add("GOODNIGHT");
        keywords.add("AWKWARD");
        keywords.add("AWW");
        keywords.add("PLEASE");
        keywords.add("YIKES");
        keywords.add("OMG");
        keywords.add("BYE");
        keywords.add("WAITING");
        keywords.add("EYEROLL");
        keywords.add("IDK");
        keywords.add("KITTIES");
        keywords.add("PUPPIES");
        keywords.add("LOSER");
        keywords.add("COLD");
        keywords.add("PARTY");
        keywords.add("AGREE");
        keywords.add("DANCE");
        keywords.add("EXCUSE ME");
        keywords.add("WHAT");
        keywords.add("STOP");
        keywords.add("SLEEPY");
        keywords.add("CREEP");
        keywords.add("JK");
        keywords.add("SCARED");
        keywords.add("CHILL OUT");
        keywords.add("MISS YOU");
        keywords.add("DONE");

        keywordView.setAdapter(new GifKeywordAdapter(this.getContext(), keywords, new IAsyncCallback<String>() {
            @Override
            public void handleResponse(String result) {
                if (mGifsAdapter != null) {
                    mGifsAdapter.clearGifs();
                }
                NetworkManager.getNetworkController(GifSearchFragment.this.getContext()).searchGiphy(result, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (mGifsAdapter == null) {
                            mGifsAdapter = new GifSearchAdapter(GifSearchFragment.this.getContext(), getGifUrls(responseString), mCallback);
                            mRecyclerView.setAdapter(mGifsAdapter);
                        }
                        else {
                            mGifsAdapter.setGifs(getGifUrls(responseString));
                        }


                    }
                }));

            }
        }));


    }

    public void setCallback(IAsyncCallback<String> callback) {
        mCallback = callback;


    }

    private List<String> getGifUrls(String result) {
        ArrayList<String> gifURLs = new ArrayList<>();
        try {

            JSONObject json = new JSONObject(result);
            JSONArray data = json.getJSONArray("data");

            for (int i = 0; i < data.length(); i++) {
                JSONObject orig = data.getJSONObject(i).getJSONObject("images").getJSONObject("fixed_height");
                String url = orig.getString("url");
                if (url.toLowerCase().startsWith("https")) {
                    gifURLs.add(url);
                }
            }
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return gifURLs;
    }
}
