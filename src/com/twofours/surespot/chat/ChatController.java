package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.activities.LoginActivity;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.viewpagerindicator.TitlePageIndicator;

public class ChatController {

	private static final String TAG = "ChatController";
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;
	private static final int STATE_DISCONNECTED = 2;

	private static final int MAX_RETRIES = 5;
	private SocketIO socket;
	private int mRetries = 0;
	private Timer mBackgroundTimer;
	// private TimerTask mResendTask;

	private IOCallback mSocketCallback;

	private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
	private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();

	private int mConnectionState;
	private boolean mOnWifi;
	private NotificationManager mNotificationManager;
	private BroadcastReceiver mConnectivityReceiver;
	private HashMap<String, ChatAdapter> mChatAdapters;
	private HashMap<String, Integer> mLastReceivedMessageIds;
	private HashMap<String, Boolean> mMessageActivity;
	private HashMap<String, Integer> mEarliestMessage;
	private Set<String> mActiveChats;

	private FriendAdapter mFriendAdapter;
	private ChatPagerAdapter mChatPagerAdapter;
	private ViewPager mViewPager;
	private TitlePageIndicator mIndicator;
	private FragmentManager mFragmentManager;
	private ArrayList<MenuItem> mMenuItems;

	private static String mCurrentChat;
	private static boolean mPaused = true;

	private Context mContext;
	public static final int MODE_NORMAL = 0;
	public static final int MODE_SELECT = 1;

	private int mMode = MODE_NORMAL;

