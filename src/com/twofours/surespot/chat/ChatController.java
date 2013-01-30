package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.Utils;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.ui.activities.LoginActivity;
import com.twofours.surespot.ui.activities.StartupActivity;

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
	private IConnectCallback mConnectCallback;
	private IOCallback mSocketCallback;

	private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
	private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();

	private int mState;
	private boolean mOnWifi;

	private BroadcastReceiver mConnectivityReceiver;

	//
	public ChatController(IConnectCallback connectCallback) {
		SurespotLog.v(TAG, "constructor.");
		mConnectCallback = connectCallback;

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
				SurespotLog.v(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: " + mRetries);

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

					// Toast.makeText(SurespotApplication.getAppContext(),
					// "Can not connect to chat server. Please check your network and try again.",
					// Toast.LENGTH_LONG).show(); // TODO tie in with network controller 401 handling
					Intent intent = new Intent(SurespotApplication.getAppContext(), LoginActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					SurespotApplication.getAppContext().startActivity(intent);

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
				mRetries = 0;

				if (mBackgroundTimer != null) {
					mBackgroundTimer.cancel();
					mBackgroundTimer = null;
				}

				if (mReconnectTask != null && mReconnectTask.cancel()) {
					SurespotLog.v(TAG, "Cancelled reconnect timer.");
					mReconnectTask = null;
				}

				if (mConnectCallback != null) {
					mConnectCallback.connectStatus(true);
				}

				sendConnectStatus(true);

				sendMessages();
				SurespotApplication.getAppContext().registerReceiver(mConnectivityReceiver,
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
					sendMessageReceived((String) args[0]);
					// TODO check who from
					try {
						SurespotMessage cm = SurespotMessage.toSurespotMessage(new JSONObject((String) args[0]));
						checkAndSendNextMessage(cm);
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

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						synchronized (ChatController.this) {
							// if we're not connecting, connect
							if (getState() != STATE_CONNECTING && !mOnWifi) {
								SurespotLog.v(TAG, "isconnected: " + networkInfo.isConnected());
								SurespotLog.v(TAG, "failover: " + networkInfo.isFailover());
								SurespotLog.v(TAG, "reason: " + networkInfo.getReason());
								SurespotLog.v(TAG, "type: " + networkInfo.getTypeName());

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

		loadUnsentMessages();

	}

	private void setOnWifi() {
		// get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
		ConnectivityManager connectivityManager = (ConnectivityManager) SurespotApplication.getAppContext().getSystemService(
				Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo != null) {
			mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
		}

	}

	public void connect() {

		/*
		 * if (socket != null && socket.isConnected()) { if (mConnectCallback != null) { mConnectCallback.connectStatus(true); } return; }
		 */

		Cookie cookie = NetworkController.getConnectCookie();

		if (cookie == null) {
			// need to login
			SurespotLog.v(TAG, "No session cookie, starting Login activity.");
			Intent startupIntent = new Intent(SurespotApplication.getAppContext(), StartupActivity.class);
			startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			SurespotApplication.getAppContext().startActivity(startupIntent);
			return;
		}

		try {
			Properties headers = new Properties();
			headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
			socket = new SocketIO(SurespotConstants.WEBSOCKET_URL, headers);
			socket.connect(mSocketCallback);

		}
		catch (MalformedURLException e1) {
			// Auto-generated
			e1.printStackTrace();
			// callback.connectStatus(false);
		}

	}

	private void sendInviteRequest(String friend) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_REQUEST);
		intent.putExtra(SurespotConstants.ExtraNames.NAME, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private void sendInviteResponse(String response) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_RESPONSE);
		intent.putExtra(SurespotConstants.ExtraNames.INVITE_RESPONSE, response);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private void sendMessageReceived(String message) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED);
		intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

	}

	private void sendConnectStatus(boolean connected) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.SOCKET_CONNECTION_STATUS_CHANGED);
		intent.putExtra(SurespotConstants.ExtraNames.CONNECTED, connected);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

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

	public SurespotMessage[] getResendMessages() {
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

	public void disconnect() {
		SurespotLog.v(TAG, "disconnect.");
		setState(STATE_DISCONNECTED);
		if (socket != null && socket.isConnected()) {
			socket.disconnect();

			// workaround unchecked exception: https://code.google.com/p/android/issues/detail?id=18147
			try {
				SurespotApplication.getAppContext().unregisterReceiver(mConnectivityReceiver);
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
		sendConnectStatus(false);

		// socket = null;
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

	public void saveUnsentMessages() {
		mResendBuffer.addAll(mSendBuffer);
		SurespotLog.v(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		Utils.putSharedPrefsString(SurespotConstants.PrefNames.UNSENT_MESSAGES, Utils.chatMessagesToJson(mResendBuffer).toString());

	}

	public void loadUnsentMessages() {
		String sUnsentMessages = Utils.getSharedPrefsString("unsentmessages");

		if (sUnsentMessages != null && !sUnsentMessages.isEmpty()) {
			Iterator<SurespotMessage> iterator = Utils.jsonStringToChatMessages(sUnsentMessages).iterator();
			while (iterator.hasNext()) {
				mSendBuffer.add(iterator.next());
			}
			SurespotLog.v(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
		}

		Utils.putSharedPrefsString(SurespotConstants.PrefNames.UNSENT_MESSAGES, null);

	}

	public void destroy() {
		SurespotLog.v(TAG, "destroy.");
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

	public void sendMessage(SurespotMessage message) {
		mResendBuffer.add(message);
		if (getState() == STATE_CONNECTED) {
			// TODO handle different mime types

			socket.send(message.toJSONObject().toString());
		}

	}

	public boolean isConnected() {
		return (getState() == STATE_CONNECTED);
	}
}
