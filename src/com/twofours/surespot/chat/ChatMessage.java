package com.twofours.surespot.chat;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatMessage {
	private String mFrom;
	private String mTo;
	private String mCipherText;
	private String mPlainText;
	
	
	public String getFrom() {
		return mFrom;
	}
	public void setFro(String from) {
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
	
	public static ChatMessage toChatMessage(JSONObject jsonMessage) throws JSONException {
		ChatMessage chatMessage = new ChatMessage();
		 
		chatMessage.setFro(jsonMessage.getString("from"));
		chatMessage.setTo(jsonMessage.getString("to"));
		chatMessage.setCipherText(jsonMessage.getString("text"));

		return chatMessage;
	}
}
