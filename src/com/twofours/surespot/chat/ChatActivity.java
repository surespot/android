package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.friends.FriendActivity;

public class ChatActivity extends SherlockFragmentActivity {
	public static final String TAG = "ChatActivity";

	private ChatPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private BroadcastReceiver mMessageBroadcastReceiver;
	private MultiProgressDialog mMpd;
	private HashMap<String, Integer> mVisitedPageMessageIds;
	private ChatController mChatController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");
		mChatController = new ChatController(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (status) {

					// get the resend messages
					ChatMessage[] resendMessages = mChatController.getResendMessages();
					for (ChatMessage message : resendMessages) {
						// set the last seen id
						String room = message.getRoom();

						String lastMessageID = null;
						ChatFragment cf = getChatFragment(Utils.getOtherUser(message.getFrom(), message.getTo()));
						if (cf != null) {
							lastMessageID = cf.getLastMessageId();
						}
						if (lastMessageID == null) {
							Object oId = mVisitedPageMessageIds.get(room);
							if (oId != null) {
								lastMessageID = oId.toString();
							}
						}
						if (lastMessageID == null) {
							lastMessageID = "-1";
						}
						Log.v(TAG, "setting resendId, room: " + room + ", id: " + lastMessageID);
						message.setResendId(lastMessageID);

						// else {
						// message.setResendId(null);
						// }
						mChatController.sendMessage(message);
					}
				} else {
					Log.e(TAG, "Could not connect to chat server.");
				}

			}
		});
		setContentView(R.layout.activity_chat);
		// get intent

		Intent intent = getIntent();
		String name = intent.getExtras().getString(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);

		mPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager());
		mMpd = new MultiProgressDialog(this, "loading and decrypting messages", 750);

		// get the chats
		JSONArray jsonChats;
		boolean foundChat = false;
		try {
			String sChats = Utils.getSharedPrefsString(SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS);
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
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (!foundChat) {
			mPagerAdapter.addChatName(name);
		}

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle(name);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				String name = mPagerAdapter.getChatName(position);
				actionBar.setTitle(name);
				if (mVisitedPageMessageIds != null) {
					Log.v(TAG, "onPageSelected updating visited page, name: " + name + ", pos: " + position);
					mVisitedPageMessageIds.put(name, -1);
				}
			}

		});
		// mViewPager.setOffscreenPageLimit(4);
		mViewPager.setCurrentItem(mPagerAdapter.getChatFragmentPosition(name));
		// register for notifications
		mMessageBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.v(TAG, "onReceiveMessage");
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(message);
					String otherUser = Utils.getOtherUser(messageJson.getString("from"), messageJson.getString("to"));

					ChatFragment cf = getChatFragment(otherUser);
					// fragment might be null if user hasn't opened this
					// chat
					if (cf != null) {

						cf.addMessage(messageJson);

					} else {
						Log.v(TAG, "Fragment null");
					}

					// update last visited id for current tab
					String name = getCurrentChatName();
					if (name.equals(otherUser)) {
						mVisitedPageMessageIds.put(name, messageJson.getInt("id"));
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		};

	}
	

	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "onResume");

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED));

		// get last message id's out of shared prefs
		String lastMessageIdJson = Utils.getSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_MESSAGE_IDS);
		if (lastMessageIdJson != null) {
			try {
				mVisitedPageMessageIds = Utils.jsonStringToMap(lastMessageIdJson);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		// if we opened the tab and didn't get any new messages we know by the -1
		mVisitedPageMessageIds.put(getCurrentChatName(), -1);

		mChatController.connect();
	}
	
	@Override
	protected void onPause() {

		super.onPause();
		Log.v(TAG, "onPause");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);

		mChatController.disconnect();
		// save chat names
		JSONArray jsonArray = new JSONArray(mPagerAdapter.getChatNames());
		Utils.putSharedPrefsString(SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS, jsonArray.toString());
		mChatController.saveUnsentMessages();
		// store chats the user went into
		if (mVisitedPageMessageIds.size() > 0) {
			String jsonString = Utils.mapToJsonString(mVisitedPageMessageIds);
			Utils.putSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_MESSAGE_IDS, jsonString);

		} else {
			Utils.putSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_MESSAGE_IDS, null);
		}

		mChatController.destroy();
	}



	private ChatFragment getChatFragment(String roomName) {
		String tag = mPagerAdapter.getFragmentTag(roomName);

		Log.v(TAG, "Fragment tag: " + tag);

		if (tag != null) {

			ChatFragment cf = (ChatFragment) getSupportFragmentManager().findFragmentByTag(tag);
			return cf;
		}
		return null;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			showMain();
			return true;
		case R.id.menu_close:

			closeTab(mViewPager.getCurrentItem());

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	public void closeChat(String username) {
		if (mPagerAdapter.getCount() == 1) {
			mPagerAdapter.removeChat(0, false);
			showMain();
		} else {
			mPagerAdapter.removeChat(mPagerAdapter.getChatFragmentPosition(username), true);
			// set the title bar
			getSupportActionBar().setTitle(mPagerAdapter.getChatNames().get(mViewPager.getCurrentItem()));
		}
	}

	public void closeTab(int position) {
		// TODO remove saved messages

		if (mPagerAdapter.getCount() == 1) {
			mPagerAdapter.removeChat(0, false);
			showMain();
		} else {

			mPagerAdapter.removeChat(mViewPager.getCurrentItem(), true);
			// set the title bar
			getSupportActionBar().setTitle(mPagerAdapter.getChatNames().get(mViewPager.getCurrentItem()));
		}
	}

	private void showMain() {
		Intent parentActivityIntent = new Intent(this, FriendActivity.class);
		parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(parentActivityIntent);
		finish();

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

		

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_chat, menu);
		return true;
	}

	public void startLoadingMessagesProgress() {
		mMpd.incrProgress();
	}

	public void stopLoadingMessagesProgress() {
		mMpd.decrProgress();
	}

	private String getCurrentChatName() {
		int pos = mViewPager.getCurrentItem();
		String name = mPagerAdapter.getChatName(pos);
		return name;
	}

	public void sendMessage(String to, String message) {
		mChatController.sendMessage(to, message);

	}

	public boolean chatConnected() {
		return mChatController.isConnected();
	}
}
