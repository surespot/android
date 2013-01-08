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
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.main.MainActivity;

public class ChatActivity extends SherlockFragmentActivity {
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
		actionBar.setDisplayHomeAsUpEnabled(true);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setTitle(mPagerAdapter.getChatNames().get(position));
			}
		});
		mViewPager.setOffscreenPageLimit(4);
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
				parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(parentActivityIntent);
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
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
		super.onStart();
		Log.v(TAG, "onStart");

	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.v(TAG, "onStop");

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "onDestroy");
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);

	}

}
