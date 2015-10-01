package com.twofours.surespot.chat;

import com.twofours.surespot.network.IAsyncCallback;

import java.io.FileInputStream;

public class FileStreamMessage {
    public String mTo;
    public String mIv;
    public FileInputStream mStream;
    public String mMimeType;
    public IAsyncCallback<Integer> mAsyncCallback;
}
