package com.twofours.surespot.chat;

import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class ChatMessage {
	private String mFrom;
	private String mTo;
	private String mCipherText;
	private String mPlainText;
	private String mId;
	private String mResendId;

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

	public String getCipherText() {
		return mCipherText;
	}

	public void setCipherText(String cipherText) {
		mCipherText = cipherText;
	}

	public String getPlainText() {
		return mPlainText;
	}

	public void setPlainText(String plainText) {
		mPlainText = plainText;
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

	public String getRoom() {
		return Utils.getOtherUser(this.mFrom, this.mTo);
	}

	public static ChatMessage toChatMessage(JSONObject jsonMessage) throws JSONException {
		ChatMessage chatMessage = new ChatMessage();

		String id = jsonMessage.optString("id");
		if (id != null && !id.isEmpty()) {
			chatMessage.setId(id);
		}
		chatMessage.setFrom(jsonMessage.getString("from"));
		chatMessage.setTo(jsonMessage.getString("to"));
		chatMessage.setCipherText(jsonMessage.getString("text"));
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
			message.put("text", this.getCipherText());
			message.put("to", this.getTo());
			message.put("from", this.getFrom());
			message.put("resendId", this.getResendId());

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

		ChatMessage rhs = (ChatMessage) obj;

		if (this.getId() != null && rhs.getId() != null && this.getId().equals(rhs.getId())) {
			return true;
		} else {

			return this.getCipherText().equals(rhs.getCipherText()) && this.getTo().equals(rhs.getTo())
					&& this.getFrom().equals(rhs.getFrom()) && ((this.getId() == null) || rhs.getId() == null);
		}

	}

	public int hashCode() {
		return this.getCipherText().hashCode() + this.getFrom().hashCode() + this.getTo().hashCode();
	}

}