	public ChatController(Context context, FragmentManager fm) {
		SurespotLog.v(TAG, "constructor: " + this);

		mEarliestMessage = new HashMap<String, Integer>();
		mChatAdapters = new HashMap<String, ChatAdapter>();
		loadState();

		mContext = context;

		mFragmentManager = fm;
		mFriendAdapter = new FriendAdapter(mContext);
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		setOnWifi();

		// mViewPager.setOffscreenPageLimit(2);

		mSocketCallback = new IOCallback() {

			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					SurespotLog.v(TAG, "JSON Server said:" + json.toString(2));

				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onMessage", e);
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				SurespotLog.v(TAG, "Server said: " + data);
			}

			@Override
			public synchronized void onError(SocketIOException socketIOException) {
				// socket.io returns 403 for can't login
				if (socketIOException.getHttpStatus() == 403) {
					if (!MainActivity.getNetworkController().isUnauthorized()) {
						MainActivity.getNetworkController().setUnauthorized(true);

						SurespotLog.v(TAG, "Got 403 from socket.io, launching login intent.");
						Intent intent = new Intent(mContext, LoginActivity.class);
						intent.putExtra("401", true);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
						mContext.startActivity(intent);
					}
					return;
				}

				SurespotLog.w(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: " + mRetries,
						socketIOException);

				// if (mResendTask != null) {
				// mResendTask.cancel();
				// }

				setOnWifi();
				// kick off another task
				if (mRetries < MAX_RETRIES) {

					if (mReconnectTask != null) {
						mReconnectTask.cancel();
					}

					int timerInterval = (int) (Math.pow(2, mRetries++) * 1000);
					SurespotLog.v(TAG, "Starting another task in: " + timerInterval);

					mReconnectTask = new ReconnectTask();
					if (mBackgroundTimer == null) {
						mBackgroundTimer = new Timer("backgroundTimer");
					}
					mBackgroundTimer.schedule(mReconnectTask, timerInterval);
				}
				else {
					// TODO tell user
					SurespotLog.w(TAG, "Socket.io reconnect retries exhausted, giving up.");
					// TODO more persistent error

					// Utils.makeToast(this,mContext,
					// "Can not connect to chat server. Please check your network and try again.",
					// Toast.LENGTH_LONG).show(); // TODO tie in with network controller 401 handling
					Intent intent = new Intent(mContext, MainActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
					mContext.startActivity(intent);

				}
			}

			@Override
			public void onDisconnect() {
				SurespotLog.v(TAG, "Connection terminated.");
				// socket = null;
			}

			@Override
			public void onConnect() {
				SurespotLog.v(TAG, "socket.io connection established");
				setState(STATE_CONNECTED);
				setOnWifi();
				mRetries = 0;

				if (mBackgroundTimer != null) {
					mBackgroundTimer.cancel();
					mBackgroundTimer = null;
				}

				if (mReconnectTask != null && mReconnectTask.cancel()) {
					SurespotLog.v(TAG, "Cancelled reconnect timer.");
					mReconnectTask = null;
				}

				connected();

				// sendMessages();

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				SurespotLog.v(TAG, "Server triggered event '" + event + "'");

				if (event.equals("notification")) {
					JSONObject json = (JSONObject) args[0];
					try {
						mFriendAdapter.addFriendInviter(json.getString("data"));
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "onReceive (inviteRequest)", e);
					}
					return;
				}
				if (event.equals("inviteResponse")) {

					JSONObject jsonInviteResponse;
					try {
						jsonInviteResponse = new JSONObject((String) args[0]);
						String name = jsonInviteResponse.getString("user");
						if (jsonInviteResponse.getString("response").equals("accept")) {
							mFriendAdapter.addNewFriend(name);
						}
						else {
							mFriendAdapter.removeFriend(name);
						}
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "onReceive (inviteResponse)", e);
					}

					return;
				}
				if (event.equals("message")) {

					// TODO check who from
					try {
						SurespotMessage message = SurespotMessage.toSurespotMessage(new JSONObject((String) args[0]));

						String otherUser = message.getOtherUser();
						// check the type
						if (message.getType().equals("system")) {
							// revoke the cert
							if (message.getSubType().equals("revoke")) {
								String username = message.getFrom();
								String version = message.getFromVersion();

								// this will force download of key at some point
								IdentityController.updateLatestVersion(mContext, username, version);
							}

							else {
								if (message.getSubType().equals("delete")) {
									SurespotMessage dMessage = getChatAdapter(mContext, otherUser).deleteMessageById(
											Integer.parseInt(message.getIv()));

									// if it's an image blow the http cache entry away
									if (dMessage != null && dMessage.getMimeType() != null
											&& dMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
										MainActivity.getNetworkController().purgeCacheUrl(dMessage.getCipherData());
									}
									mLastReceivedMessageIds.put(otherUser, message.getId());
									updateLastViewedMessageId(otherUser, otherUser.equals(message.getFrom()));
									checkAndSendNextMessage(message);
									// saveState(otherUser);
								}
							}
						}
						else {
							boolean added = getChatAdapter(mContext, otherUser).addOrUpdateMessage(message, true, true);

							mChatPagerAdapter.addChatName(otherUser);
							mLastReceivedMessageIds.put(otherUser, message.getId());
							updateLastViewedMessageId(otherUser, otherUser.equals(message.getFrom()) && added);
							checkAndSendNextMessage(message);
						}
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "on", e);
					}

				}
			}
		};

		mConnectivityReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				SurespotLog.v(TAG, "Connectivity Action");
				ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
				if (networkInfo != null) {
					SurespotLog.v(TAG, "isconnected: " + networkInfo.isConnected());
					SurespotLog.v(TAG, "failover: " + networkInfo.isFailover());
					SurespotLog.v(TAG, "reason: " + networkInfo.getReason());
					SurespotLog.v(TAG, "type: " + networkInfo.getTypeName());

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						synchronized (ChatController.this) {
							// if we're not connecting, connect
							if (getState() != STATE_CONNECTING && !mOnWifi) {

								SurespotLog.v(TAG, "Network switch, Reconnecting...");

								setState(STATE_CONNECTING);

								mOnWifi = true;
								disconnect();
								connect();
							}
						}
					}
				}
				else {
					SurespotLog.v(TAG, "networkinfo null");
				}
			}
		};

	}

	// this has to be done outside of the contructor as it creates fragments, which need chat controller instance
	public void init(ViewPager viewPager, TitlePageIndicator pageIndicator, ArrayList<MenuItem> menuItems) {
		mChatPagerAdapter = new ChatPagerAdapter(mFragmentManager);
		mMenuItems = menuItems;

		mViewPager = viewPager;
		mViewPager.setAdapter(mChatPagerAdapter);
		mIndicator = pageIndicator;
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				SurespotLog.v(TAG, "onPageSelected, position: " + position);
				String name = mChatPagerAdapter.getChatName(position);
				setCurrentChat(name);

			}
		});
		mChatPagerAdapter.addChatNames(mActiveChats);
		onResume();
	}

	private void connect() {
		SurespotLog.v(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: "
				+ mConnectionState);

		// if (socket != null && socket.isConnected()) {
		// return;
		// }

		Cookie cookie = IdentityController.getCookie();

		if (cookie == null) {
			// need to login
			// SurespotLog.v(TAG, "No session cookie, starting Login activity.");
			// Intent startupIntent = new Intent(mContext, StartupActivity.class);
			// startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			// mContext.startActivity(startupIntent);
			return;
		}

		try {
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
			socket = new SocketIO(SurespotConfiguration.getBaseUrl(), headers);
			socket.connect(mSocketCallback);
		}
		catch (Exception e) {

			SurespotLog.w(TAG, "connect", e);
		}

	}

	private void disconnect() {
		SurespotLog.v(TAG, "disconnect.");
		setState(STATE_DISCONNECTED);

		if (socket != null) {
			socket.disconnect();
			socket = null;
		}

	}

	private void connected() {
		loadFriends();

	}

	private void resendMessages() {
		// get the resend messages
		SurespotMessage[] resendMessages = getResendMessages();
		for (SurespotMessage message : resendMessages) {
			// set the last received id so the server knows which messages to check
			String otherUser = message.getOtherUser();

			// String username = message.getFrom();

			// ideally get the last id from the fragment's chat adapter
			Integer lastMessageID = mLastReceivedMessageIds.get(otherUser);

			// failing that use the last viewed id
			// if (lastMessageID == null) {
			// lastMessageID = mLastViewedMessageIds.get(otherUser);
			// }

			// last option, check last x messages for dupes
			if (lastMessageID == null) {
				lastMessageID = -1;
			}
			SurespotLog.v(TAG, "setting resendId, otheruser: " + otherUser + ", id: " + lastMessageID);
			message.setResendId(lastMessageID);
			sendMessage(message);

		}
	}

	private void setOnWifi() {
		// get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
		ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo != null) {
			mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
		}

	}

	private void checkAndSendNextMessage(SurespotMessage message) {
		SurespotLog.v(TAG, "received message: " + message);
		sendMessages();

		if (mResendBuffer.size() > 0) {
			if (mResendBuffer.remove(message)) {
				SurespotLog.v(TAG, "Received and removed message from resend  buffer: " + message);
			}
		}
	}

	private SurespotMessage[] getResendMessages() {
		SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
		mResendBuffer.clear();
		return messages;

	}

	private void sendMessages() {
		if (mBackgroundTimer == null) {
			mBackgroundTimer = new Timer("backgroundTimer");
		}

		// if (mResendTask != null) {
		// mResendTask.cancel();
		// }

		SurespotLog.v(TAG, "Sending: " + mSendBuffer.size() + " messages.");

		Iterator<SurespotMessage> iterator = mSendBuffer.iterator();
		while (iterator.hasNext()) {
			SurespotMessage message = iterator.next();
			iterator.remove();
			sendMessage(message);
		}

	}

	private void sendMessage(final SurespotMessage message) {
		mResendBuffer.add(message);
		if (getState() == STATE_CONNECTED) {
			// TODO handle different mime types
			SurespotLog.v(TAG, "sendmessage, socket: " + socket);
			JSONObject json = message.toJSONObject();
			SurespotLog.v(TAG, "sendmessage, json: " + json);
			String s = json.toString();
			SurespotLog.v(TAG, "sendmessage, message string: " + s);

			socket.send(s);
		}
	}

	private int getState() {
		return mConnectionState;
	}

	private synchronized void setState(int state) {
		mConnectionState = state;
	}

	private ReconnectTask mReconnectTask;

	private class ReconnectTask extends TimerTask {

		@Override
		public void run() {
			SurespotLog.v(TAG, "Reconnect task run.");
			connect();
		}
	}

	// private class ResendTask extends TimerTask {
	//
	// @Override
	// public void run() {
	// // resend message
	// sendMessages();
	// }
	// }
	//
	// private boolean isConnected() {
	// return (getState() == STATE_CONNECTED);
	// }

	private void updateLastViewedMessageId(String username, boolean activity) {

		if (!username.equals(mCurrentChat)) {
			mFriendAdapter.setMessageActivity(username, activity);
			mMessageActivity.put(username, activity);

		}
		else {
			mFriendAdapter.setMessageActivity(username, false);
			mMessageActivity.put(username, false);
		}
	}

	private boolean getActivity(String username) {
		Boolean activity = mMessageActivity.get(username);
		return activity == null ? false : activity;
	}

	// message handling shiznit

	void loadEarlierMessages(final String username) {

		// mLoading = true;
		// get the list of messages

		Integer firstMessageId = mEarliestMessage.get(username);
		if (firstMessageId == null) {
			firstMessageId = getEarliestMessageId(username);
			mEarliestMessage.put(username, firstMessageId);
		}
		// else {
		// firstMessageId -= 60;
		// if (firstMessageId < 1) {
		// firstMessageId = 1;
		// }
		// }

		if (firstMessageId != null) {
			if (firstMessageId > 1) {

				SurespotLog.v(TAG, username + ": asking server for messages before messageId: " + firstMessageId);
				// final int fMessageId = firstMessageId;
				final ChatAdapter chatAdapter = getChatAdapter(mContext, username);
				MainActivity.getNetworkController().getEarlierMessages(username, firstMessageId, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here

						// if (getActivity() != null) {
						SurespotMessage message = null;

						try {
							for (int i = jsonArray.length() - 1; i >= 0; i--) {
								JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
								message = SurespotMessage.toSurespotMessage(jsonMessage);

								// if it's a system message ignore it
								if (message.getType().equals("message")) {
									chatAdapter.insertMessage(message, false);
								}

							}
						}
						catch (JSONException e) {
							SurespotLog.e(TAG, username + ": error creating chat message: " + e.toString(), e);
						}

						SurespotLog.v(TAG, username + ": loaded: " + jsonArray.length() + " earlier messages from the server.");

						if (message != null) {
							mEarliestMessage.put(username, message.getId());
							chatAdapter.notifyDataSetChanged();
						}
						chatAdapter.setLoading(false);
					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.w(TAG, username + ": getEarlierMessages", error);
						chatAdapter.setLoading(false);
					}
				});
			}
			else {
				SurespotLog.v(TAG, username + ": getEarlierMessages: no more messages.");
				// ChatFragment.this.mNoEarlierMessages = true;
			}
		}
	}

	private void loadAllLatestMessages() {
		SurespotLog.v(TAG, "loadAllLatestMessages ");
		setMessagesLoading(true);
		// gather up all the latest message IDs

		// JSONObject messageIdHolder = new JSONObject();
		JSONObject messageIds = new JSONObject();
		for (Entry<String, Integer> eLastReceivedId : mLastReceivedMessageIds.entrySet()) {
			try {
				messageIds.put(ChatUtils.getSpot(IdentityController.getLoggedInUser(), eLastReceivedId.getKey()),
						eLastReceivedId.getValue());
			}
			catch (JSONException e) {
				SurespotLog.w(TAG, "loadAllLatestMessages", e);
			}
		}

		MainActivity.getNetworkController().getLatestMessages(messageIds, new JsonHttpResponseHandler() {

			@Override
			public void onSuccess(int statusCode, final JSONArray jsonArray) {
				SurespotLog.v(TAG, "loadAllLatestMessages success (jsonArray), statusCode: " + statusCode);

				// new AsyncTask<Void, Void, Void>() {
				// @Override
				// protected Void doInBackground(Void... params) {
				// TODO Auto-generated method stub

				// Utils.makeToast(mContext, "received latest messages: " + response.toString());
				for (int i = 0; i < jsonArray.length(); i++) {
					try {
						JSONObject jsonObject = jsonArray.getJSONObject(i);
						String spot = jsonObject.getString("spot");
						String otherUser = ChatUtils.getOtherSpotUser(spot, IdentityController.getLoggedInUser());
						JSONArray messages = jsonObject.getJSONArray("messages");
						handleMessages(otherUser, messages);
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "loadLatestAllMessages", e);
					}
				}

				// send resend
				resendMessages();
				// return null;
				// if (jsonArray.length() > 0) {
				// saveMessages();
				// }
				// }

				// protected void onPostExecute(Void result) {
				setMessagesLoading(false);
				// };
				// }.execute();
			}

			@Override
			public void onFailure(Throwable error, String content) {
				setMessagesLoading(false);
				Utils.makeToast(mContext, "loading latest messages failed: " + content);
			}
		});

	}

	private void handleMessages(String username, JSONArray jsonArray) {
		SurespotLog.v(TAG, username + ": handleMessages");
		final ChatAdapter chatAdapter = getChatAdapter(mContext, username);

		SurespotMessage message = null;
		boolean messageActivity = false;
		for (int i = 0; i < jsonArray.length(); i++) {
			try {
				JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
				message = SurespotMessage.toSurespotMessage(jsonMessage);

				// if it's a system message from another user then check version
				if (message.getType().equals("system")) {
					if (message.getSubType().equals("revoke")) {
						IdentityController.updateLatestVersion(mContext, message.getFrom(), message.getFromVersion());
					}
					else {
						if (message.getSubType().equals("delete")) {
							SurespotMessage dMessage = chatAdapter.deleteMessageById(Integer.parseInt(message.getIv()));

							// if it's an image blow the http cache entry away
							if (dMessage != null && dMessage.getMimeType() != null
									&& dMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
								MainActivity.getNetworkController().purgeCacheUrl(message.getCipherData());

							}

							if (!messageActivity && (message.getOtherUser() == message.getFrom())) {
								messageActivity = true;
							}
						}
					}
				}
				else {
					boolean added = chatAdapter.addOrUpdateMessage(message, false, false);

					// if we didn't send the message and it's new
					if (!messageActivity && message.getFrom().equals(message.getOtherUser()) && added) {
						messageActivity = true;
					}
				}
			}
			catch (JSONException e) {
				SurespotLog.w(TAG, username + ": error creating chat message: " + e.toString(), e);
			}

		}

		if (message != null) {
			SurespotLog.v(TAG, username + ": loaded: " + jsonArray.length() + " latest messages from the server.");
			mLastReceivedMessageIds.put(username, message.getId());
			updateLastViewedMessageId(username, messageActivity);
			//
			// Runnable runnable = new Runnable() {
			//
			// @Override
			// public void run() {
			chatAdapter.sort();
			chatAdapter.notifyDataSetChanged();
			//
			// }
			// };
			//
			// MainActivity.getMainHandler().post(runnable);

		}
	}

	// tell the chat adapters we've loaded their data (even if they didn't have any)
	public void setMessagesLoading(boolean loading) {
		for (ChatAdapter ca : mChatAdapters.values()) {
			ca.setLoading(loading);
		}
	}

	private Integer getEarliestMessageId(String username) {

		SurespotMessage firstMessage = getChatAdapter(mContext, username).getFirstMessageWithId();

		Integer firstMessageId = null;

		if (firstMessage != null) {
			firstMessageId = firstMessage.getId();
		}
		return firstMessageId;
	}

	private Integer getLatestMessageId(String username) {
		Integer lastMessageId = 0;
		SurespotMessage lastMessage = getChatAdapter(mContext, username).getLastMessageWithId();
		if (lastMessage != null) {
			lastMessageId = lastMessage.getId();
		}
		return lastMessageId;

	}

	private synchronized void loadMessages(String username) {
		SurespotLog.v(TAG, "loadMessages: " + username);
		String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), username);
		getChatAdapter(mContext, username).addMessages(MainActivity.getStateController().loadMessages(spot));
	}

	private synchronized void saveMessages() {
		// save last 30? messages
		SurespotLog.v(TAG, "saveMessages");
		for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
			String them = entry.getKey();
			String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), them);
			MainActivity.getStateController().saveMessages(spot, entry.getValue().getMessages());
		}

	}

	private synchronized void saveMessages(String username) {
		// save last 30? messages
		SurespotLog.v(TAG, "saveMessages, username:" + username);
		ChatAdapter chatAdapter = getChatAdapter(mContext, username);
		MainActivity.getStateController().saveMessages(ChatUtils.getSpot(IdentityController.getLoggedInUser(), username),
				chatAdapter.getMessages());

	}

	private void saveUnsentMessages() {
		mResendBuffer.addAll(mSendBuffer);
		// SurespotLog.v(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		MainActivity.getStateController().saveUnsentMessages(mResendBuffer);
	}

	private void loadUnsentMessages() {
		Iterator<SurespotMessage> iterator = MainActivity.getStateController().loadUnsentMessages().iterator();
		while (iterator.hasNext()) {
			mResendBuffer.add(iterator.next());
		}
		// SurespotLog.v(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
	}

	public synchronized void logout() {
		mCurrentChat = null;
		onPause();
		mViewPager = null;
		mChatPagerAdapter = null;
		mIndicator = null;
		mFragmentManager = null;
		mFriendAdapter = null;
		mMenuItems = null;
		mSocketCallback = null;
		mChatAdapters.clear();
		mActiveChats.clear();
		mLastReceivedMessageIds.clear();
		mMessageActivity.clear();
		mResendBuffer.clear();
		mSendBuffer.clear();
	}

	private void saveState(String username) {

		SurespotLog.v(TAG, "saveState");
		saveUnsentMessages();

		if (username == null) {
			saveMessages();
		}
		else {
			saveMessages(username);
		}

		SurespotLog.v(TAG, "setting last chat to: " + mCurrentChat);
		Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.LAST_CHAT, mCurrentChat);

		MainActivity.getStateController().saveActiveChats(mActiveChats);
		MainActivity.getStateController().saveLastReceivedMessageIds(mLastReceivedMessageIds);
		MainActivity.getStateController().saveMessageActivity(mMessageActivity);

	}

	private void loadState() {
		SurespotLog.v(TAG, "loadState");
		mMessageActivity = MainActivity.getStateController().loadMessageActivity();
		mLastReceivedMessageIds = MainActivity.getStateController().loadLastReceivedMessageIds();
		mActiveChats = MainActivity.getStateController().loadActiveChats();

		loadUnsentMessages();
	}

	public synchronized void onResume() {
		SurespotLog.v(TAG, "onResume, mPaused: " + mPaused + ": " + this);
		if (mPaused) {
			mPaused = false;

			connect();
			mContext.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		}
	}

	public synchronized void onPause() {
		SurespotLog.v(TAG, "onPause, mPaused: " + mPaused + ": " + this);
		if (!mPaused) {

			mPaused = true;
			saveState(null);
		}

		disconnect();

		if (mBackgroundTimer != null) {
			mBackgroundTimer.cancel();
			mBackgroundTimer = null;
		}
		if (mReconnectTask != null) {
			boolean cancel = mReconnectTask.cancel();
			mReconnectTask = null;
			SurespotLog.v(TAG, "Cancelled reconnect task: " + cancel);
		}

		// socket = null;

		// workaround unchecked exception: https://code.google.com/p/android/issues/detail?id=18147
		try {
			mContext.unregisterReceiver(mConnectivityReceiver);
		}
		catch (IllegalArgumentException e) {
			if (e.getMessage().contains("Receiver not registered")) {
				// Ignore this exception. This is exactly what is desired
			}
			else {
				// unexpected, re-throw
				throw e;
			}
		}
		// }

	}

	ChatAdapter getChatAdapter(Context context, String username) {

		ChatAdapter chatAdapter = mChatAdapters.get(username);
		if (chatAdapter == null) {

			chatAdapter = new ChatAdapter(context);
			SurespotLog.v(TAG, "getChatAdapter created chat adapter for: " + username + ", id:  " + chatAdapter);
			mChatAdapters.put(username, chatAdapter);

			// load savedmessages
			loadMessages(username);

		}
		return chatAdapter;
	}

	public void destroyChatAdapter(String username) {
		SurespotLog.v(TAG, "destroying chat adapter for: " + username);
		saveState(username);
		mChatAdapters.remove(username);
	}

	public synchronized void setCurrentChat(final String username) {

		SurespotLog.v(TAG, username + ": setCurrentChat");
		mCurrentChat = username;

		if (username != null) {
			mChatPagerAdapter.addChatName(username);
			mFriendAdapter.setChatActive(username, true);
			mActiveChats.add(username);
			updateLastViewedMessageId(username, false);
			// cancel associated notifications
			mNotificationManager.cancel(ChatUtils.getSpot(IdentityController.getLoggedInUser(), username),
					SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
			int wantedPosition = mChatPagerAdapter.getChatFragmentPosition(username);

			if (wantedPosition != mViewPager.getCurrentItem()) {
				mViewPager.setCurrentItem(wantedPosition, true);
			}

			if (mMode == MODE_SELECT) {

				String fragmentTag = Utils.makePagerFragmentName(mViewPager.getId(), username.hashCode());
				SurespotLog.v(TAG, "looking for fragment: " + fragmentTag);
				ChatFragment chatFragment = (ChatFragment) mFragmentManager.findFragmentByTag(fragmentTag);
				SurespotLog.v(TAG, "fragment: " + chatFragment);
				chatFragment.handleSendIntent();

				setMode(MODE_NORMAL);

			}
			enableMenuItems();
		}
		else {
			mViewPager.setCurrentItem(0, true);
			mNotificationManager.cancel(SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
			mNotificationManager.cancel(SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);

			// disable menu items
			enableMenuItems();

		}
	}

	void sendMessage(final String username, final String plainText, final String mimeType) {
		if (plainText.length() > 0) {

			// display the message immediately
			final byte[] iv = EncryptionController.getIv();

			// build a message without the encryption values set as they could take a while
			final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(username, mimeType, plainText,
					new String(Utils.base64Encode(iv)));
			getChatAdapter(mContext, username).addOrUpdateMessage(chatMessage, true, true);

			// do encryption in background
			new AsyncTask<Void, Void, SurespotMessage>() {

				@Override
				protected SurespotMessage doInBackground(Void... arg0) {
					String ourLatestVersion = IdentityController.getOurLatestVersion();
					String theirLatestVersion = IdentityController.getTheirLatestVersion(username);

					String result = EncryptionController.symmetricEncrypt(ourLatestVersion, username, theirLatestVersion, plainText, iv);

					if (result != null) {
						chatMessage.setCipherData(result);
						chatMessage.setFromVersion(ourLatestVersion);
						chatMessage.setToVersion(theirLatestVersion);
						ChatController.this.sendMessage(chatMessage);
					}

					return null;
				}

				// protected void onPostExecute(SurespotMessage result) {
				// if (result != null) {
				//
				//
				// }
				//
				// };
			}.execute();
		}
	}

	public static String getCurrentChat() {
		return mCurrentChat;
	}

	public static boolean isPaused() {
		return mPaused;
	}

	public boolean hasEarlierMessages(String username) {
		Integer id = mEarliestMessage.get(username);
		if (id == null) {
			id = getEarliestMessageId(username);
		}

		if (id != null && id > 1) {
			return true;
		}

		return false;
	}

	public void deleteMessage(SurespotMessage message) {
		// if it's on the server, send delete message otherwise just delete it locally
		if (message.getId() != null) {
			SurespotMessage dmessage = new SurespotMessage();

			dmessage.setType("system");
			dmessage.setSubType("delete");
			dmessage.setTo(message.getTo());
			dmessage.setFrom(message.getFrom());
			dmessage.setIv(message.getId().toString());

			sendMessage(dmessage);
		}
		else {
			String otherUser = message.getOtherUser();
			mResendBuffer.remove(message);
			mSendBuffer.remove(message);
			getChatAdapter(mContext, otherUser).deleteMessageByIv(message.getIv());
			saveState(otherUser);
		}
	}

	public FriendAdapter getFriendAdapter() {
		return mFriendAdapter;
	}

	private void loadFriends() {
		mFriendAdapter.setLoading(true);
		// get the list of friends
		MainActivity.getNetworkController().getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				SurespotLog.v(TAG, "getFriends success.");
				ArrayList<Friend> friends = new ArrayList<Friend>();
				try {
					for (int i = 0; i < jsonArray.length(); i++) {
						JSONObject jsonFriend = jsonArray.getJSONObject(i);
						Friend friend = Friend.toFriend(jsonFriend);
						friend.setChatActive(mActiveChats.contains(friend.getName()));
						friend.setMessageActivity(getActivity(friend.getName()));
						friends.add(friend);

					}
				}
				catch (JSONException e) {
					SurespotLog.e(TAG, e.toString(), e);
					mFriendAdapter.setLoading(false);
					return;
				}

				mFriendAdapter.setFriends(friends);
				mFriendAdapter.setLoading(false);
				loadAllLatestMessages();
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				// if we didn't get a 401
				if (!MainActivity.getNetworkController().isUnauthorized()) {
					mFriendAdapter.setLoading(false);
					SurespotLog.w(TAG, "getFriends: " + content, arg0);
				}
			}
		});
	}

	public void closeTab() {
		if (mChatPagerAdapter.getCount() > 0) {

			int position = mViewPager.getCurrentItem();
			if (position > 0) {

				String name = mChatPagerAdapter.getChatName(position);
				SurespotLog.v(TAG, "closeTab, name: " + name + ", position: " + position);

				mChatPagerAdapter.removeChat(mViewPager.getId(), position);
				destroyChatAdapter(name);
				mIndicator.notifyDataSetChanged();
				mFriendAdapter.setChatActive(name, false);
				mActiveChats.remove(name);
				mEarliestMessage.remove(name);
			}
		}
	}

	public synchronized void setMode(int mode) {
		mMode = mode;
	}

	public int getMode() {
		return mMode;
	}

	public void enableMenuItems() {
		boolean enabled = mMode != MODE_SELECT && mCurrentChat != null;
		SurespotLog.v(TAG, "enableMenuItems, mMenuItems: " + mMenuItems + ", enabled: " + enabled);
		if (mMenuItems != null) {
			for (MenuItem menuItem : mMenuItems) {
				menuItem.setVisible(enabled);
			}
		}
	}
}
