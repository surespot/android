package com.twofours.surespot.friends;

import android.util.Log;

public class Friend implements Comparable<Friend> {
	public static final int NEW_FRIEND = 1;
	public static final int ACTIVE_CHAT = 2;
	private static final String TAG = "Friend";
	

	private String mName;
	private int mFlags;
	private int mMessageCount;

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

	public Integer getMessageCount() {
		return mMessageCount;
	}

	public synchronized void incMessageCount(int messageCount) {
		mMessageCount += messageCount;
		Log.v(TAG, "newCount: " + mMessageCount);
	}
	
	public synchronized void setMessageCount(int messageCount) {
		mMessageCount = messageCount;
	}

	@Override
	public int compareTo(Friend another) {

		// if they're not the same order them by flag
		if (another.getFlags() != this.getFlags()) {
			return Integer.valueOf(another.getFlags()).compareTo(this.getFlags());
		}
		else {
			// flags are the same, order by name
			return this.getName().compareTo(another.getName());
		}

	}
}
