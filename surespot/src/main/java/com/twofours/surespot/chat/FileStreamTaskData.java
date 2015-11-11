package com.twofours.surespot.chat;

import org.spongycastle.crypto.tls.NewSessionTicket;

/**
 * Created by adam on 11/10/15.
 */
public class FileStreamTaskData {

    public enum TaskStatus {
        NEW, INPROGRESS, COMPLETE
    }

    private String mFrom;
    private String mTo;


    private String mIv;
    private String mMimeType;
    private String mLocalFilePath;
    private int mStatusCode;
    private int mTaskStatus;


    private String TAG;

    public FileStreamTaskData(String from, String to, String iv, String mimeType, String localFilePath) {
        this.mFrom = from;
        this.mTo = to;
        this.mIv = iv;
        this.mMimeType = mimeType;
        this.mLocalFilePath = localFilePath;

        TAG = String.format("FileStreamTaskData, from: %s, to: %s, mimeType: %s", mFrom, mTo, mMimeType);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileStreamTaskData that = (FileStreamTaskData) o;

        return !(mIv != null ? !mIv.equals(that.mIv) : that.mIv != null);

    }

    @Override
    public int hashCode() {
        return mIv != null ? mIv.hashCode() : 0;
    }

    public String getFrom() {
        return mFrom;
    }


    public String getTo() {
        return mTo;

    }

    public String getIv() {
        return mIv;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getLocalFilePath() {
        return mLocalFilePath;
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    public void setStatusCode(int statusCode) {
        mStatusCode = statusCode;
    }

    public int getTaskStatus() {
        return mTaskStatus;
    }

    public void setTaskStatus(TaskStatus taskStatus) {
        mTaskStatus = taskStatus.ordinal();
    }


}
