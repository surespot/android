package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.ListIterator;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendFragment;
import com.twofours.surespot.ui.SurespotFragmentPagerAdapter;
import com.viewpagerindicator.IconProvider;

public class ChatPagerAdapter extends SurespotFragmentPagerAdapter implements IconProvider {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<Friend> mChatFriends;
	private static String mHomeName;

	public ChatPagerAdapter(Context context, FragmentManager fm) {
		super(fm);
		mHomeName = "";
	}

	@Override
	public Fragment getItem(int i) {
		SurespotLog.d(TAG, "getItem, I: " + i);
		if (i == 0) {

			FriendFragment ff = new FriendFragment();
			SurespotLog.d(TAG, "created new friend fragment: " + ff);

			// ff.setRetainInstance(true);

			return ff;
		}
		else {
			String name = mChatFriends.get(i - 1).getName();
			ChatFragment cf = ChatFragment.newInstance(name);
			SurespotLog.d(TAG, "created new chat fragment: " + cf);

			// cf.setRetainInstance(true);

			return cf;
		}

	}

	@Override
	public int getItemPosition(Object object) {
		SurespotLog.d(TAG, "getItemPosition, object: " + object.getClass().getName());
		if (object instanceof FriendFragment) {
			SurespotLog.d(TAG, "getItemPosition, returning 0");
			return 0;
		}

		ChatFragment chatFragment = (ChatFragment) object;

		String user = chatFragment.getUsername();
		int index = getFriendIndex(user);

		if (index == -1) {
			SurespotLog.d(TAG, "getItemPosition, returning POSITION_NONE for: " + user);
			return POSITION_NONE;
		}
		else {
			SurespotLog.d(TAG, "getItemPosition, returning " + (index + 1) + " for: " + user);
			return index + 1;
		}
	}

	private synchronized int getFriendIndex(String username) {
		ListIterator<Friend> iterator = mChatFriends.listIterator();

		while (iterator.hasNext()) {
			if (iterator.next().getName().equals(username)) {
				int index = iterator.previousIndex();
				SurespotLog.d(TAG, "friend index for %s: %d", username, index);
				return index;
			}
		}

		return -1;
	}

	@Override
	public int getCount() {
		if (mChatFriends == null) {
			return 0;
		}
		return mChatFriends.size() + 1;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		if (position == 0) {
			return mHomeName;
		}
		else {
			if (mChatFriends.size() > position - 1) {
				String title = mChatFriends.get(position - 1).getNameOrAlias();
				SurespotLog.v(TAG, "returning title %s for position %d", title, position);
				return title;
			}
		}
		return null;

	}

	public void addChatFriend(Friend friend) {
		if (!mChatFriends.contains(friend)) {
			mChatFriends.add(friend);
			sort();
			this.notifyDataSetChanged();
		}
	}

	public void setChatFriends(ArrayList<Friend> friends) {
		mChatFriends = friends;
		sort();
		this.notifyDataSetChanged();
	}

	public synchronized void sort() {

		mChatFriends = new ArrayList<Friend>(new Ordering<Friend>() {

			@Override
			public int compare(Friend arg0, Friend arg1) {
				return ComparisonChain.start().compare(arg0.getNameOrAlias().toLowerCase(), arg1.getNameOrAlias().toLowerCase(), Ordering.natural()).result();				
			}

		}.immutableSortedCopy(mChatFriends));
	}

	public boolean containsChat(String username) {
		return getFriendIndex(username) > -1;
	}

	public int getChatFragmentPosition(String username) {

		return getFriendIndex(username) + 1;

	}

	// public String getFragmentTag(String username) {
	// int pos = getChatFragmentPosition(username);
	// if (pos == -1)
	// return null;
	// return Utils.makePagerFragmentName(R.id.pager, getItemId(pos));
	// }
	//
	// public String getFragmentTag(int position) {
	// int pos = position;
	// if (pos == -1)
	// return null;
	// return Utils.makePagerFragmentName(R.id.pager, getItemId(position + 1));
	// }

	public String getChatName(int position) {
		if (position == 0) {
			return null;
		}
		else {
			if (position <= mChatFriends.size()) {
				return mChatFriends.get(position - 1).getName();
			}
			else {
				return null;
			}
		}
	}

	public void removeChat(int viewId, int index) {
		Friend friend = mChatFriends.remove(index - 1);

		String fragname = makeFragmentName(viewId, friend.getName().hashCode());
		Fragment fragment = mFragmentManager.findFragmentByTag(fragname);

		// SurespotLog.d(TAG, "Detaching item #" + getItemId(position-1) + ": f=" + object
		// + " v=" + ((Fragment)object).getView());
		if (fragment != null) {
			// blow the fragment away
			if (mCurTransaction == null) {
				mCurTransaction = mFragmentManager.beginTransaction();
			}

			mCurTransaction.remove(fragment);
			// mCurTransaction.commit();
		}

		notifyDataSetChanged();
	}

	public long getItemId(int position) {
		if (position == 0) {
			return mHomeName.hashCode();
		}
		else {
			return mChatFriends.get(position - 1).getName().hashCode();
		}
	}

	@Override
	public int getIcon(int position) {
		if (position == 0) {
			return R.drawable.ic_menu_home_blue;
		}
		else {
			return IconProvider.NO_ICON;
		}
	}
}
