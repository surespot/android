package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ChatPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<String> mChatNames;

	public ChatPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>();

	}

	@Override
	public Fragment getItem(int i) {
		SurespotLog.v(TAG, "getItem, I: " + i);
		return ChatFragment.newInstance(mChatNames.get(i));

	}

	@Override
	public int getItemPosition(Object object) {
		ChatFragment chatFragment = (ChatFragment) object;
		// SurespotLog.v(TAG, "getItemPosition, object: " + object.getClass().getName());
		int index = mChatNames.indexOf(chatFragment.getUsername());
		if (index == -1) {
			return POSITION_NONE;
		}
		else {
			return index;
		}
	}

	@Override
	public int getCount() {
		return mChatNames.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {

		if (mChatNames.size() > position) {
			return mChatNames.get(position);
		}
		return null;

	}

	public void addChatName(String username) {
		if (!mChatNames.contains(username)) {
			mChatNames.add(username);
			this.notifyDataSetChanged();
		}
	}

	public void addChatNames(List<String> names) {
		for (String name : names) {
			mChatNames.add(name);
		}
		this.notifyDataSetChanged();

	}

	public boolean containsChat(String username) {
		return mChatNames.contains(username);
	}

	public int getChatFragmentPosition(String username) {

		return mChatNames.indexOf(username);

	}

	public String getFragmentTag(String username) {
		int pos = getChatFragmentPosition(username);
		if (pos == -1)
			return null;
		return Utils.makePagerFragmentName(R.id.pager, getItemId(pos));
	}

	public String getFragmentTag(int position) {
		int pos = position;
		if (pos == -1)
			return null;
		return Utils.makePagerFragmentName(R.id.pager, getItemId(position));
	}

	public ArrayList<String> getChatNames() {
		return mChatNames;
	}

	public String getChatName(int position) {
		return mChatNames.get(position);
	}

	public void removeChat(int index, boolean notify) {
		mChatNames.remove(index);
		if (notify) {
			notifyDataSetChanged();
		}
	}

	public long getItemId(int position) {
		return mChatNames.get(position).hashCode();
	}
}
