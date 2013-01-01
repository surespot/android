package com.twofours.surespot.activities;

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
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.fragments.ChatFragment;
import com.twofours.surespot.layout.ChatPagerAdapter;

public class ChatActivity extends SherlockFragmentActivity implements ActionBar.TabListener {
	public static final String TAG = "ChatActivity";

	ChatPagerAdapter mPagerAdapter;
	ViewPager mViewPager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.v(TAG, "onCreate");
		setContentView(R.layout.activity_main);

		Intent intent = getIntent();
		String username = intent.getExtras().getString("username");

		mPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager(), username);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayHomeAsUpEnabled(true);

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

		// register for notifications
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(message);
					ChatFragment cf = mPagerAdapter.getChatFragment(Utils.getOtherUser(messageJson.getString("from"),
							messageJson.getString("to")));

					// fragment might be null if user hasn't opened this chat
					// TODO indicate new message on FRIENDS screen?
					if (cf != null) {
						cf.addMessage(messageJson);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}, new IntentFilter(SurespotConstants.EventFilters.MESSAGE_RECEIVED_EVENT));

		// register for notifications
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				showChat(intent.getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME));

			}
		}, new IntentFilter(SurespotConstants.EventFilters.SHOW_CHAT_EVENT));

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

	private void showChat(String username) {
		if (mPagerAdapter.containsChat(username)) {
			int pos = mPagerAdapter.getChatFragmentPosition(username);
			if (pos > -1) {
				getSupportActionBar().setSelectedNavigationItem(pos);
			}
		} else {
			ChatFragment cf = new ChatFragment(username);
			mPagerAdapter.addFragment(cf);
		}
	}

	@Override
	public void onBackPressed() {
		goHome();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	        	goHome();
	        	return true;
	    }
	    return super.onOptionsItemSelected(item);
	}
	
	private void goHome() {

        // in the Action Bar.
        Intent parentActivityIntent = new Intent(this, MainActivity.class);
        parentActivityIntent.addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(parentActivityIntent);

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
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		Log.v(TAG, "onStop");
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.v(TAG, "onDestroy");
	}
}
