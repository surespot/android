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
    private Callback mCallback;

    public MainThreadCallbackWrapper(Callback callback) {
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mCallback.onResponse(call, response);
                }
                catch (IOException e) {
                    mCallback.onFailure(call, e);
                }
            }
        });
    }
    private void runOnUiThread(Runnable task) {
        new Handler(Looper.getMainLooper()).post(task);
    }
}
