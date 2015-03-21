package com.twofours.surespot.chat;


import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.common.SurespotLog;

/**
 * @author adam
 * 
 */
public class SurespotErrorMessage {
	private static final String TAG = "SurespotErrorMessage";

	private String mId;
	private int mStatus;

	public String getId() {
		return mId;
	}

	public void setId(String id) {
		mId = id;
	}



	public int getStatus() {
		return mStatus;
	}

	public void setStatus(int status) {
		mStatus = status;
	}

	public static SurespotErrorMessage toSurespotMessage(String jsonString) {
		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(jsonString);
			return toSurespotErrorMessage(jsonObject);
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
	public static SurespotErrorMessage toSurespotErrorMessage(JSONObject jsonMessage) throws JSONException {

		SurespotErrorMessage surespotErrorMessage = new SurespotErrorMessage();

		surespotErrorMessage.setId(jsonMessage.getString("id"));
		surespotErrorMessage.setStatus(jsonMessage.getInt("status"));
		return surespotErrorMessage;
	}

	public JSONObject toJSONObject() {
		JSONObject message = new JSONObject();

		try {
			message.put("id", this.getId());
			message.put("status", this.getStatus());
			return message;
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "toJSONObject", e);
		}
		return null;

	}

}
