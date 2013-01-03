package com.twofours.surespot.ui.activities;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.twofours.surespot.MainPagerAdapter;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.chat.ChatFragment;
import com.twofours.surespot.chat.IConnectCallback;

public class MainActivity extends SherlockFragmentActivity implements ActionBar.TabListener {
	public static final String TAG = "MainActivity";

	MainPagerAdapter mPagerAdapter;
	ViewPager mViewPager;
	BroadcastReceiver mMessageBroadcastReceiver;
	BroadcastReceiver mShowChatBroadcastReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");
		setContentView(R.layout.activity_main);

		mPagerAdapter = new MainPagerAdapter(getSupportFragmentManager());

		// restore tabs
		if (savedInstanceState != null) {
			ArrayList<String> chatNames = savedInstanceState.getStringArrayList("chats");
			if (chatNames != null) {
				mPagerAdapter.setChatNames(chatNames);
			}
		}

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
		mViewPager.setOffscreenPageLimit(10);

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by the adapter.
			// Also specify this Activity object, which implements the TabListener interface, as the
			// listener for when this tab is selected.
			actionBar.addTab(actionBar.newTab().setText(mPagerAdapter.getPageTitle(i)).setTabListener(this));
		}

		// register for notifications
		mMessageBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.v(TAG, "onReceiveMessage");
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(message);
					String tag = mPagerAdapter.getFragmentTag(Utils.getOtherUser(messageJson.getString("from"),
							messageJson.getString("to")));

					Log.v(TAG, "Fragment tag: " + tag);

					if (tag != null) {

						ChatFragment cf = (ChatFragment) getSupportFragmentManager().findFragmentByTag(tag);

						// fragment might be null if user hasn't opened this chat
						// TODO indicate new message on FRIENDS screen?
						if (cf != null) {
							cf.addMessage(messageJson);
						}
						else {
							Log.v(TAG, "Fragment null");
						}
					}
				}
				catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		};
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(SurespotConstants.EventFilters.MESSAGE_RECEIVED_EVENT));

		// register for notifications
		mShowChatBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				showChat(intent.getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME));

			}
		};
		LocalBroadcastManager.getInstance(this).registerReceiver(mShowChatBroadcastReceiver,
				new IntentFilter(SurespotConstants.EventFilters.SHOW_CHAT_EVENT));

	}

	private void showChat(String username) {

		if (mPagerAdapter.containsChat(username)) {
			int pos = mPagerAdapter.getChatFragmentPosition(username);
			if (pos > -1) {
				getSupportActionBar().setSelectedNavigationItem(pos);
			}
		}
		else {

			mPagerAdapter.addFragment(username);

			ActionBar actionBar = getSupportActionBar();
			Tab tab = actionBar.newTab();
			tab.setText(username);
			tab.setTabListener(this);
			actionBar.addTab(tab);

			// mViewPager.setCurrentItem(tab.getPosition());
			tab.select();
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
	protected void onPause() {

		super.onPause();
		Log.v(TAG, "onPause");

	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		Log.v(TAG, "onResume");
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		Log.v(TAG, "onStart");
		SurespotApplication.getChatController().connect(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}

			}
		});
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		Log.v(TAG, "onStop");
		SurespotApplication.getChatController().disconnect();

	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.v(TAG, "onDestroy");
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mShowChatBroadcastReceiver);

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.v(TAG, "onSaveInstanceState");
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);

		outState.putStringArrayList("chats", mPagerAdapter.getChatNames());
	}

}
