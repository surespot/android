package com.twofours.surespot.chat;

import android.app.Activity;

import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;

import java.io.FileInputStream;

public class FileStreamMessage {
    public String mTo;
    public String mIv;
    public FileInputStream mStream;
    public String mMimeType;
    public IAsyncCallback<Integer> mAsyncCallback;
    public String mLocalFilePath;
    public ChatController mChatController;
    public Activity mActivity;


    public FileStreamMessage() {



            mAsyncCallback = new IAsyncCallback<Integer>() {

            @Override
            public void handleResponse(Integer statusCode) {
                // if it failed update the message
                SurespotLog.v(TAG, "postFileStream complete, result: %d", statusCode);
                ChatAdapter chatAdapter = null;
                switch (statusCode) {
                    case 200:
                        break;
                    case 402:
                        if (finalMessage != null) {
                            finalMessage.setErrorStatus(402);
                        }
                        chatAdapter = mChatController.getChatAdapter(mActivity, mTo);
                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                        break;
                    default:
                        if (finalMessage != null) {
                            finalMessage.setErrorStatus(500);
                        }
                        chatAdapter = mChatController.getChatAdapter(mActivity, mTo);
                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                }

                callback.handleResponse(true);
            }
        };
    }
}
