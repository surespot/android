package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.friends.FriendFragment;

public class ChatPagerAdapter extends FragmentPagerAdapter {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<String> mChatNames;

	public ChatPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>();
	}

	@Override
	public Fragment getItem(int i) {
		SurespotLog.v(TAG, "getItem, I: " + i);
		if (i == 0) {
			FriendFragment ff = new FriendFragment();
			return ff;
		}
		else {
			return ChatFragment.newInstance(mChatNames.get(i - 1));
		}

	}

	@Override
	public int getItemPosition(Object object) {
		SurespotLog.v(TAG, "getItemPosition, object: " + object.getClass().getName());
		if (object instanceof FriendFragment) {
			SurespotLog.v(TAG, "getItemPosition, returning 0");
			return 0;
		}

		ChatFragment chatFragment = (ChatFragment) object;

		String user = chatFragment.getUsername();
		int index = mChatNames.indexOf(user);

		if (index == -1) {
			SurespotLog.v(TAG, "getItemPosition, returning POSITION_NONE for: " + user);
			return POSITION_NONE;
		}
		else {
			SurespotLog.v(TAG, "getItemPosition, returning " + (index + 1) + " for: " + user);
			return index + 1;
		}
	}

	@Override
	public int getCount() {
		if (mChatNames == null) {
			return 0;
		}
		return mChatNames.size() + 1;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		if (position == 0) {
			return "home";
		}
		else {
			if (mChatNames.size() > position - 1) {
				return mChatNames.get(position - 1);
			}
		}
		return null;

	}

	public void addChatName(String username) {
		if (!mChatNames.contains(username)) {
			mChatNames.add(username);
			sort();
			this.notifyDataSetChanged();
		}
	}

	public void addChatNames(Set<String> names) {
		for (String name : names) {
			mChatNames.add(name);
		}
		sort();
		this.notifyDataSetChanged();

	}

	private void sort() {
		Collections.sort(mChatNames);
	}

	public boolean containsChat(String username) {
		return mChatNames.contains(username);
	}

	public int getChatFragmentPosition(String username) {

		return mChatNames.indexOf(username) + 1;

	}

//	public String getFragmentTag(String username) {
//		int pos = getChatFragmentPosition(username);
//		if (pos == -1)
//			return null;
//		return Utils.makePagerFragmentName(R.id.pager, getItemId(pos));
//	}
//
//	public String getFragmentTag(int position) {
//		int pos = position;
//		if (pos == -1)
//			return null;
//		return Utils.makePagerFragmentName(R.id.pager, getItemId(position + 1));
//	}

	public ArrayList<String> getChatNames() {
		return mChatNames;
	}

	public String getChatName(int position) {
		if (position == 0) {
			return null;
		}
		else {
			return mChatNames.get(position - 1);
		}
	}

	public void removeChat(int index, boolean notify) {
		mChatNames.remove(index - 1);
		if (notify) {
			notifyDataSetChanged();
		}
	}

	public long getItemId(int position) {
		if (position == 0) {
			return "home".hashCode();
		}
		else {
			return mChatNames.get(position - 1).hashCode();
		}
	}
}
