package io.socket;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import com.twofours.surespot.common.SurespotLog;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

public class WebsocketTransport implements IOTransport, WebSocketConnectionObserver {

	WebSocketConnection websocket;
	private final static Pattern PATTERN_HTTP = Pattern.compile("^http");
	public static final String TRANSPORT_NAME = "websocket";
	private static final String TAG = "WebsocketTransport";
	private IOConnection connection;
	private URI mUri;

	public static IOTransport create(URL url, IOConnection connection) {
		URI uri = URI.create(PATTERN_HTTP.matcher(url.toString()).replaceFirst("ws") + IOConnection.SOCKET_IO_1 + TRANSPORT_NAME + "/"
				+ connection.getSessionId());

		return new WebsocketTransport(uri, connection);
	}

	public WebsocketTransport(URI uri, IOConnection connection) {
		mUri = uri;
		websocket = new WebSocketConnection();
//	websocket.connect(uri, this);
		this.connection = connection;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.socket.IOTransport#disconnect()
	 */
	@Override
	public void disconnect() {
		try {
			websocket.disconnect();
		}
		catch (Exception e) {
			connection.transportError(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.socket.IOTransport#canSendBulk()
	 */
	@Override
	public boolean canSendBulk() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.socket.IOTransport#sendBulk(java.lang.String[])
	 */
	@Override
	public void sendBulk(String[] texts) throws IOException {
		throw new RuntimeException("Cannot send Bulk!");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.socket.IOTransport#invalidate()
	 */
	@Override
	public void invalidate() {
		connection = null;
	}

	@Override
	public void onOpen() {
		if (connection != null)
			connection.transportConnected();
	}

	@Override
	public String getName() {
		return TRANSPORT_NAME;
	}

	@Override
	public void connect() {
		try {
			websocket.connect(mUri, this);
		}
		catch (Exception e) {
			connection.transportError(e);
		}

	}

	@Override
	public void send(String text) throws Exception {
		websocket.sendTextMessage(text);

	}

	@Override
	public void onClose(WebSocketCloseNotification code, String reason) {
		SurespotLog.v(TAG, "onClose, code: " + code + ", reason: " + reason);
		if (code != WebSocketCloseNotification.RECONNECT) {
		
			if (connection != null)
				connection.transportDisconnected();
			
			connection.transportError(new Exception("disconnected"));
		}

	}

	@Override
	public void onTextMessage(String payload) {
		if (connection != null)
			connection.transportMessage(payload);

	}

	@Override
	public void onRawTextMessage(byte[] payload) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBinaryMessage(byte[] payload) {
		// TODO Auto-generated method stub

	}

}