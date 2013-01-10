package com.twofours.surespot.friends;

import com.twofours.surespot.IListItem;
import com.twofours.surespot.main.MainAdapter;

public class FriendInvite  implements IListItem{
	private String mName;


	public String getName() {
		return mName;
	}

	public void setName(String mName) {
		this.mName = mName;
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public int getType() {
		return MainAdapter.TYPE_INVITE;
	}
}
