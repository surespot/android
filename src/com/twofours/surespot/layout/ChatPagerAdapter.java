package com.twofours.surespot.layout;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.twofours.surespot.fragments.ChatFragment;

public class ChatPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {
	private List<ChatFragment> mChatFragments;
	
	
	public ChatPagerAdapter(FragmentManager fm, String username) {
		super(fm);
		mChatFragments = new ArrayList<ChatFragment>();
		ChatFragment cf = new ChatFragment(username);	
		mChatFragments.add(cf);
	}

	@Override
	public Fragment getItem(int i) {
		return mChatFragments.get(i);
	}

	@Override
	public int getCount() {
		return mChatFragments.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return mChatFragments.get(position).getUsername();
		
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
			pos++;
			if (cf.getUsername().equals(username)) {
				return pos;
			}
		}
		return -1;
	}
	
}
