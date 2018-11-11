package com.twofours.surespot.filetransfer;

import android.content.Context;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.PBFileUtils;
import com.twofours.surespot.utils.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;



/**
 * Created by adam on 3/26/17.
 */

public class FileTransferManager {
    private static HashMap<String, FileTransferTask> mTasks;

    private static boolean mRecreated;

    static {
        mTasks = new HashMap<>();
        mRecreated = false;
    }


    public static void download(Context context, String ourUsername, SurespotMessage message, IAsyncCallback<String> callback) {
        FileTransferTask task = mTasks.get(message.getIv());

        if (task == null) {
            task = new FileTransferTask(context, ourUsername, message, callback);
            mTasks.put(message.getIv(), task);
            SurespotApplication.THREAD_POOL_EXECUTOR.execute(task);
        }

    }

    public static class FileTransferTask implements Runnable{
        private String TAG = "FileTransferTask";
        private Context mContext;

        private String mOurUsername;
        private String mOurVersion;
        private String mTheirUsername;
        private String mTheirVersion;
        private String mFilename;
        private String mIv;
        private String mUrl;

        private IAsyncCallback<String> mCallback;



        public FileTransferTask(Context context, String ourUsername, SurespotMessage message, IAsyncCallback<String> callback) {
            mContext = context;
            mOurUsername = ourUsername;
            mTheirUsername = message.getOtherUser(mOurUsername);
            mOurVersion = message.getOurVersion(mOurUsername);
            mTheirVersion = message.getTheirVersion(mOurUsername);
            mIv = message.getIv();
            mUrl = message.getFileMessageData().getCloudUrl();
            mFilename = message.getFileMessageData().getFilename();
            mCallback = callback;
        }

        public String getIv() {
            return mIv;
        }

        public String getUrl() {
            return mUrl;
        }

        @Override
        public void run() {
            InputStream encryptedFileStream = NetworkManager.getNetworkController(mContext).getFileStream(getUrl());

            PipedOutputStream out = new PipedOutputStream();
            PipedInputStream inputStream = null;
            try {
                inputStream = new PipedInputStream(out);

                EncryptionController.runDecryptTask(mContext, mOurUsername, mOurVersion, mTheirUsername, mTheirVersion, mIv, true,
                        new BufferedInputStream(encryptedFileStream), out);

                File file = new File(String.format("/sdcard/Download/%s", mFilename));
                Utils.copyStreamToFile(inputStream, file);
                SurespotLog.d(TAG, "Stream downloaded and decrypted to file.");
                mCallback.handleResponse(PBFileUtils.getUri(file).toString());

            }
            catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}
