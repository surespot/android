package com.twofours.surespot.chat;

import android.text.TextUtils;

import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.utils.ChatUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Observable;

/**
 * @author adam
 */
public class SurespotMessage extends Observable implements Comparable<SurespotMessage> {
    private static final String TAG = "SurespotMessage";

    public final static String STATE_CREATED = "created";
    public final static String STATE_TRANSFERRING = "transferring";
    public final static String STATE_COMPLETE = "complete";
    public final static String STATE_ERRORED = "errored";
    public final static String STATE_FATALITY_ERRORED = "fatalityerrored";


    private String mState;
    private String mFrom;
    private String mTo;
    private String mIv;
    private String mData;
    private CharSequence mPlainData;
    private byte[] mPlainBinaryData;
    private Integer dataSize;
    private Integer mId;
    private int mErrorStatus;
    private String mMimeType;
    private Date mDateTime;
    private String mToVersion;

    private String mFromVersion;
    private boolean mDeleted;
    private boolean mShareable;

    private boolean mLoaded;
    private boolean mLoading;
    private boolean mGcm;
    private boolean mPlayVoice = false;
    private boolean mVoicePlayed = false;
    private boolean mHashed;
    private boolean mDownloadGif = false;

    private FileMessageData mFileMessageData;

    public String getState() {
        return mState;
    }

    public void setState(String state) {
        mState = state;
    }


    public String getFrom() {
        return mFrom;
    }

    public void setFrom(String from) {
        mFrom = from;
    }

    public String getTo() {
        return mTo;
    }

    public void setTo(String to) {
        mTo = to;
    }

    public String getData() {
        return mData;
    }

    public void setData(String data) {
        if (TextUtils.isEmpty(data)) {
            mData = null;
        }
        else {
            mData = data;
        }
    }

    public CharSequence getPlainData() {
        return mPlainData;
    }

    public byte[] getPlainBinaryData() {
        return mPlainBinaryData;
    }

    public void setPlainData(CharSequence charSequence) {
        mPlainData = charSequence;
    }

    public void setPlainBinaryData(byte[] plainData) {
        mPlainBinaryData = plainData;
    }

