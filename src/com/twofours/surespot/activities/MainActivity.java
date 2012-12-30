package com.twofours.surespot.activities;

import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.fragments.FriendListFragment;
import com.twofours.surespot.fragments.NotificationListFragment;
import com.twofours.surespot.fragments.NotificationListFragment.OnInviteClickedListener;
import com.twofours.surespot.layout.MainPagerAdapter;

public class MainActivity extends SherlockFragmentActivity implements ActionBar.TabListener, OnInviteClickedListener {
	public static final String TAG = "MainActivity";

	MainPagerAdapter mPagerAdapter;
	ViewPager mViewPager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mPagerAdapter = new MainPagerAdapter(getSupportFragmentManager());

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by the adapter.
			// Also specify this Activity object, which implements the TabListener interface, as the
			// listener for when this tab is selected.
			actionBar.addTab(actionBar.newTab().setText(mPagerAdapter.getPageTitle(i)).setTabListener(this));
		}
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		mViewPager.setCurrentItem(tab.getPosition());

	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInviteClicked(String username, String action) {
		Log.v(TAG, "onInviteClicked, username: " + username + ", action: " + action);

		// add the user who's invite was accepted to the friend list to avoid a web request
		FriendListFragment friendListFragment = (FriendListFragment) getSupportFragmentManager().findFragmentById(
				R.id.friend_list_fragment);
		friendListFragment.inviteClicked(username, action);

	}

	

}
