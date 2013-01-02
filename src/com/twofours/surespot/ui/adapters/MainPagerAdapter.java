package com.twofours.surespot.ui.adapters;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.twofours.surespot.ui.fragments.ChatFragment;
import com.twofours.surespot.ui.fragments.FriendFragment;
import com.twofours.surespot.ui.fragments.NotificationListFragment;

public class MainPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {
	private List<ChatFragment> mChatFragments;
	private static final int STATIC_TAB_COUNT = 2;
	public MainPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatFragments = new ArrayList<ChatFragment>();
	}

	@Override
	public Fragment getItem(int i) {
		Log.v("MainPagerAdapter","getItem: " + i);
		switch (i) {
		case 0:
			return new FriendFragment();

		case 1:
			return new NotificationListFragment();		

		default:
			return mChatFragments.get(i-STATIC_TAB_COUNT);
		}
		
	}
	

	@Override
	public int getCount() {
		return STATIC_TAB_COUNT + mChatFragments.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {
		switch (position) {
		case 0:
			return "friends";
		case 1:
			return "notifications";		
		default:
			return mChatFragments.get(position-STATIC_TAB_COUNT).getUsername();
		}
	}
	

	public void addFragment(ChatFragment fragment) {
		mChatFragments.add(fragment);
		this.notifyDataSetChanged();
	}
	
	public boolean containsChat(String username) {
		for (ChatFragment cf : mChatFragments) {
			if (cf.getUsername().equals(username)) {
				return true;
			}
		}
		return false;
	}
	
	public ChatFragment getChatFragment(String username) {
		for (ChatFragment cf : mChatFragments) {
			if (cf.getUsername().equals(username)) {
				return cf;
			}
		}
		return null;
	}
	
	public int getChatFragmentPosition(String username) {
		int pos = 0;
		for (ChatFragment cf : mChatFragments) {
			
			if (cf.getUsername().equals(username)) {
				return (pos + STATIC_TAB_COUNT);
			}
			pos++;
		}
		return -1;
	}
}
