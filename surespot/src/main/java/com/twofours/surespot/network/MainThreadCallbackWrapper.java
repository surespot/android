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
        //catch the ioexception here otherwise it is never propogated @(%*&#(&@(*
        //https://github.com/square/okhttp/issues/1066
        //why'd I switch to okhttp???

        String bodyString = null;
        try {
            bodyString = response.body().string();
            response.body().close();
        }
        catch (final IOException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onFailure(call, e);
                }
            });
            return;
        }

        final String responseString = bodyString;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mCallback.onResponse(call, response, responseString);
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

    public interface MainThreadCallback {

        void onFailure(Call call, IOException e);

        void onResponse(Call call, Response response, String responseString) throws IOException;
    }

}
