package com.twofours.surespot.chat;

import java.util.ArrayList;
import java.util.HashMap;

import org.acra.ACRA;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.ImageSelectActivity;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.activities.SettingsActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.viewpagerindicator.TitlePageIndicator;

public class ChatActivity extends SherlockFragmentActivity {
	public static final String TAG = "ChatActivity";
	private static final int REQUEST_SELECT_IMAGE = 1;
	private static final int REQUEST_SETTINGS = 2;

	private ChatPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private BroadcastReceiver mMessageBroadcastReceiver;
	private HashMap<String, Integer> mLastViewedMessageIds;
	private ChatController mChatController;

	private TitlePageIndicator mIndicator;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurespotLog.v(TAG, "onCreate");

		mChatController = new ChatController(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (status) {

					// get the resend messages
					SurespotMessage[] resendMessages = mChatController.getResendMessages();
					for (SurespotMessage message : resendMessages) {
						// set the last received id so the server knows which messages to check
						String room = message.getSpot();

						// ideally get the last id from the fragment's chat adapter
						String lastMessageID = null;
						ChatFragment cf = getChatFragment(ChatUtils.getOtherUser(message.getFrom(), message.getTo()));
						if (cf != null) {
							lastMessageID = cf.getLatestMessageId();
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
						SurespotLog.v(TAG, "setting resendId, room: " + room + ", id: " + lastMessageID);
						message.setResendId(lastMessageID);
						mChatController.sendMessage(message);

					}
				}
				else {
					SurespotLog.w(TAG, "Could not connect to chat server.");
				}

			}
		});
		setContentView(R.layout.activity_chat);

		String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
		SurespotLog.v(TAG, "Intent contained name: " + name);

		// if we don't have an intent, see if we have saved chat
		if (name == null) {
			name = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT);
		}

		mPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager());

		// get the chats
		JSONArray jsonChats;
		boolean foundChat = false;
		try {
			String sChats = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS);
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
			SurespotLog.w(TAG, "onCreate", e1);
		}

		if (!foundChat && name != null) {
			mPagerAdapter.addChatName(name);
		}

		Utils.configureActionBar(this, "spots", IdentityController.getLoggedInUser(this), true);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mIndicator = (TitlePageIndicator) findViewById(R.id.indicator);
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				String name = mPagerAdapter.getChatName(position);
				// actionBar.setTitle(name);
				if (mLastViewedMessageIds != null) {
					SurespotLog.v(TAG, "onPageSelected name: " + name + ", pos: " + position);
					updateLastViewedMessageId(name);

					// getChatFragment(name).requestFocus();

				}
			}

		});
		mViewPager.setOffscreenPageLimit(2);

		if (name != null) {
			mViewPager.setCurrentItem(mPagerAdapter.getChatFragmentPosition(name));
		}

		// register for notifications
		mMessageBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				SurespotLog.v(TAG, "onReceiveMessage");
				String sMessage = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(sMessage);
					SurespotMessage message = SurespotMessage.toSurespotMessage(messageJson);
					sendMessageToFragment(message);

					// update last visited id for message
					String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
					updateLastViewedMessageId(otherUser, messageJson.getInt("id"));
					mPagerAdapter.addChatName(otherUser);
					mIndicator.notifyDataSetChanged();
				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onReceive", e);
				}

			}
		};

	}

	private void sendMessageToFragment(SurespotMessage message) {
		String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
		ChatFragment cf = getChatFragment(otherUser);
		// fragment might be null if user hasn't opened this chat
		if (cf != null) {
			cf.addMessage(message);
		}
		else {
			SurespotLog.v(TAG, "Fragment null");
		}

	}

	public void updateLastViewedMessageId(String name) {
		ChatFragment cf = getChatFragment(name);
		if (cf != null) {
			String sLastMessageId = cf.getLatestMessageId();
			SurespotLog.v(TAG, "updating lastViewedMessageId for " + name + " to: " + sLastMessageId);
			if (sLastMessageId != null) {
				mLastViewedMessageIds.put(name, Integer.parseInt(sLastMessageId));
			}
		}
	}

	public void updateLastViewedMessageId(String name, int lastMessageId) {
		SurespotLog.v(TAG, "Received lastMessageId: " + lastMessageId + " for " + name);
		if (name.equals(getCurrentChatName())) {
			SurespotLog.v(TAG, "The tab is visible so updating lastViewedMessageId for " + name + " to: " + lastMessageId);
			mLastViewedMessageIds.put(name, lastMessageId);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED));

		// get last message id's out of shared prefs
		String lastMessageIdJson = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS);
		if (lastMessageIdJson != null) {
			try {
				mLastViewedMessageIds = Utils.jsonStringToMap(lastMessageIdJson);
				SurespotLog.v(TAG, "Loaded last viewed ids: " + mLastViewedMessageIds);
			}
			catch (Exception e1) {
				// TODO Auto-generated catch block
				ACRA.getErrorReporter().handleException(e1);
				e1.printStackTrace();
			}
		}
		else {
			mLastViewedMessageIds = new HashMap<String, Integer>();
		}

		mChatController.loadUnsentMessages();
		mChatController.connect();

	}

	@Override
	protected void onPause() {

		super.onPause();
		SurespotLog.v(TAG, "onPause");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);

		mChatController.disconnect();
		// save chat names
		JSONArray jsonArray = new JSONArray(mPagerAdapter.getChatNames());
		Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS, jsonArray.toString());
		mChatController.saveUnsentMessages();
		// store chats the user went into
		if (mLastViewedMessageIds.size() > 0) {
			String jsonString = ChatUtils.mapToJsonString(mLastViewedMessageIds);
			Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS, jsonString);
			SurespotLog.v(TAG, "saved last viewed ids: " + mLastViewedMessageIds);

		}
		else {
			Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS, null);
		}

		Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT, getCurrentChatName());

		mChatController.destroy();
	}

	private ChatFragment getChatFragment(String roomName) {
		String tag = mPagerAdapter.getFragmentTag(roomName);

		SurespotLog.v(TAG, "Fragment tag: " + tag);

		if (tag != null) {

			ChatFragment cf = (ChatFragment) getSupportFragmentManager().findFragmentByTag(tag);
			return cf;
		}
		return null;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		switch (item.getItemId()) {
		case android.R.id.home:
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			showMain();
			return true;
		case R.id.menu_close_bar:
			// case R.id.menu_close_menu:
			closeTab(mViewPager.getCurrentItem());
			return true;

		case R.id.menu_send_image_bar:
			// case R.id.menu_send_image_menu:
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("source", ImageSelectActivity.REQUEST_EXISTING_IMAGE);
			intent.putExtra("to", getCurrentChatName());
			startActivityForResult(intent, REQUEST_SELECT_IMAGE);
			return true;
		case R.id.menu_capture_image_bar:
			// case R.id.menu_capture_image_menu:
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("source", ImageSelectActivity.REQUEST_CAPTURE_IMAGE);
			intent.putExtra("to", getCurrentChatName());
			startActivityForResult(intent, REQUEST_SELECT_IMAGE);

			return true;
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, REQUEST_SETTINGS);
			return true;
		case R.id.menu_logout:
			IdentityController.logout(this, new IAsyncCallback<Boolean>() {
				@Override
				public void handleResponse(Boolean result) {
					Intent intent = new Intent(ChatActivity.this, LoginActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					ChatActivity.this.startActivity(intent);
					Utils.putSharedPrefsString(ChatActivity.this, SurespotConstants.PrefNames.LAST_CHAT, null);
					finish();
				}
			});

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Uri selectedImageUri = null;
		if (resultCode == RESULT_OK) {
			switch (requestCode) {

			case REQUEST_SELECT_IMAGE:
				selectedImageUri = data.getData();
				String to = data.getStringExtra("to");
				if (selectedImageUri != null) {
					ChatUtils.uploadPictureMessageAsync(this, selectedImageUri, to, false, new IAsyncCallback<Boolean>() {
						@Override
						public void handleResponse(Boolean result) {
							if (!result) {
								Utils.makeToast(ChatActivity.this, "Could not upload picture, please try again later.");
							}
						}
					});
					break;
				}
			}
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
		}
		else {

			mPagerAdapter.removeChat(position, true);
			// when removing the 0 tab, onPageSelected is not fired for some reason so we need to set this stuff
			String name = mPagerAdapter.getChatName(mViewPager.getCurrentItem());
			updateLastViewedMessageId(name);
			mIndicator.notifyDataSetChanged();
		}
	}

	private void showMain() {
		Intent parentActivityIntent = new Intent(this, FriendActivity.class);
		parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(parentActivityIntent);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_chat, menu);
		return true;
	}

	public String getCurrentChatName() {
		if (mPagerAdapter.getCount() > 0) {
			int pos = mViewPager.getCurrentItem();
			String name = mPagerAdapter.getChatName(pos);
			return name;
		}
		else {
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
