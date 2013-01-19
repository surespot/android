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

import org.apache.http.cookie.Cookie;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.drm.DrmManagerClient.OnErrorListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.ui.activities.LoginActivity;
import com.twofours.surespot.ui.activities.StartupActivity;

public class ChatController {

	private static final String TAG = "ChatController";
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;

	private static final int MAX_RETRIES = 5;
	private SocketIO socket;
	private int mRetries = 0;
	private Timer mBackgroundTimer;
	private TimerTask mResendTask;
	private IConnectCallback mConnectCallback;
	private IOCallback mSocketCallback;

	private ConcurrentLinkedQueue<ChatMessage> mSendBuffer = new ConcurrentLinkedQueue<ChatMessage>();
	private ConcurrentLinkedQueue<ChatMessage> mResendBuffer = new ConcurrentLinkedQueue<ChatMessage>();

	private int mState;

	private BroadcastReceiver mConnectivityReceiver;

	//
	public ChatController(IConnectCallback connectCallback) {
		Log.v(TAG, "constructor.");
		mConnectCallback = connectCallback;

		mSocketCallback = new IOCallback() {

			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					Log.v(TAG, "JSON Server said:" + json.toString(2));

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				Log.v(TAG, "Server said: " + data);
			}

			@Override
			public synchronized void onError(SocketIOException socketIOException) {
				Log.v(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: " + mRetries);

				if (mResendTask != null) {
					mResendTask.cancel();
				}

				// kick off another task
				if (mRetries <= MAX_RETRIES) {

					if (mReconnectTask != null) {
						mReconnectTask.cancel();
					}

					int timerInterval = (int) (Math.pow(2, mRetries++) * 1000);
					Log.v(TAG, "Starting another task in: " + timerInterval);

					mReconnectTask = new ReconnectTask();
					if (mBackgroundTimer == null) {
						mBackgroundTimer = new Timer("backgroundTimer");
					}
					mBackgroundTimer.schedule(mReconnectTask, timerInterval);
				} else {
					// TODO tell user
					Log.e(TAG, "Socket.io reconnect retries exhausted, giving up.");
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
				Log.v(TAG, "Connection terminated.");
			}

			@Override
			public void onConnect() {
				Log.v(TAG, "socket.io connection established");
				setState(STATE_CONNECTED);
				mRetries = 0;
				if (mBackgroundTimer != null) {
					mBackgroundTimer.cancel();
					mBackgroundTimer = null;
				}

				if (mReconnectTask != null && mReconnectTask.cancel()) {
					Log.v(TAG, "Cancelled reconnect timer.");
				}

				mSendBuffer.addAll(mResendBuffer);
				mResendBuffer.clear();

				if (mConnectCallback != null) {
					mConnectCallback.connectStatus(true);
				}

				sendMessages();

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				Log.v(TAG, "Server triggered event '" + event + "'");

				if (event.equals("notification")) {
					JSONObject json = (JSONObject) args[0];
					try {
						sendInviteRequest(json.getString("data"));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
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
						ChatMessage cm = ChatMessage.toChatMessage(new JSONObject((String) args[0]));
						checkAndSendNextMessage(cm);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		};

		mConnectivityReceiver = new BroadcastReceiver() {

			@Override
			public synchronized void onReceive(Context context, Intent intent) {
				Log.v(TAG, "Connectivity Action");
				ConnectivityManager connectivityManager = (ConnectivityManager) context
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
				if (networkInfo != null) {

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover()
							&& (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						// if we're not connecting, connect
						if (getState() != STATE_CONNECTING) {
							Log.v(TAG, "isconnected: " + networkInfo.isConnected());
							Log.v(TAG, "failover: " + networkInfo.isFailover());
							Log.v(TAG, "reason: " + networkInfo.getReason());
							Log.v(TAG, "type: " + networkInfo.getTypeName());

							Log.v(TAG, "Network switch, Reconnecting...");

							setState(STATE_CONNECTING);
							disconnect();
							connect();
						}
					}
				} else {
					Log.v(TAG, "networkinfo null");
				}
			}
		};

		loadUnsentMessages();

		SurespotApplication.getAppContext().registerReceiver(mConnectivityReceiver,
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public void connect() {

		/*
		 * if (socket != null && socket.isConnected()) { if (mConnectCallback != null) {
		 * mConnectCallback.connectStatus(true); } return; }
		 */

		Cookie cookie = NetworkController.getConnectCookie();

		if (cookie == null) {
			// need to login
			Log.v(TAG, "No session cookie, starting Login activity.");
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

		} catch (MalformedURLException e1) {
			// Auto-generated
			e1.printStackTrace();
			// callback.connectStatus(false);
		}

	}

	private static void sendInviteRequest(String friend) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_REQUEST);
		intent.putExtra(SurespotConstants.ExtraNames.NAME, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private static void sendInviteResponse(String response) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITE_RESPONSE);
		intent.putExtra(SurespotConstants.ExtraNames.INVITE_RESPONSE, response);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private static void sendMessageReceived(String message) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED);
		intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

	}

	private void checkAndSendNextMessage(ChatMessage message) {
		Log.v(TAG, "received message: " + message);
		sendMessages();

		if (mResendBuffer.size() > 0) {
			// String sentMessage = mMessageBuffer.peek();
			// Log.v(TAG, "message we sent: " + sentMessage);

			// TODO deserialize and check fields (id is added so can't do equals)
			// if (message.startsWith(sentMessage.substring(0, sentMessage.length() - 1))) {

			if (mResendBuffer.remove(message))
				Log.v(TAG, "Received and removed message from resend  buffer: " + message);

			// } else {
			// Log.e(TAG,"didn't receive same message we sent.");
			// }
		}
	}

	// private static void sendConnectionStatusChanged(boolean status) {
	// Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED_EVENT);
	// intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
	// LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	//
	// }

	public void sendMessage(String to, String text) {
		if (text != null && text.length() > 0) {
			ChatMessage cm = new ChatMessage();
			cm.setFrom(EncryptionController.getIdentityUsername());
			cm.setTo(to);
			cm.setCipherText(text);
			mSendBuffer.add(cm);

			// keep track of the messages we sent
			// and remove them from the buffer when we receive them back from the server
			sendMessages();

		}
	}

	private void sendMessages() {
		if (mBackgroundTimer == null) {

			mBackgroundTimer = new Timer("backgroundTimer");
		}

		if (mResendTask != null) {
			mResendTask.cancel();
		}

		// mResendTask = new ResendTask();
		// mBackgroundTimer.schedule(mResendTask, 1000);
		Log.v(TAG, "Sending: " + mSendBuffer.size() + " messages.");

		Iterator<ChatMessage> iterator = mSendBuffer.iterator();

		while (iterator.hasNext()) {
			ChatMessage message = iterator.next();
			iterator.remove();
			mResendBuffer.add(message);
			if (getState() == STATE_CONNECTED) {
				socket.send(message.toJSONObject().toString());
			}
		}

	}

	public void disconnect() {
		Log.v(TAG, "disconnect.");
		socket.disconnect();
		// socket = null;

	}

	private synchronized int getState() {
		return mState;
	}

	private synchronized void setState(int state) {
		mState = state;
	}

	private ReconnectTask mReconnectTask;

	private class ReconnectTask extends TimerTask {

		@Override
		public void run() {
			Log.v(TAG, "Reconnect task run.");
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
		Log.v(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		Utils.putSharedPrefsString("unsentmessages", Utils.chatMessagesToJson(mResendBuffer).toString());

	}

	public void loadUnsentMessages() {
		String sUnsentMessages = Utils.getSharedPrefsString("unsentmessages");

		if (sUnsentMessages != null && !sUnsentMessages.isEmpty()) {
			Iterator<ChatMessage> iterator = Utils.jsonStringToChatMessages(sUnsentMessages).iterator();
			while (iterator.hasNext()) {
				mSendBuffer.add(iterator.next());
			}
			Log.v(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
		}

		Utils.putSharedPrefsString("unsentmessages", null);

	}

	public void destroy() {
		Log.v(TAG, "destroy.");
		if (mBackgroundTimer != null) {
			mBackgroundTimer.cancel();
		}
	//	SurespotApplication.getAppContext().unregisterReceiver(mConnectivityReceiver);
	}
}