    public Integer getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }

    public boolean isPlayVoice() {
        return mPlayVoice;
    }

    public boolean isVoicePlayed() {
        return mVoicePlayed;
    }

    public void setVoicePlayed(boolean voicePlayed) {
        mVoicePlayed = voicePlayed;
    }

    public void setPlayMedia(boolean play) {
        mPlayVoice = play;
    }

    public Integer getId() {
        return mId;
    }

    public void setId(Integer id) {
        mId = id;
    }

    public String getOtherUser(String ourUser) {
        return ChatUtils.getOtherUser(ourUser, this.mFrom, this.mTo);
    }

    public String getTheirVersion(String ourUser) {
        String otherUser = ChatUtils.getOtherUser(ourUser, this.mFrom, this.mTo);
        if (mFrom.equals(otherUser)) {
            return getFromVersion();
        }
        else {
            return getToVersion();
        }
    }

    public String getOurVersion(String ourUser) {
        String otherUser = ChatUtils.getOtherUser(ourUser, this.mFrom, this.mTo);
        if (mFrom.equals(otherUser)) {
            return getToVersion();
        }
        else {
            return getFromVersion();
        }
    }

    public boolean isHashed() {
        return mHashed;
    }

    public void setHashed(boolean hashed) {
        this.mHashed = hashed;
    }

    public static SurespotMessage toSurespotMessage(String jsonString) {
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonString);
            return toSurespotMessage(jsonObject);
        }
        catch (JSONException e) {
            SurespotLog.w(TAG, "toSurespotMessage", e);
        }

        return null;

    }

    /**
     * @param jsonMessage
     * @return SurespotMessage
     * @throws JSONException
     */
    public static SurespotMessage toSurespotMessage(JSONObject jsonMessage) throws JSONException {

        SurespotMessage chatMessage = new SurespotMessage();

        chatMessage.setFrom(jsonMessage.getString("from"));
        chatMessage.setTo(jsonMessage.getString("to"));
        chatMessage.setIv(jsonMessage.getString("iv"));
        chatMessage.setData(jsonMessage.optString("data"));
        chatMessage.setMimeType(jsonMessage.getString("mimeType"));
        chatMessage.setToVersion(jsonMessage.optString("toVersion"));
        chatMessage.setFromVersion(jsonMessage.optString("fromVersion"));
        chatMessage.setShareable(jsonMessage.optBoolean("shareable", false));
        chatMessage.setVoicePlayed(jsonMessage.optBoolean("voicePlayed", false));
        chatMessage.setHashed(jsonMessage.optBoolean("hashed", false));
        chatMessage.setDownloadGif(jsonMessage.optBoolean("downloadGif", false));

        chatMessage.setGcm(jsonMessage.optBoolean("gcm", false));

        Integer id = jsonMessage.optInt("id");
        if (id > 0) {
            chatMessage.setId(id);
        }

        Integer errorStatus = jsonMessage.optInt("errorStatus");
        if (errorStatus > 0) {
            chatMessage.setErrorStatus(errorStatus);
        }

        long datetime = jsonMessage.optLong("datetime");
        if (datetime > 0) {
            chatMessage.setDateTime(new Date(datetime));
        }

        Integer dataSize = jsonMessage.optInt("dataSize");
        if (dataSize > 0) {
            chatMessage.setDataSize(dataSize);
        }

        //set plain data if encrypted data not present
        if (TextUtils.isEmpty(chatMessage.getData())) {
            chatMessage.setPlainData(jsonMessage.optString("plainData", null));
        }

        JSONObject joFileMessageData = jsonMessage.optJSONObject("fileMessageData");
        if (joFileMessageData != null) {
            chatMessage.setFileMessageData(FileMessageData.fromJSONObject(joFileMessageData));
        }

        return chatMessage;
    }

    public JSONObject toJSONObject(boolean withPlain) {
        JSONObject message = new JSONObject();

        try {
            message.put("to", this.getTo());
            message.put("from", this.getFrom());
            message.put("toVersion", this.getToVersion());
            message.put("fromVersion", this.getFromVersion());
            message.put("iv", this.getIv());
            message.put("data", this.getData());
            message.put("mimeType", this.getMimeType());
            message.put("shareable", this.isShareable());
            message.put("gcm", this.isGcm());
            message.put("voicePlayed", this.isVoicePlayed());
            message.put("hashed", this.isHashed());
            message.put("downloadGif", this.isDownloadGif());

            if (this.getErrorStatus() > 0) {
                message.put("errorStatus", this.getErrorStatus());
            }

            if (this.getId() != null) {
                message.put("id", this.getId());
            }

            if (this.getDateTime() != null) {
                message.put("datetime", this.getDateTime().getTime());
            }

            if (this.getDataSize() != null) {
                message.put("dataSize", this.getDataSize());
            }

            //if we don't have encrypted data, save plain data (for unsent messages only)
            if (TextUtils.isEmpty(this.getData()) && withPlain) {
                message.put("plainData", this.getPlainData());
            }

            if (this.getFileMessageData() != null) {
                message.put("fileMessageData", this.getFileMessageData().toJSONObject());
            }


            return message;
        }
        catch (JSONException e) {
            SurespotLog.w(TAG, "toJSONObject", e);
        }
        return null;

    }

    public JSONObject toJSONObjectSocket() {
        JSONObject message = new JSONObject();

        try {
            message.put("to", this.getTo());
            message.put("from", this.getFrom());
            message.put("toVersion", this.getToVersion());
            message.put("fromVersion", this.getFromVersion());
            message.put("iv", this.getIv());
            message.put("data", this.getData());
            message.put("mimeType", this.getMimeType());

            return message;
        }
        catch (JSONException e) {
            SurespotLog.w(TAG, "toJSONObjectSocket", e);
        }
        return null;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mData == null) ? 0 : mData.hashCode());
        result = prime * result + ((mDateTime == null) ? 0 : mDateTime.hashCode());
        result = prime * result + ((mFrom == null) ? 0 : mFrom.hashCode());
        result = prime * result + ((mFromVersion == null) ? 0 : mFromVersion.hashCode());
        result = prime * result + ((mId == null) ? 0 : mId.hashCode());
        result = prime * result + ((mIv == null) ? 0 : mIv.hashCode());
        result = prime * result + ((mMimeType == null) ? 0 : mMimeType.hashCode());
        result = prime * result + ((mPlainData == null) ? 0 : mPlainData.hashCode());
        result = prime * result + ((mTo == null) ? 0 : mTo.hashCode());
        result = prime * result + ((mToVersion == null) ? 0 : mToVersion.hashCode());
        result = prime * result + (mHashed ? 1 : 0);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        SurespotMessage rhs = (SurespotMessage) obj;

        if (this.getId() != null && rhs.getId() != null && this.getId().equals(rhs.getId())) {
            return true;
        }
        else {
            // iv should be unique across all messages
            return (this.getIv().equals(rhs.getIv()));
        }
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setMimeType(String mMimeType) {
        this.mMimeType = mMimeType;
    }

    public String getIv() {
        return mIv;
    }

    public void setIv(String mIv) {
        this.mIv = mIv;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public void setLoaded(boolean mLoaded) {
        this.mLoaded = mLoaded;
    }

    public boolean isLoading() {
        return mLoading;
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    public boolean isGcm() {
        return mGcm;
    }

    public void setGcm(boolean gcm) {
        mGcm = gcm;
    }

    public Date getDateTime() {
        return mDateTime;
    }

    public void setDateTime(Date mDateTime) {
        this.mDateTime = mDateTime;
    }

    public String getToVersion() {
        return mToVersion;
    }

    public void setToVersion(String toVersion) {
        if (TextUtils.isEmpty(toVersion)) {
            mToVersion = null;
        }
        else {
            mToVersion = toVersion;
        }
    }

    public String getFromVersion() {
        return mFromVersion;
    }

    public void setFromVersion(String fromVersion) {
        if (TextUtils.isEmpty(fromVersion)) {
            mFromVersion = null;
        }
        else {
            mFromVersion = fromVersion;
        }
    }

    public boolean getDeleted() {
        return mDeleted;
    }

    public void setDeleted(Boolean deleted) {
        mDeleted = deleted;
    }

    public boolean isShareable() {
        return mShareable;
    }

    public void setShareable(boolean shareable) {
        if (shareable != mShareable) {
            mShareable = shareable;
            setChanged();
            notifyObservers();
        }
    }

    public boolean isDownloadGif() {
        return mDownloadGif;
    }

    public void setDownloadGif(boolean downloadGif) {
        this.mDownloadGif = downloadGif;
    }


    @Override
    public int compareTo(SurespotMessage another) {

        Integer thisId = this.getId();
        Integer rhsId = another.getId();

        if (thisId == rhsId) {
            return 0;
        }

        // if we're null we want to be at the bottom of list
        if (thisId == null && rhsId != null) {
            return 1;
        }

        if (rhsId == null && thisId != null) {
            // should never be true
            return -1;
        }

        return thisId.compareTo(rhsId);
    }

    public void setErrorStatus(int status) {
        mErrorStatus = status;
    }

    public int getErrorStatus() {
        return mErrorStatus;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nSurespotMessage:\n");
        sb.append("\tid: " + getId() + "\n");
        sb.append("\tto: " + getTo() + "\n");
        sb.append("\tfrom: " + getFrom() + "\n");
        sb.append("\ttoVersion: " + getToVersion() + "\n");
        sb.append("\tfromVersion: " + getFromVersion() + "\n");
        sb.append("\tiv: " + getIv() + "\n");
        sb.append("\tdata: " + getData() + "\n");
        sb.append("\tdataSize: " + getDataSize() + "\n");
        sb.append("\tmimeType: " + getMimeType() + "\n");
        // sb.append("\tdeletedTo: " + getDeletedTo() + "\n");
        sb.append("\tshareable: " + isShareable() + "\n");
        sb.append("\terrorStatus: " + getErrorStatus() + "\n");
        sb.append("\tdatetime: " + getDateTime() + "\n");
        sb.append("\tgcm: " + isGcm() + "\n");
        sb.append("\tvoicePlayed: " + isVoicePlayed() + "\n");

        if (getFileMessageData() != null) {
            sb.append(getFileMessageData());
        }
        //sb.append("\thashed: " + isHashed() + "\n");

        return sb.toString();
    }

    public static boolean areMessagesEqual(SurespotMessage lastMessage, SurespotMessage message) {
        return lastMessage.getIv() != null && lastMessage.getIv().equals(message.getIv());
    }

    public FileMessageData getFileMessageData() {
        return mFileMessageData;
    }

    public void setFileMessageData(FileMessageData fileMessageData) {
        mFileMessageData = fileMessageData;
    }

    public static class FileMessageData {
        private String mMimeType;
        private String mFilename;
        private String mLocalUri;
        private String mCloudUrl;
        private long mSize;

        public String getLocalUri() {
            return mLocalUri;
        }

        public void setLocalUri(String localUri) {
            mLocalUri = localUri;
        }


        public long getSize() {
            return mSize;
        }

        public void setSize(long size) {
            mSize = size;
        }

        public void setFilename(String filename) {
            mFilename = filename;
        }

        public String getFilename() {
            return mFilename;
        }

        String toJSONStringSocket() {
            JSONObject jo = new JSONObject();
            try {
                jo.put("filename", getFilename());
                jo.put("cloudUrl", mCloudUrl);
                jo.put("size", mSize);
                jo.put("mimeType", mMimeType);

                return jo.toString();
            }
            catch (JSONException e) {
                SurespotLog.e(TAG, e, "toJSONString error");
                return null;
            }

        }

        public static FileMessageData fromJSONString(String jsonString) {
            try {
                JSONObject jo = new JSONObject(jsonString);

                FileMessageData fmd = new FileMessageData();

                fmd.setFilename(jo.optString("filename"));
                fmd.setLocalUri(jo.optString("localUri"));
                fmd.setCloudUrl(jo.optString("cloudUrl"));
                fmd.setSize(jo.optInt("size"));
                fmd.setMimeType(jo.optString("mimeType"));

                return fmd;

            }
            catch (JSONException e) {
                SurespotLog.e(TAG, e, "fromJSONString error");
                return null;
            }

        }



        JSONObject toJSONObject() {
            JSONObject jo = new JSONObject();
            try {
                jo.put("filename", mFilename);
                jo.put("localUri", mLocalUri);
                jo.put("cloudUrl", mCloudUrl);
                jo.put("size", mSize);
                jo.put("mimeType", mMimeType);

                return jo;
            }
            catch (JSONException e) {
                SurespotLog.e(TAG, e, "toJSONString error");
                return null;
            }

        }


        static FileMessageData fromJSONObject(JSONObject jsonObject) {
            if (jsonObject == null) {
                return null;
            }
            //   try {
            JSONObject jo = jsonObject;

            FileMessageData fmd = new FileMessageData();

            fmd.setSize(jo.optLong("size"));
            fmd.setFilename(jo.optString("filename"));
            fmd.setLocalUri(jo.optString("localUri"));
            fmd.setCloudUrl(jo.optString("cloudUrl"));
            fmd.setMimeType(jo.optString("mimeType"));

            return fmd;

//            }
//            catch (JSONException e) {
//                SurespotLog.e(TAG, e, "fromJSONString error");
//                return null;
//            }

        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\nFileMessageData:\n");
            sb.append("\tfilename: " + getFilename() + "\n");
            sb.append("\tlocalUri: " + getLocalUri() + "\n");
            sb.append("\tsize: " + getSize() + "\n");
            sb.append("\tcloudUrl: " + getCloudUrl() + "\n");
            sb.append("\tmimeType: " + getMimeType() + "\n");
            return sb.toString();
        }

        public String getMimeType() {
            return mMimeType;
        }

        public void setMimeType(String mimeType) {
            mMimeType = mimeType;
        }

        public String getCloudUrl() {
            return mCloudUrl;
        }

        public void setCloudUrl(String cloudUrl) {
            mCloudUrl = cloudUrl;
        }


    }
}
