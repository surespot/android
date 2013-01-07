package com.twofours.surespot.chat;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import com.twofours.surespot.main.MainActivity;

public class ChatActivity extends SherlockFragmentActivity implements ActionBar.TabListener {
	public static final String TAG = "ChatActivity";

	ChatPagerAdapter mPagerAdapter;
	ViewPager mViewPager;
	BroadcastReceiver mMessageBroadcastReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");
		setContentView(R.layout.activity_chat);
		// get intent
		Intent intent = getIntent();
		String name = intent.getExtras().getString(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);

		mPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager());

		// get the chats
		SharedPreferences prefs = getSharedPreferences(SurespotConstants.PREFS_FILE, 0);
		JSONArray jsonChats;
		boolean foundChat = false;
		try {
			String sChats = prefs.getString("chats", null);
			if (sChats != null) {
				jsonChats = new JSONArray(sChats);

				ArrayList<String> chatNames = new ArrayList<String>(jsonChats.length() + 1);

				for (int i = 0; i < jsonChats.length(); i++) {
					String chatName = jsonChats.getString(i);
					if (chatName.equals(name)) {
						foundChat = true;
					}
					chatNames.add(chatName);

				}
				if (!foundChat) {
					chatNames.add(name);
					foundChat = true;
				}

				mPagerAdapter.addChatNames(chatNames);
			}
		}
		catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (!foundChat) {
			mPagerAdapter.addChatName(name);
		}

		final ActionBar actionBar = getSupportActionBar();
		//actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayHomeAsUpEnabled(true);
		

		mViewPager = (ViewPager) findViewById(R.id.pager);		
		mViewPager.setAdapter(mPagerAdapter);
		/*mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});*/
		mViewPager.setOffscreenPageLimit(4);

		// For each of the sections in the app, add a tab to the action bar.
		/*for (int i = 0; i < mPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter.
			// Also specify this Activity object, which implements the
			// TabListener interface, as the
			// listener for when this tab is selected.
			actionBar.addTab(actionBar.newTab().setText(mPagerAdapter.getPageTitle(i)).setTabListener(this));
		}
*/
	/*	int pos = mPagerAdapter.getChatFragmentPosition(name);
		if (pos > -1) {
			actionBar.setSelectedNavigationItem(pos);
		}*/
		
		mViewPager.setCurrentItem(mPagerAdapter.getChatFragmentPosition(name));
		// register for notifications
		mMessageBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.v(TAG, "onReceiveMessage");
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(message);
					String tag = mPagerAdapter
							.getFragmentTag(Utils.getOtherUser(messageJson.getString("from"), messageJson.getString("to")));

					Log.v(TAG, "Fragment tag: " + tag);

					if (tag != null) {

						ChatFragment cf = (ChatFragment) getSupportFragmentManager().findFragmentByTag(tag);

						// fragment might be null if user hasn't opened this
						// chat
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

	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            // This is called when the Home (Up) button is pressed
	            // in the Action Bar.
	            Intent parentActivityIntent = new Intent(this, MainActivity.class);
	            parentActivityIntent.addFlags(
	                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
	                    Intent.FLAG_ACTIVITY_NEW_TASK);
	            startActivity(parentActivityIntent);
	            finish();
	            return true;
	    }
	    return super.onOptionsItemSelected(item);
	}

	private void showChat(String username) {

		if (mPagerAdapter.containsChat(username)) {
			int pos = mPagerAdapter.getChatFragmentPosition(username);
			if (pos > -1) {
				getSupportActionBar().setSelectedNavigationItem(pos);
			}
		}
		else {

			mPagerAdapter.addChatName(username);

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
	//	mViewPager.setCurrentItem(tab.getPosition());

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
		ChatController.disconnect();
		// save chat names
		SharedPreferences prefs = getSharedPreferences(SurespotConstants.PREFS_FILE, 0);
		SharedPreferences.Editor editor = prefs.edit();
		JSONArray jsonArray = new JSONArray(mPagerAdapter.getChatNames());
		editor.putString("chats", jsonArray.toString());
		editor.commit();

	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		Log.v(TAG, "onResume");
		ChatController.connect(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}

			}
		});
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
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);

	}

}
