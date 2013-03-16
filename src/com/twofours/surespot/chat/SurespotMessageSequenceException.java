package com.twofours.surespot.chat;

public class SurespotMessageSequenceException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5804462488484202910L;
	private int mMessageId;

	public SurespotMessageSequenceException(int messageId) {
		super("message out of order, messageId: " + messageId);
		mMessageId = messageId;
	}

	public int getMessageId() {
		return mMessageId;
	}

	
	
}
