package com.twofours.surespot.chat;

import android.os.AsyncTask;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class FileStreamTask extends AsyncTask<Void, Void, Void> {

    private IAsyncCallback<Void> mCallback;
    private FileStreamTaskData mMessage;
    private ChatAdapter mChatAdapter;

    private int mStatusCode;

    private String TAG;

    public FileStreamTask(FileStreamTaskData message) {
        mMessage = message;
        //0 = haven't executed
        mStatusCode = 0;

        TAG = String.format("FileStreamTask, from: %s, to: %s, mimeType: %s", mMessage.getFrom(), mMessage.getTo(), mMessage.getMimeType());
    }

    public void setCallback(IAsyncCallback<Void> callback) {
        mCallback = callback;
    }

    @Override
    protected void onPreExecute() {
        ChatController chatController = getChatController();
        if (chatController != null) {
            mChatAdapter = chatController.getChatAdapter(mMessage.getTo(), false);
        }
    }

    @Override
    protected Void doInBackground(Void... voids) {



        //post message via http if we have network controller for the from user
        NetworkController networkController = SurespotApplication.getNetworkController();
        if (networkController != null && mMessage.getFrom().equals(networkController.getUsername())) {
// upload encrypted image to server
            FileInputStream uploadStream;
            try {
                uploadStream = new FileInputStream(mMessage.getLocalFilePath());
            } catch (FileNotFoundException e) {
                SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
                mStatusCode = 500;
                //return setMessageStatus(500);
                return null;
            }


            mStatusCode = networkController.postFileStreamSync(IdentityController.getOurLatestVersion(mMessage.getFrom()), mMessage.getTo(), IdentityController.getTheirLatestVersion(mMessage.getTo()),
                    mMessage.getIv(), uploadStream, mMessage.getMimeType());

//            switch (statusCode) {
//                case 200:
//                    break;
//                case 401,402,403,404:
//                    SurespotLog.i(TAG, "got 402 from server");
//                    return setMessageStatus(402);
//
//
//                default:
//                    SurespotLog.i(TAG, "got 500 from server");
//                    return setMessageStatus(500);
//            }

        }
        else {
            SurespotLog.i(TAG, "network controller null or different user");
            mStatusCode = 500;
        }

        //done
        mCallback.handleResponse(null);
        mCallback = null;
        return null;
    }

//    @Override
//    protected void onPostExecute(Boolean notify) {
//        if (mChatAdapter != null && notify) {
//            mChatAdapter.notifyDataSetChanged();
//        }
//    }

    private ChatController getChatController() {
        ChatController chatController = SurespotApplication.getChatController();

        //if they're different do nothing we'll get it on the reload

        if (chatController != null) {

            if (mMessage.getFrom().equals(chatController.getUsername())) {
                return chatController;
            }

        }

        return null;
    }

    private boolean setMessageStatus(int status) {

        if (mChatAdapter != null) {
            SurespotMessage message = mChatAdapter.getMessageByIv(mMessage.getIv());
            message.setErrorStatus(status);
            return true;
        }


        return false;
    }

    public int getStatusCode() {
        return mStatusCode;
    }
}
