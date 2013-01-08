package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.twofours.surespot.R;
import com.twofours.surespot.Utils;

public class ChatPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<String> mChatNames;

	public ChatPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>();

	}

	@Override
	public Fragment getItem(int i) {
		Log.v(TAG, "getItem, I: " + i);
		return ChatFragment.newInstance(mChatNames.get(i));

	}

	@Override
	public int getItemPosition(Object object) {
		ChatFragment chatFragment = (ChatFragment) object;
	//	Log.v(TAG, "getItemPosition, object: " + object.getClass().getName());
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

		return mChatNames.get(position);

	}

	public void addChatName(String username) {
		mChatNames.add(username);
		this.notifyDataSetChanged();
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
		if (pos == -1) return null;
		return Utils.makePagerFragmentName(R.id.pager, pos);
	}

	public String getFragmentTag(int position) {
		int pos = position;
		if (pos == -1) return null;
		return Utils.makePagerFragmentName(R.id.pager, pos);
	}

	public ArrayList<String> getChatNames() {
		return mChatNames;
	}

	public void removeChat(int index, boolean notify) {
		mChatNames.remove(index);
		if (notify) {
			notifyDataSetChanged();
		}
	}
}
