package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.twofours.surespot.NewFragmentStatePagerAdapter;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendFragment;


//workaround for bug: https://code.google.com/p/android/issues/detail?can=2&start=0&num=100&q=&colspec=ID%20Type%20Status%20Owner%20Summary%20Stars&groupby=&sort=&id=37990
public class ChatPagerAdapter extends NewFragmentStatePagerAdapter {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<String> mChatNames;
	private ChatController mChatController;

	public ChatPagerAdapter(ChatController chatController, FragmentManager fm) {
		super(fm);
		mChatNames = new ArrayList<String>();
		mChatController = chatController;

	}

	@Override
	public Fragment getItem(int i) {
		SurespotLog.v(TAG, "getItem, I: " + i);
		if (i == 0) {
			FriendFragment ff = new FriendFragment();
			ff.setChatController(mChatController);
			return ff;
		}
		else {
			return ChatFragment.newInstance(mChatController, mChatNames.get(i - 1));
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
			SurespotLog.v(TAG, "getItemPosition, returning " + (index +1 ) + " for: " + user);
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

		return mChatNames.indexOf(username) + 1;

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
		return Utils.makePagerFragmentName(R.id.pager, getItemId(position + 1));
	}

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
