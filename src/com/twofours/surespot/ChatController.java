package com.twofours.surespot;

import java.net.MalformedURLException;

import org.apache.http.cookie.Cookie;
import org.json.JSONException;
import org.json.JSONObject;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

public class ChatController {
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
					System.out.println("Server said:" + json.toString(2));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				System.out.println("Server said: " + data);
			}

			@Override
			public void onError(SocketIOException socketIOException) {
				System.out.println("an Error occured");
				socketIOException.printStackTrace();
			}

			@Override
			public void onDisconnect() {
				System.out.println("Connection terminated.");
			}

			@Override
			public void onConnect() {
				System.out.println("socket.io connection established");
				callback.connectStatus(true);

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				System.out.println("Server triggered event '" + event + "'");
			}
		});

		// JSONObject j = new JSONObject();
		// //j.putOpt(name,
		// value)
		// socket.send()

	}

}
