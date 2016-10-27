package com.twofours.surespot.network;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by adam on 3/31/16.
 */
public class MainThreadCallbackWrapper implements Callback {
    private MainThreadCallback mCallback;

    public MainThreadCallbackWrapper(MainThreadCallback callback) {
        mCallback = callback;
    }


    @Override
    public void onFailure(final Call call, final IOException e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCallback.onFailure(call, e);
            }
        });
    }

    @Override
    public void onResponse(final Call call, final Response response) throws IOException {
        //force body to download
        final String responseString = response.body().string();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mCallback.onResponse(call, response, responseString);
                }
                catch (IOException e) {
                    mCallback.onFailure(call, e);
                }
                finally {
                    response.body().close();
                }
            }
        });
    }
    private void runOnUiThread(Runnable task) {
        new Handler(Looper.getMainLooper()).post(task);
    }

    public interface MainThreadCallback {

        void onFailure(Call call, IOException e);
        void onResponse(Call call, Response response, String responseString) throws IOException;
    }

}
