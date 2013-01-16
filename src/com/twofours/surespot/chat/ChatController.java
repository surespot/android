package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.ui.activities.StartupActivity;

public class ChatController {

	private static final String TAG = "ChatController";
	private static SocketIO socket;
	private static final int MAX_RETRIES = 5;
	private static int mRetries = 0;
	private static Timer mBackgroundTimer;
	private static TimerTask mResendTask;

	private static ConcurrentLinkedQueue<String> mMessageBuffer = new ConcurrentLinkedQueue<String>();
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;

	private static int mState;

	//
	static {
		SurespotApplication.getAppContext().registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.v(TAG, "Connectivity Action");
				ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
				if (networkInfo != null) {
					Log.v(TAG, "isconnected: " + networkInfo.isConnected());
					Log.v(TAG, "failover: " + networkInfo.isFailover());
					Log.v(TAG, "reason: " + networkInfo.getReason());
					Log.v(TAG, "type: " + networkInfo.getTypeName());

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover()
							&& (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						// if we're not connecting, connect
						if (!(getState() == STATE_CONNECTING)) {
							setState(STATE_CONNECTING);
							disconnect();
							connect(new IConnectCallback() {

								@Override
								public void connectStatus(boolean status) {
									if (true) {
										sendNextMessage();
									}

								}
							});
						}
					}
				} else {
					Log.v(TAG, "networkinfo null");
				}
			}
		}, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public static void connect(final IConnectCallback callback) {

		if (socket != null && socket.isConnected()) {
			return;
		}

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

		} catch (MalformedURLException e1) {
			// Auto-generated
			e1.printStackTrace();
			// callback.connectStatus(false);
		}

		socket.connect(new IOCallback() {

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

					/*
					 * Toast.makeText(SurespotApplication.getAppContext(),
					 * "Can not connect to chat server. Please check your network and try again.",
					 * Toast.LENGTH_LONG).show(); //TODO tie in with network controller 401 handling Intent intent = new
					 * Intent(SurespotApplication.getAppContext(), LoginActivity.class);
					 * intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					 * SurespotApplication.getAppContext().startActivity(intent);
					 */
				}
			}

			@Override
			public void onDisconnect() {
				Log.v(TAG, "Connection terminated.");
			}

			@Override
			public void onConnect() {
				Log.v(TAG, "socket.io connection established");
				mRetries = 0;
				if (mBackgroundTimer != null) {
					mBackgroundTimer.cancel();
					mBackgroundTimer = null;
				}

				if (callback != null) {
					setState(STATE_CONNECTED);
					callback.connectStatus(true);

				}

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				Log.v(TAG, "Server triggered event '" + event + "'");

				if (event.equals("notification")) {
					JSONObject json = (JSONObject) args[0];
					try {
						sendNotification(json.getString("data"));
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return;
				}
				if (event.equals("friend")) {
					sendFriendAdded((String) args[0]);
					return;
				}
				if (event.equals("message")) {
					sendMessageReceived((String) args[0]);
					// TODO check who from
					checkAndSendNextMessage((String) args[0]);
				}
			}
		});
	}

	private static void sendNotification(String friend) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.INVITATION_INTENT);
		intent.putExtra(SurespotConstants.ExtraNames.INVITATION, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private static void sendFriendAdded(String friend) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.FRIEND_ADDED_EVENT);
		intent.putExtra(SurespotConstants.ExtraNames.FRIEND_ADDED, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

	private static void sendMessageReceived(String message) {
		Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED_EVENT);
		intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

	}

	private static void checkAndSendNextMessage(String message) {
		Log.v(TAG, "received message: " + message);
		if (mMessageBuffer.size() > 0) {
			String sentMessage = mMessageBuffer.peek();
			Log.v(TAG, "message we sent: " + sentMessage);

			// TODO deserialize and check fields (id is added so can't do equals)
			if (message.startsWith(sentMessage.substring(0, sentMessage.length() - 1))) {

				mMessageBuffer.remove();
				Log.v(TAG, "Messages equal, sending next message in buffer.");
				sendNextMessage();

			} else {
				// Log.e(TAG,"didn't receive same message we sent.");
			}
		}
	}

	// private static void sendConnectionStatusChanged(boolean status) {
	// Intent intent = new Intent(SurespotConstants.IntentFilters.MESSAGE_RECEIVED_EVENT);
	// intent.putExtra(SurespotConstants.ExtraNames.MESSAGE, message);
	// LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	//
	// }

	public static void sendMessage(String to, String text) {
		if (text != null && text.length() > 0) {
			JSONObject message = new JSONObject();
			try {
				message.put("text", text);
				message.put("to", to);
				message.put("from", EncryptionController.getIdentityUsername());				
				String sMessage = message.toString();

				mMessageBuffer.add(sMessage);
				// if there's no messages in the buffer
				if (mMessageBuffer.size() == 1) {

					// keep track of the messages we sent
				 	// and remove them from the buffer when we receive them back from the server
					sendNextMessage();
				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private static void sendNextMessage() {
		if (mBackgroundTimer == null) {

			mBackgroundTimer = new Timer("backgroundTimer");
		}

		if (mResendTask != null) {
			mResendTask.cancel();
		}

		if (getState() == STATE_CONNECTED) {
			mResendTask = new ResendTask();
			mBackgroundTimer.schedule(mResendTask, 1000);

			if (mMessageBuffer.size() > 0) {
				socket.send(mMessageBuffer.peek());
			}
		}
	}

	public static void disconnect() {
		socket.disconnect();

	}

	public static synchronized int getState() {
		return mState;
	}

	public static synchronized void setState(int state) {
		mState = state;
	}

	private static ReconnectTask mReconnectTask;

	private static class ReconnectTask extends TimerTask {

		@Override
		public void run() {
			Log.v(TAG, "Reconnect task run.");
			disconnect();
			connect(new IConnectCallback() {

				@Override
				public void connectStatus(boolean status) {

					sendNextMessage();

				}
			});

		}

	}

	private static class ResendTask extends TimerTask {

		@Override
		public void run() {
			// resend message
			sendNextMessage();

		}

	}
}
