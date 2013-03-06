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
	
	private static final String TAG = "Friend";

	private String mName;
	private int mFlags;

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		this.mName = name;
	}

	public void setChatActive(boolean set) {
		if (set) {
			mFlags |= CHAT_ACTIVE;
		}
		else {
			mFlags &= ~CHAT_ACTIVE;
		}
	}
	
	public void setMessageActivity(boolean set) {
		if (set) {
			mFlags |= MESSAGE_ACTIVITY;
		}
		else {
			mFlags &= ~MESSAGE_ACTIVITY;
		}
	}
	

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

	public void setNewFriend(boolean set) {
		if (set) {
			mFlags |= NEW_FRIEND;
			mFlags &= ~INVITED;
			mFlags &= ~INVITER;
		}
		else {
			mFlags &= ~NEW_FRIEND;
		}
	}

	public boolean isNewFriend() {
		return (mFlags & NEW_FRIEND) == NEW_FRIEND;
	}
	
	public boolean isMessageActivity() {
		return (mFlags & MESSAGE_ACTIVITY) == MESSAGE_ACTIVITY;
	}

	public boolean isFriend() {
		return (!isInvited() && !isInviter());
	}

	public int getFlags() {
		return mFlags;
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
		if ((another.getFlags() == this.getFlags())
				|| (another.getFlags() < MESSAGE_ACTIVITY && this.getFlags() < MESSAGE_ACTIVITY)) {
			return this.getName().compareTo(another.getName());
		} else {
			//sort by flag value
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
		Friend friend = new Friend();

		String status = jsonFriend.getString("status");
		if (status.equals("invited")) {
			friend.setInvited(true);
		}

		else {
			if (status.equals("invitee")) {
				friend.setInviter(true);
			}
		}
		// }

		friend.setName(jsonFriend.getString("name"));

		return friend;
	}


	
};
