package com.twofours.surespot.main;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.twofours.surespot.R;
import com.twofours.surespot.Utils;
import com.twofours.surespot.R.id;
import com.twofours.surespot.chat.ChatFragment;
import com.twofours.surespot.friends.FriendFragment;
import com.twofours.surespot.notifications.NotificationListFragment;

public class MainPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {
	
	private ArrayList<String> mChatNames;
	
	

	private static final int STATIC_TAB_COUNT = 2;
	public MainPagerAdapter(FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>(); 
				
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
		
			return ChatFragment.newInstance(mChatNames.get(i-STATIC_TAB_COUNT));
		}
		
	}
	

	@Override
	public int getCount() {
		return STATIC_TAB_COUNT + mChatNames.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {
		switch (position) {
		case 0:
			return "friends";
		case 1:
			return "notifications";		
		default:
			return mChatNames.get(position-STATIC_TAB_COUNT);
		}
	}
	

	public void addFragment(String username) {
		mChatNames.add(username);
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
		return Utils.makePagerFragmentName(R.id.pager, pos+STATIC_TAB_COUNT);
	}
	
	public ArrayList<String> getChatNames() {
		return mChatNames;
	}

	public void setChatNames(ArrayList<String> chatNames) {
		this.mChatNames = chatNames;
	}
	
}

