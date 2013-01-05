package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.twofours.surespot.R;
import com.twofours.surespot.Utils;

public class ChatPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {

	private ArrayList<String> mChatNames;

	public ChatPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>();

	}

	@Override
	public Fragment getItem(int i) {
		return ChatFragment.newInstance(mChatNames.get(i));
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

	public ArrayList<String> getChatNames() {
		return mChatNames;
	}

	

}
