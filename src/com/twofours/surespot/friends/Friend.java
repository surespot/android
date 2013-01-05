package com.twofours.surespot.friends;

public class Friend {
	public static final int CHAT_TAB_OPEN = 1;
	public static final int NEW_MESSAGE = 2;
	public static final int NEW_FRIEND = 4;
	
	private String mName;
	private int mFlags;
	
	public String getName() {
		return mName;
	}
	public void setName(String name) {
		this.mName = name;
	}
	public int getFlags() {
		return mFlags;
	}
	public void setFlags(int flags) {
		this.mFlags = flags;
	}
	
	
	
}
