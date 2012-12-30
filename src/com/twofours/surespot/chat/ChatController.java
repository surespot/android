package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;

public class ChatController {

	
	private static final String TAG = "ChatController";
	private SocketIO socket;

	public void connect(final IConnectCallback callback) {

		Cookie cookie = SurespotApplication.getNetworkController().getCookie();
		//TODO handle no cookie
		//if (cookie == null) 
		try {
			socket = new SocketIO("http://192.168.10.68:3000");
			socket.addHeader("cookie", cookie.getName() + "=" + cookie.getValue());
		} catch (MalformedURLException e1) {
			// Auto-generated
			e1.printStackTrace();
		//	callback.connectStatus(false);
		}

		socket.connect(new IOCallback() {

			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					Log.v(TAG,"JSON Server said:" + json.toString(2));
				
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				Log.v(TAG,"Server said: " + data);
			}

			@Override
			public void onError(SocketIOException socketIOException) {
				Log.v(TAG,"an Error occured");
				socketIOException.printStackTrace();
			}

			@Override
			public void onDisconnect() {
				Log.v(TAG,"Connection terminated.");
			}

			@Override
			public void onConnect() {
				Log.v(TAG,"socket.io connection established");
				callback.connectStatus(true);

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				Log.v(TAG,"Server triggered event '" + event + "'");
				if (event.equals("notification")) {
					sendNotification((JSONObject) args[0]);
					return;
				}
				if (event.equals("friend")) {
					sendFriendAdded((String) args[0]);
					return;
				}
			}
		});

		
		// JSONObject j = new JSONObject();
		// //j.putOpt(name,
		// value)
		// socket.send()

	}
	
	private void sendNotification(JSONObject notification) {
		Intent intent = new Intent(SurespotConstants.EventFilters.NOTIFICATION_EVENT);
		intent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION, notification.toString());
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}
	
	private void sendFriendAdded(String friend) {
		Intent intent = new Intent(SurespotConstants.EventFilters.FRIEND_ADDED_EVENT);
		intent.putExtra(SurespotConstants.ExtraNames.FRIEND_ADDED, friend);
		LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);
	}

}
