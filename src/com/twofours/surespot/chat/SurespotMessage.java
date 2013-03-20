package com.twofours.surespot.chat;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.common.SurespotLog;

/**
 * @author adam
 * 
 */
public class SurespotMessage implements Comparable<SurespotMessage> {
	private static final String TAG = "SurespotMessage";

	private String mFrom;
	private String mTo;
	private String mIv;
	private String mData;
	private String mPlainData;
	private Integer mId;
	private Integer mResendId;
	private String mMimeType;
	private int mHeight;
	private Date mDateTime;
	private String mToVersion;

	private String mFromVersion;
	private boolean mDeletedTo;
	private boolean mShareable;
	
	private boolean mLoading;

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
		mData = data;
	}

	public String getPlainData() {
		return mPlainData;
	}

	public void setPlainData(String plainText) {
		mPlainData = plainText;
	}

	public Integer getId() {
		return mId;
	}

	public void setId(Integer id) {
		mId = id;
	}

	public Integer getResendId() {
		return mResendId;
	}

	public void setResendId(Integer resendId) {
		this.mResendId = resendId;
	}

	public String getOtherUser() {
		return ChatUtils.getOtherUser(this.mFrom, this.mTo);
	}

	public String getTheirVersion() {
		String otherUser = ChatUtils.getOtherUser(this.mFrom, this.mTo);
		if (mFrom.equals(otherUser)) {
			return getFromVersion();
		}
		else {
			return getToVersion();
		}
	}

	public String getOurVersion() {
		String otherUser = ChatUtils.getOtherUser(this.mFrom, this.mTo);
		if (mFrom.equals(otherUser)) {
			return getToVersion();
		}
		else {
			return getFromVersion();
		}
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
		chatMessage.setToVersion(jsonMessage.getString("toVersion"));
		chatMessage.setFromVersion(jsonMessage.getString("fromVersion"));
		chatMessage.setDeletedTo(jsonMessage.optBoolean("deletedTo", false));
		chatMessage.setShareable(jsonMessage.optBoolean("shareable", false));

		Integer id = jsonMessage.optInt("id");
		if (id > 0) {
			chatMessage.setId(id);
		}

		Integer height = jsonMessage.optInt("height");
		if (height > 0) {
			chatMessage.setHeight(jsonMessage.optInt("height"));
		}

		Integer resendId = jsonMessage.optInt("resendId");
		if (resendId > 0) {
			chatMessage.setResendId(resendId);
		}

		long datetime = jsonMessage.optLong("datetime");
		if (datetime > 0) {
			chatMessage.setDateTime(new Date(datetime));
		}

		return chatMessage;
	}

	public JSONObject toJSONObject() {
		JSONObject message = new JSONObject();

		try {
			message.put("to", this.getTo());
			message.put("from", this.getFrom());
			message.put("toVersion", this.getToVersion());
			message.put("fromVersion", this.getFromVersion());
			message.put("iv", this.getIv());
			message.put("data", this.getData());
			message.put("mimeType", this.getMimeType());
			message.put("deletedTo", this.getDeletedTo());
			message.put("shareable", this.isShareable());

			if (this.getId() != null) {
				message.put("id", this.getId());
			}

			if (this.getResendId() != null) {
				message.put("resendId", this.getResendId());
			}

			if (this.getDateTime() != null) {
				message.put("datetime", this.getDateTime().getTime());
			}
			if (this.getHeight() > 0) {
				message.put("height", this.getHeight());
			}

			return message;
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "toJSONObject", e);
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
		result = prime * result + mHeight;
		result = prime * result + ((mId == null) ? 0 : mId.hashCode());
		result = prime * result + ((mIv == null) ? 0 : mIv.hashCode());
		result = prime * result + (mLoading ? 1231 : 1237);
		result = prime * result + ((mMimeType == null) ? 0 : mMimeType.hashCode());
		result = prime * result + ((mPlainData == null) ? 0 : mPlainData.hashCode());
		result = prime * result + ((mResendId == null) ? 0 : mResendId.hashCode());

		result = prime * result + ((mTo == null) ? 0 : mTo.hashCode());
		result = prime * result + ((mToVersion == null) ? 0 : mToVersion.hashCode());

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

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

	public boolean isLoading() {
		return mLoading;
	}

	public void setLoading(boolean mLoading) {
		this.mLoading = mLoading;
	}

	public Integer getHeight() {
		return mHeight;
	}

	public void setHeight(Integer mHeight) {
		this.mHeight = mHeight;
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
		mToVersion = toVersion;
	}

	public String getFromVersion() {
		return mFromVersion;
	}

	public void setFromVersion(String fromVersion) {
		mFromVersion = fromVersion;
	}

	public boolean getDeletedTo() {
		return mDeletedTo;
	}

	public void setDeletedTo(Boolean deletedTo) {
		mDeletedTo = deletedTo;
	}

	

	public boolean isShareable() {
		return mShareable;
	}

	public void setShareable(boolean shareable) {
		mShareable = shareable;
	}

	@Override
	public int compareTo(SurespotMessage another) {

		Integer thisId = this.getId();
		Integer rhsId = another.getId();

		if (thisId == rhsId)
			return 0;

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

}
