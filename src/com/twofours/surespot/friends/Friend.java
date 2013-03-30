package com.twofours.surespot.friends;

import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.common.SurespotLog;

public class Friend implements Comparable<Friend> {
	public static final int INVITER = 32;
	public static final int MESSAGE_ACTIVITY = 16;
	public static final int CHAT_ACTIVE = 8;
	public static final int NEW_FRIEND = 4;
	public static final int INVITED = 2;
	public static final int DELETED = 1;

	private static final String TAG = "Friend";

	private String mName;
	private int mFlags;
	private int mLastViewedMessageId;
	private int mAvailableMessageId;
	private int mLastReceivedMessageControlId;
	private int mAvailableMessageControlId;
	private int mLastReceivedUserControlId;

	public Friend(String name) {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		this.mName = name;
	}

	public int getLastViewedMessageId() {
		return mLastViewedMessageId;
	}

	public void setLastViewedMessageId(int lastViewedMessageId) {
		if (lastViewedMessageId > 0) {
			mLastViewedMessageId = lastViewedMessageId;
		}
		else {
			mLastViewedMessageId = mAvailableMessageId;
		}
	}

	public int getAvailableMessageId() {
		return mAvailableMessageId;
	}

	public void setAvailableMessageId(int availableMessageId) {
		if (availableMessageId > 0) {
			mAvailableMessageId = availableMessageId;
		}
	}
	
	public int getAvailableMessageControlId() {
		return mAvailableMessageControlId;
	}

	public void setAvailableMessageControlId(int availableMessageControlId) {
		if (availableMessageControlId > 0) {
			mAvailableMessageControlId = availableMessageControlId;
		}
	}

	public int getLastReceivedMessageControlId() {
		return mLastReceivedMessageControlId;
	}

	public void setLastReceivedMessageControlId(int lastReceivedMessageControlId) {
		mLastReceivedMessageControlId = lastReceivedMessageControlId;
	}

	public int getLastReceivedUserControlId() {
		return mLastReceivedUserControlId;
	}

	public void setLastReceivedUserControlId(int lastReceivedUserControlId) {
		mLastReceivedUserControlId = lastReceivedUserControlId;
	}

	public void setChatActive(boolean set) {
		if (set) {
			mFlags |= CHAT_ACTIVE;
			setNewFriend(false);
		}
		else {
			mFlags &= ~CHAT_ACTIVE;
		}
	}

	// public void setMessageActivity(boolean set) {
	// if (set) {
	// mFlags |= MESSAGE_ACTIVITY;
	// }
	// else {
	// mFlags &= ~MESSAGE_ACTIVITY;
	// }
	// }

	public void setInviter(boolean set) {
		if (set) {
			mFlags |= INVITER;
		}
		else {
			mFlags &= ~INVITER;
		}
	}

	public boolean isInviter() {
		return (mFlags & INVITER) == INVITER;
	}

	public void setInvited(boolean set) {
		if (set) {
			mFlags |= INVITED;
		}
		else {
			mFlags &= ~INVITED;
		}
	}

	public boolean isInvited() {		
		return (mFlags & INVITED) == INVITED;
	}


	public void setDeleted() {
		mFlags = DELETED;		
	}

	
	public boolean isDeleted() {
		return (mFlags & DELETED) == DELETED;
	}

	
	public void setNewFriend(boolean set) {
		if (set) {
			mFlags |= NEW_FRIEND;
			mFlags &= ~INVITED;
			mFlags &= ~INVITER;
			mFlags &= ~DELETED;
		}
		else {
			mFlags &= ~NEW_FRIEND;
		}
	}

	public boolean isNewFriend() {
		return (mFlags & NEW_FRIEND) == NEW_FRIEND;
	}

	public boolean isMessageActivity() {
		// return (mFlags & MESSAGE_ACTIVITY) == MESSAGE_ACTIVITY;
		SurespotLog.v(TAG, mName + ": isMessageActivity, lastviewed: " + mLastViewedMessageId + ", lastAvailable: " + mAvailableMessageId);
		return mAvailableMessageId - mLastViewedMessageId > 0;
	}

	public boolean isFriend() {
		return (!isInvited() && !isInviter());
	}

	public int getFlags() {
		return mFlags;
	}

	public void setFlags(int flags) {
		mFlags = flags;
	}

	public boolean isChatActive() {
		return (mFlags & CHAT_ACTIVE) == CHAT_ACTIVE;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		Friend rhs = (Friend) obj;
		return this.getName().equals(rhs.getName());
	}

	@Override
	public int compareTo(Friend another) {
		// if the flags are the same sort by name
		// not active or invite, sort by name
		if ((another.getFlags() == this.getFlags()) || (another.getFlags() < MESSAGE_ACTIVITY && this.getFlags() < MESSAGE_ACTIVITY)) {
			return this.getName().compareTo(another.getName());
		}
		else {
			// sort by flag value
			return Integer.valueOf(another.getFlags()).compareTo(this.getFlags());
		}

	}

	public boolean update(JSONObject jsonFriend) {

		String status;

		try {

			String name = jsonFriend.getString("name");
			if (name.equals(this.getName())) {
				status = jsonFriend.getString("status");

				if (status.equals("invited")) {
					this.setInvited(true);
				}

				else {
					if (status.equals("invitee")) {
						this.setInviter(true);
					}
				}

				this.setName(jsonFriend.getString("name"));
				setNewFriend(false);
				return true;
			}

		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "update", e);
		}
		return false;
	}

	public static Friend toFriend(JSONObject jsonFriend) throws JSONException {
		Friend friend = new Friend(jsonFriend.getString("name"));

		friend.setFlags(jsonFriend.optInt("flags"));
		friend.setLastReceivedMessageControlId(jsonFriend.optInt("lastReceivedMessageControlId"));
		friend.setAvailableMessageId(jsonFriend.optInt("lastAvailableMessageId"));
		friend.setLastReceivedUserControlId(jsonFriend.optInt("lastReceivedUserControlId"));
		friend.setLastViewedMessageId(jsonFriend.optInt("lastViewedMessageId"));

		return friend;
	}

	public void update(Friend friend) {
		this.setNewFriend(false);
		this.setInvited(friend.isInvited());
		this.setInviter(friend.isInviter());
		// this.setChatActive(friend.isChatActive());
		// this.setMessageActivity(friend.isMessageActivity());
	}

	public JSONObject toJSONObject() {
		JSONObject jsonFriend = new JSONObject();

		try {
			jsonFriend.put("name", this.getName());
			jsonFriend.put("flags", this.getFlags());
			jsonFriend.put("lastReceivedMessageControlId", this.getLastReceivedMessageControlId());
			jsonFriend.put("lastAvailableMessageId", this.mAvailableMessageId);
			jsonFriend.put("lastReceivedUserControlId", this.getLastReceivedUserControlId());
			jsonFriend.put("lastViewedMessageId", this.getLastViewedMessageId());

			return jsonFriend;
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "toJSONObject", e);
		}
		return null;

	}

};
