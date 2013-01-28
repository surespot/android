package com.twofours.surespot.chat;

import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.Utils;

/**
 * @author adam
 * 
 */
public class SurespotMessage {
	private String mFrom;
	private String mTo;
	private String mIv;
	private String mCipherData;
	private String mPlainData;
	private String mId;
	private String mResendId;
	private String mMimeType;
	private int mHeight;

	private boolean mLoading;

	public SurespotMessage() {

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

	public String getCipherData() {
		return mCipherData;
	}

	public void setCipherData(String cipherText) {
		mCipherData = cipherText;
	}

	public String getPlainData() {
		return mPlainData;
	}

	public void setPlainData(String plainText) {
		mPlainData = plainText;
	}

	public String getId() {
		return mId;
	}

	public void setId(String id) {
		mId = id;
	}

	public String getResendId() {
		return mResendId;
	}

	public void setResendId(String resendId) {
		this.mResendId = resendId;
	}

	public String getSpot() {
		return Utils.getOtherUser(this.mFrom, this.mTo);
	}
	
	public static SurespotMessage toSurespotMessage(String jsonString) {
		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return toSurespotMessage(jsonObject);
		}
		catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

		String id = jsonMessage.optString("id");
		if (id != null && !id.isEmpty()) {
			chatMessage.setId(id);
		}
		chatMessage.setFrom(jsonMessage.getString("from"));
		chatMessage.setTo(jsonMessage.getString("to"));
		chatMessage.setCipherData(jsonMessage.optString("data", null));
		chatMessage.setMimeType(jsonMessage.getString("mimeType"));
		chatMessage.setIv(jsonMessage.getString("iv"));
		chatMessage.setHeight(jsonMessage.optInt("height"));
		String resendId = jsonMessage.optString("resendId");
		if (resendId != null && !resendId.isEmpty()) {
			chatMessage.setResendId(resendId);
		}

		return chatMessage;
	}

	public JSONObject toJSONObject() {
		JSONObject message = new JSONObject();

		try {
			if (this.getId() != null) {
				message.put("id", this.getId());
			}

			message.put("data", this.getCipherData());
			message.put("to", this.getTo());
			message.put("from", this.getFrom());
			message.put("resendId", this.getResendId());
			message.put("mimeType", this.getMimeType());
			message.put("iv", this.getIv());

			message.put("height", this.getHeight());

			return message;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

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
		} else {
			// iv should be unique across all messages
			return (this.getIv().equals(rhs.getIv()));
		}
	}

	public int hashCode() {
		return this.getCipherData().hashCode() + this.getFrom().hashCode() + this.getTo().hashCode();
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
	
	

}
