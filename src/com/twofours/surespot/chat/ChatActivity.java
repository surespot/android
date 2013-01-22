package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.HashMap;

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
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.FriendActivity;

public class ChatActivity extends SherlockFragmentActivity {
	public static final String TAG = "ChatActivity";

	private ChatPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private BroadcastReceiver mMessageBroadcastReceiver;
	private MultiProgressDialog mMpd;
	private HashMap<String, Integer> mLastViewedMessageIds;
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
					SurespotMessage[] resendMessages = mChatController.getResendMessages();
					for (SurespotMessage message : resendMessages) {
						// set the last received id so the server knows which messages to check
						String room = message.getRoom();

						// ideally get the last id from the fragment's chat adapter
						String lastMessageID = null;
						ChatFragment cf = getChatFragment(Utils.getOtherUser(message.getFrom(), message.getTo()));
						if (cf != null) {
							lastMessageID = cf.getLastMessageId();
						}

						// failing that use the last viewed id
						if (lastMessageID == null) {
							Object oId = mLastViewedMessageIds.get(room);
							if (oId != null) {
								lastMessageID = oId.toString();
							}
						}

						// last option, check last x messages for dupes
						if (lastMessageID == null) {
							lastMessageID = "-1";
						}
						Log.v(TAG, "setting resendId, room: " + room + ", id: " + lastMessageID);
						message.setResendId(lastMessageID);
						mChatController.sendMessage(message);

					}
				} else {
					Log.e(TAG, "Could not connect to chat server.");
				}

			}
		});
		setContentView(R.layout.activity_chat);

		String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
		Log.v(TAG, "Intent contained name: " + name);

		// if we don't have an intent, see if we have saved chat
		if (name == null) {
			name = Utils.getSharedPrefsString(SurespotConstants.PrefNames.LAST_CHAT);
		}

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

		if (!foundChat && name != null) {
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
				if (mLastViewedMessageIds != null) {
					Log.v(TAG, "onPageSelected name: " + name + ", pos: " + position);
					updateLastViewedMessageId(name);
					getChatFragment(name).requestFocus();
					
				}
			}

		});
		// mViewPager.setOffscreenPageLimit(0);

		if (name != null) {
			mViewPager.setCurrentItem(mPagerAdapter.getChatFragmentPosition(name));
		}

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

					// update last visited id for message
					updateLastViewedMessageId(otherUser, messageJson.getInt("id"));

				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		};

	}

	public void updateLastViewedMessageId(String name) {
		String sLastMessageId = getChatFragment(name).getLastMessageId();
		Log.v(TAG, "updating lastViewedMessageId for " + name + " to: " + sLastMessageId);
		if (sLastMessageId != null) {
			mLastViewedMessageIds.put(name, Integer.parseInt(sLastMessageId));
		}
	}

	public void updateLastViewedMessageId(String name, int lastMessageId) {
		Log.v(TAG, "Received lastMessageId: " + lastMessageId + " for " + name);
		if (name.equals(getCurrentChatName())) {
			Log.v(TAG, "The tab is visible so updating lastViewedMessageId for " + name + " to: " + lastMessageId);
			mLastViewedMessageIds.put(name, lastMessageId);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "onResume");

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED));

		// get last message id's out of shared prefs
		String lastMessageIdJson = Utils.getSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS);
		if (lastMessageIdJson != null) {
			try {
				mLastViewedMessageIds = Utils.jsonStringToMap(lastMessageIdJson);
				Log.v(TAG,"Loaded last viewed ids: "+ mLastViewedMessageIds);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} else {
			mLastViewedMessageIds = new HashMap<String, Integer>();
		}

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
		if (mLastViewedMessageIds.size() > 0) {
			String jsonString = Utils.mapToJsonString(mLastViewedMessageIds);
			Utils.putSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS, jsonString);
			Log.v(TAG,"saved last viewed ids: "+ mLastViewedMessageIds);

		} else {
			Utils.putSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS, null);
		}

		Utils.putSharedPrefsString(SurespotConstants.PrefNames.LAST_CHAT, getCurrentChatName());

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
		closeTab(mPagerAdapter.getChatFragmentPosition(username));
	}

	public void closeTab(int position) {
		// TODO remove saved messages

		if (mPagerAdapter.getCount() == 1) {
			mPagerAdapter.removeChat(0, false);
			showMain();
		} else {

			mPagerAdapter.removeChat(position, true);
			// when removing the 0 tab, onPageSelected is not fired for some reason so we need to set this stuff
			String name = mPagerAdapter.getChatName(mViewPager.getCurrentItem());
			updateLastViewedMessageId(name);
			getSupportActionBar().setTitle(name);

		}
	}

	private void showMain() {
		Intent parentActivityIntent = new Intent(this, FriendActivity.class);
		parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(parentActivityIntent);
		finish();

	}

	// @Override
	// protected void onStart() {
	// super.onStart();
	// Log.v(TAG, "onStart");
	//
	// }
	//
	// @Override
	// protected void onStop() {
	// super.onStop();
	// Log.v(TAG, "onStop");
	//
	// }
	//
	// @Override
	// protected void onDestroy() {
	// super.onDestroy();
	// Log.v(TAG, "onDestroy");
	//
	//
	//
	// }

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

	public String getCurrentChatName() {
		if (mPagerAdapter.getCount() > 0) {
			int pos = mViewPager.getCurrentItem();
			String name = mPagerAdapter.getChatName(pos);
			return name;
		} else {
			return null;
		}
	}

	public void sendMessage(SurespotMessage message) {
		mChatController.sendMessage(message);

	}

	public boolean chatConnected() {
		return mChatController.isConnected();
	}
}
