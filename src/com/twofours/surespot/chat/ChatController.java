package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
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
import android.support.v4.content.LocalBroadcastManager;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.StartupActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class ChatController {

	private static final String TAG = "ChatController";
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;
	private static final int STATE_DISCONNECTED = 2;

	private static final int MAX_RETRIES = 5;
	private SocketIO socket;
	private int mRetries = 0;
	private Timer mBackgroundTimer;
	private TimerTask mResendTask;

	private IOCallback mSocketCallback;

	private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
	private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();

	private int mState;
	private boolean mOnWifi;
	private NotificationManager mNotificationManager;
	private BroadcastReceiver mConnectivityReceiver;
	private HashMap<String, ChatAdapter> mChatAdapters;
	private HashMap<String, Integer> mLastViewedMessageIds;
	private HashMap<String, Boolean> mReadSinceReconnect;
	private static String mCurrentChat;

	private static boolean mTrackChat;
	private static boolean mPaused;

	private Context mContext;

	// private

	//
	public ChatController(Context context) {
		SurespotLog.v(TAG, "constructor.");

		mContext = context;
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mChatAdapters = new HashMap<String, ChatAdapter>();
		mReadSinceReconnect = new HashMap<String, Boolean>();

		setOnWifi();

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
				//socket.io returns 403 for can't login
				if (socketIOException.getHttpStatus() == 403) {
					SurespotApplication.getNetworkController().setUnauthorized(true);
				
					SurespotLog.v(TAG, "Got 403 from socket.io, launching login intent.");
					Intent intent = new Intent(mContext, StartupActivity.class);
					intent.putExtra("401", true);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
					mContext.startActivity(intent);
					
					return;
				}
				
				SurespotLog.w(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: " + mRetries,
						socketIOException);

				if (mResendTask != null) {
					mResendTask.cancel();
				}

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

					// Utils.makeToast(this,SurespotApplication.getContext(),
					// "Can not connect to chat server. Please check your network and try again.",
					// Toast.LENGTH_LONG).show(); // TODO tie in with network controller 401 handling
					Intent intent = new Intent(SurespotApplication.getContext(), StartupActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					SurespotApplication.getContext().startActivity(intent);

				}
			}

			@Override
			public void onDisconnect() {
				SurespotLog.v(TAG, "Connection terminated.");
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

				sendMessages();
				SurespotApplication.getContext().registerReceiver(mConnectivityReceiver,
						new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				SurespotLog.v(TAG, "Server triggered event '" + event + "'");

				if (event.equals("notification")) {
					JSONObject json = (JSONObject) args[0];
					try {
						sendInviteRequest(json.getString("data"));
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "on", e);
					}
					return;
				}
				if (event.equals("inviteResponse")) {
					sendInviteResponse((String) args[0]);
					return;
				}
				if (event.equals("message")) {

					// TODO check who from
					try {
						SurespotMessage cm = SurespotMessage.toSurespotMessage(new JSONObject((String) args[0]));

						// check the type
						if (cm.getType().equals("system")) {
							// revoke the cert
							if (cm.getSubType().equals("revoke")) {
								String username = cm.getFrom();
								String version = cm.getFromVersion();

								// this will force download of key
								IdentityController.updateLatestVersion(SurespotApplication.getContext(), username, version);
							}
						}
						else {
							sendMessageReceived((String) args[0]);
							ChatAdapter chatAdapter = mChatAdapters.get(cm.getOtherUser());
							if (chatAdapter != null) {
								chatAdapter.addOrUpdateMessage(cm, true);

							}

							updateLastViewedMessageId(cm.getOtherUser(), cm.getId());
							checkAndSendNextMessage(cm);
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

	private void connect() {

		/*
		 * if (socket != null && socket.isConnected()) { if (mConnectCallback != null) { mConnectCallback.connectStatus(true); } return; }
		 */

		Cookie cookie = IdentityController.getCookie();

		if (cookie == null) {
			// need to login
			// SurespotLog.v(TAG, "No session cookie, starting Login activity.");
			// Intent startupIntent = new Intent(SurespotApplication.getContext(), StartupActivity.class);
			// startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			// SurespotApplication.getContext().startActivity(startupIntent);
			return;
		}

		try {
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
			socket = new SocketIO( SurespotConfiguration.getBaseUrl(),  headers);
			socket.connect(mSocketCallback);
		}
		catch (Exception e) {

			SurespotLog.w(TAG, "connect", e);
		}

	}

	private void disconnect() {
		SurespotLog.v(TAG, "disconnect.");
		setState(STATE_DISCONNECTED);

		if (socket != null && socket.isConnected()) {
			socket.disconnect();
		}

		// workaround unchecked exception: https://code.google.com/p/android/issues/detail?id=18147
		try {
			SurespotApplication.getContext().unregisterReceiver(mConnectivityReceiver);
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
	}

	private void connected() {
		// get the resend messages
		SurespotMessage[] resendMessages = getResendMessages();
		for (SurespotMessage message : resendMessages) {
			// set the last received id so the server knows which messages to check
			String room = message.getOtherUser();

			String username = message.getFrom();

			// ideally get the last id from the fragment's chat adapter
			Integer lastMessageID = getLatestMessageId(username);

			// failing that use the last viewed id
			if (lastMessageID == null) {

				lastMessageID = mLastViewedMessageIds.get(room);

			}

			// last option, check last x messages for dupes
			if (lastMessageID == null) {
				lastMessageID = -1;
			}
			SurespotLog.v(TAG, "setting resendId, room: " + room + ", id: " + lastMessageID);
			message.setResendId(lastMessageID);
			sendMessage(message);

		}

		// reset read since reconnect flags
		for (String chat : mReadSinceReconnect.keySet()) {
			mReadSinceReconnect.put(chat, false);
		}

		if (mTrackChat) {
			loadLatestMessages(mCurrentChat);
		}
	}

	private void setOnWifi() {
		// get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
		ConnectivityManager connectivityManager = (ConnectivityManager) SurespotApplication.getContext().getSystemService(
				Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo != null) {
			mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
		}

	}

	private void sendInviteRequest(String friend) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_REQUEST);
		intent.putExtra(SurespotConstants.ExtraNames.NAME, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getContext()).sendBroadcast(intent);
	}

	private void sendInviteResponse(String response) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_RESPONSE);
		intent.putExtra(SurespotConstants.ExtraNames.INVITE_RESPONSE, response);
		LocalBroadcastManager.getInstance(SurespotApplication.getContext()).sendBroadcast(intent);
	}

	private void sendMessageReceived(String message) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED);
		intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
		LocalBroadcastManager.getInstance(SurespotApplication.getContext()).sendBroadcast(intent);

	}

	// private void sendConnectStatus(boolean connected) {
	// Intent intent = new Intent(SurespotConstants.IntentFilters.SOCKET_CONNECTION_STATUS_CHANGED);
	// intent.putExtra(SurespotConstants.ExtraNames.CONNECTED, connected);
	// LocalBroadcastManager.getInstance(SurespotApplication.getContext()).sendBroadcast(intent);
	//
	// }

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

		if (mResendTask != null) {
			mResendTask.cancel();
		}

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
			socket.send(message.toJSONObject().toString());
		}
	}

	private int getState() {
		return mState;
	}

	private synchronized void setState(int state) {
		mState = state;
	}

	private ReconnectTask mReconnectTask;

	private class ReconnectTask extends TimerTask {

		@Override
		public void run() {
			SurespotLog.v(TAG, "Reconnect task run.");
			connect();
		}
	}

	private class ResendTask extends TimerTask {

		@Override
		public void run() {
			// resend message
			sendMessages();
		}
	}

	private boolean isConnected() {
		return (getState() == STATE_CONNECTED);
	}

	private void updateLastViewedMessageId(String username, Integer id) {

		if (mTrackChat && username.equals(mCurrentChat)) {

			if (id != null) {
				SurespotLog.v(TAG, username + ": setting last viewed message id to: " + id);
				mLastViewedMessageIds.put(username, id);
			}
			else {
				Integer latestMessageId = getLatestMessageId(username);
				SurespotLog.v(TAG, username + ": setting last viewed message id to: " + latestMessageId);
				mLastViewedMessageIds.put(username, latestMessageId);
			}
		}
	}

	private void loadLatestMessages(final String username) {
		SurespotLog.v(TAG, "loadlatestMessages: " + username);
		final ChatAdapter chatAdapter = getChatAdapter(mContext, username);

		// get the list of messages
		Integer lastMessageId = getLatestMessageId(username);

		SurespotLog.v(TAG, username + ": asking server for messages after messageId: " + lastMessageId);
		SurespotApplication.getNetworkController().getMessages(username, lastMessageId, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {

				SurespotMessage message = null;

				for (int i = 0; i < jsonArray.length(); i++) {
					try {
						JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
						message = SurespotMessage.toSurespotMessage(jsonMessage);

						// if it's a system message from another user then check version
						if (message.getType().equals("system")) {
							if (message.getSubType().equals("revoke") && !message.getFrom().equals(IdentityController.getLoggedInUser())) {
								IdentityController.updateLatestVersion(mContext, message.getFrom(), message.getFromVersion());
							}
						}
						else {
							chatAdapter.addOrUpdateMessage(message, false);
						}
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, username + ": error creating chat message: " + e.toString(), e);
					}

				}

				if (message != null) {
					SurespotLog.v(TAG, username + ": loaded: " + jsonArray.length() + " latest messages from the server.");

					updateLastViewedMessageId(username, message.getId());

				}
				else {
					// update to the last id if we didn't load any messages because they could have been added to the adapter when
					// the tab wasn't showing
					updateLastViewedMessageId(username, null);
				}

				// we've read the messages now, yay
				mReadSinceReconnect.put(username, true);
				chatAdapter.notifyDataSetChanged();
			}

			@Override
			public void onFailure(Throwable error, String content) {
				// if (!SurespotApplication.getNetworkController().isUnauthorized()) {
				SurespotLog.w(TAG, username + ": getMessages", error);

			}
		});

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

	private void loadMessages(String username) {
		SurespotLog.v(TAG, "loadMessages: " + username);
		String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), username);
		getChatAdapter(mContext, username).addMessages(SurespotApplication.getStateController().loadMessages(spot));
	}

	private void saveMessages() {
		// save last 30? messages
		SurespotLog.v(TAG, "saveMessages");
		for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
			String us = entry.getKey();
			String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), us);
			SurespotApplication.getStateController().saveMessages(spot, entry.getValue().getMessages());
		}

	}

	private void saveUnsentMessages() {
		mResendBuffer.addAll(mSendBuffer);
		// SurespotLog.v(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		SurespotApplication.getStateController().saveUnsentMessages(mResendBuffer);
	}

	private void loadUnsentMessages() {
		Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages().iterator();
		while (iterator.hasNext()) {
			mSendBuffer.add(iterator.next());
		}
		// SurespotLog.v(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
	}

	private void saveState() {
		SurespotLog.v(TAG, "saveState");

		saveUnsentMessages();

		saveMessages();
		// store chats the user went into

		if (!mLastViewedMessageIds.isEmpty()) {
			SurespotLog.v(TAG, "saving last viewed message ids");
			SurespotApplication.getStateController().saveLastViewedMessageIds(mLastViewedMessageIds);
		}

		// TODO save per user?
		if (mTrackChat && mCurrentChat != null) {
			SurespotLog.v(TAG, "setting last chat to: " + mCurrentChat);
			Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.LAST_CHAT, mCurrentChat);
		}

	}

	private void loadState() {
		SurespotLog.v(TAG, "loadState");
		mLastViewedMessageIds = SurespotApplication.getStateController().loadLastViewMessageIds();
		loadUnsentMessages();
	}

	public void onResume(boolean trackChat) {
		SurespotLog.v(TAG, "onResume, mtrackchat: " + mTrackChat);
		mPaused = false;

		loadState();

		connect();

		mTrackChat = trackChat;

	}

	public void onPause() {
		SurespotLog.v(TAG, "onPause");
		mPaused = true;
		disconnect();
		saveState();

		if (mBackgroundTimer != null) {
			mBackgroundTimer.cancel();
			mBackgroundTimer = null;
		}
		if (mReconnectTask != null) {
			boolean cancel = mReconnectTask.cancel();
			mReconnectTask = null;
			SurespotLog.v(TAG, "Cancelled reconnect task: " + cancel);
		}

		socket = null;
	}

	ChatAdapter getChatAdapter(Context context, String username) {

		ChatAdapter chatAdapter = mChatAdapters.get(username);
		if (chatAdapter == null) {
			SurespotLog.v(TAG, "getChatAdapter creating chat adapter for: " + username);
			chatAdapter = new ChatAdapter(context);
			mChatAdapters.put(username, chatAdapter);

			// load savedmessages
			loadMessages(username);

			// load any new messages
			loadLatestMessages(username);
		}
		return chatAdapter;
	}

	void destroyChatAdapter(String username) {
		SurespotLog.v(TAG, "destroying chat adapter for: " + username);
		mChatAdapters.remove(username);

	}

	void setCurrentChat(final String username, boolean creating) {

		SurespotLog.v(TAG, username + ": setCurrentChat");
		mCurrentChat = username;

		// if we've read since we connected, don't read again
		Boolean read = mReadSinceReconnect.get(username);
		if (read != null && read.booleanValue()) {
			SurespotLog.v(TAG, username + ": not asking the server for more messages as we've read since connecting");

			updateLastViewedMessageId(username, null);

			// cancel associated notifications
			mNotificationManager.cancel(ChatUtils.getSpot(IdentityController.getLoggedInUser(), username),
					SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
		}
		else {
			if (!creating) {
				loadLatestMessages(username);
			}
			// else {
			// new AsyncTask<Void, Void, Void>() {
			// protected Void doInBackground(Void... params) {
			// // get the key going
			// final String ourVersion = IdentityController.getOurLatestVersion();
			// final String theirVersion = IdentityController.getTheirLatestVersion(username);
			//
			// // SurespotApplication.getCachingService().getSharedSecret(ourVersion, username, theirVersion);
			// return null;
			// }
			// }.execute();
			// }
		}

	}

	void sendMessage(final String username, final String plainText, final String mimeType) {
		if (plainText.length() > 0) {

			// display the message immediately
			final byte[] iv = EncryptionController.getIv();

			// build a message without the encryption values set as they could take a while
			final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(username, mimeType, plainText,
					new String(Utils.base64Encode(iv)));
			getChatAdapter(mContext, username).addOrUpdateMessage(chatMessage, true);

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

	// message handling shiznit

	public void loadEarlierMessages(final String username) {

		// mLoading = true;
		// get the list of messages
		Integer firstMessageId = getEarliestMessageId(username);

		if (firstMessageId != null) {
			if (firstMessageId > 1) {
				SurespotLog.v(TAG, username + ": asking server for messages before messageId: " + firstMessageId);
				SurespotApplication.getNetworkController().getEarlierMessages(username, firstMessageId, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here

						// if (getActivity() != null) {
						SurespotMessage message = null;
						ChatAdapter chatAdapter = getChatAdapter(mContext, username);
						try {
							for (int i = jsonArray.length() - 1; i >= 0; i--) {
								JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
								message = SurespotMessage.toSurespotMessage(jsonMessage);

								// if it's a system message ignore it
								if (message.getType().equals("user")) {
									chatAdapter.insertMessage(message, false);
								}

							}
						}
						catch (JSONException e) {
							SurespotLog.e(TAG, username + ": error creating chat message: " + e.toString(), e);
						}

						SurespotLog.v(TAG, username + ": loaded: " + jsonArray.length() + " earlier messages from the server.");

						chatAdapter.notifyDataSetChanged();
					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.w(TAG, username + ": getEarlierMessages", error);
					}
				});
			}
			else {
				SurespotLog.v(TAG, username + ": getEarlierMessages: no more messages.");
				// ChatFragment.this.mNoEarlierMessages = true;
			}
		}
	}

	public static String getCurrentChat() {
		return mCurrentChat;
	}

	public static boolean getTrackChat() {
		return mTrackChat;
	}

	public static boolean isPaused() {
		return mPaused;
	}

	public boolean hasEarlierMessages(String username) {
		Integer id = getEarliestMessageId(username);
		if (id != null && id > 1) {
			return true;
		}

		return false;
	}

	public synchronized void logout() {
		// saveState();

		mTrackChat = false;
		// mLastViewedMessageIds.clear();
		mChatAdapters.clear();
		mCurrentChat = null;
		Utils.putSharedPrefsString(SurespotApplication.getContext(), SurespotConstants.PrefNames.LAST_CHAT, null);
	}

}
