package com.twofours.surespot.layout;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.twofours.surespot.fragments.FriendFragment;
import com.twofours.surespot.fragments.NotificationListFragment;
import com.twofours.surespot.fragments.SpotListFragment;

public class MainPagerAdapter extends android.support.v4.app.FragmentPagerAdapter {

	public MainPagerAdapter(FragmentManager fm) {
		super(fm);
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
			return new FriendFragment();
		}
	}

	@Override
	public int getCount() {
		return 2;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		switch (position) {
		case 0:
			return "friends";
		case 1:
			return "notifications";		
		default:
			return "your mama";
		}
	}
}
