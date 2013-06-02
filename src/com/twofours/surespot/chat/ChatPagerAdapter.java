package com.twofours.surespot.chat;

import java.util.ArrayList;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.google.common.collect.Ordering;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.friends.FriendFragment;
import com.twofours.surespot.ui.SurespotFragmentPagerAdapter;

public class ChatPagerAdapter extends SurespotFragmentPagerAdapter {

	private static final String TAG = "ChatPagerAdapter";
	private ArrayList<String> mChatNames;
	
	private static String mHomeName;

	public ChatPagerAdapter(Context context, FragmentManager fm) {
		super(fm);
		mHomeName =  context.getResources().getString(R.string.home);
	}

	@Override
	public Fragment getItem(int i) {
		SurespotLog.v(TAG, "getItem, I: " + i);
		if (i == 0) {

			FriendFragment ff = new FriendFragment();
			SurespotLog.v(TAG, "created new friend fragment: " + ff);

			// ff.setRetainInstance(true);

			return ff;
		}
		else {
			String name = mChatNames.get(i - 1);
			ChatFragment cf = ChatFragment.newInstance(name);
			SurespotLog.v(TAG, "created new chat fragment: " + cf);

			// cf.setRetainInstance(true);

			return cf;
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
			return mHomeName;
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

	public void setChatNames(ArrayList<String> names) {

		mChatNames = names;

		sort();
		this.notifyDataSetChanged();

	}

	private synchronized void sort() {
		
		mChatNames  = new ArrayList<String>(Ordering.from(String.CASE_INSENSITIVE_ORDER).immutableSortedCopy(mChatNames));
	}

	public boolean containsChat(String username) {
		return mChatNames.contains(username);
	}

	public int getChatFragmentPosition(String username) {

		return mChatNames.indexOf(username) + 1;

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

	public void removeChat(int viewId, int index) {		
		String name = mChatNames.remove(index - 1);
		
		String fragname = makeFragmentName(viewId, name.hashCode());
		Fragment fragment = mFragmentManager.findFragmentByTag(fragname);

		// SurespotLog.v(TAG, "Detaching item #" + getItemId(position-1) + ": f=" + object
		// + " v=" + ((Fragment)object).getView());
		if (fragment != null) {
			// blow the fragment away
			if (mCurTransaction == null) {
				mCurTransaction = mFragmentManager.beginTransaction();
			}

			mCurTransaction.remove(fragment);
			notifyDataSetChanged();
		}
	}

	public long getItemId(int position) {
		if (position == 0) {
			return mHomeName.hashCode();
		}
		else {
			return mChatNames.get(position - 1).hashCode();
		}
	}
}
