package com.twofours.surespot;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.R;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class Login extends Activity {
	private Button loginButton;
	private Button sayHelloButton;
	private SocketIO socket;
	//TODO put this behind a factory or singleton or something
	private AbstractHttpClient _httpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		_httpClient = new DefaultHttpClient();
		HttpConnectionParams.setConnectionTimeout(_httpClient.getParams(), 10000); // Timeout
																				// Limit
		
		this.loginButton = (Button) this.findViewById(R.id.bLogin);
		this.loginButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				Map<String, String> params = new HashMap<String, String>();
				params.put("username", ((EditText) Login.this
						.findViewById(R.id.etUsername)).getText().toString());
				params.put("password", ((EditText) Login.this
						.findViewById(R.id.etPassword)).getText().toString());

				AsyncHttpPost post = new AsyncHttpPost(_httpClient,
						"http://192.168.10.68:3000/login", params,
						new IAsyncHttpCallback() {

							@Override
							public void handleResponse(HttpResponse response) {
								
								/* Checking response */
								if (response.getStatusLine().getStatusCode() == 204) {
									Cookie cookie = null;
									for (Cookie c : (_httpClient).getCookieStore()
											.getCookies()) {
										System.out.println("Cookie name: "
												+ c.getName() + " value: "
												+ c.getValue());
										if (c.getName().equals("connect.sid")) {
											cookie = c;
											break;
										}
									}

									if (cookie == null) {
										System.out
												.println("did not get cookie from login");
										return;
									}
									try {
										socket = new SocketIO(
												"http://192.168.10.68:3000");
										socket.addHeader(
												"cookie",
												cookie.getName() + "="
														+ cookie.getValue());
									} catch (MalformedURLException e1) {
										// Auto-generated
										e1.printStackTrace();
									}

									socket.connect(new IOCallback() {

										@Override
										public void onMessage(JSONObject json,
												IOAcknowledge ack) {
											try {
												System.out.println("Server said:"
														+ json.toString(2));
											} catch (JSONException e) {
												e.printStackTrace();
											}
										}

										@Override
										public void onMessage(String data,
												IOAcknowledge ack) {
											System.out.println("Server said: "
													+ data);
										}

										@Override
										public void onError(
												SocketIOException socketIOException) {
											System.out
													.println("an Error occured");
											socketIOException.printStackTrace();
										}

										@Override
										public void onDisconnect() {
											System.out
													.println("Connection terminated.");
										}

										@Override
										public void onConnect() {
											System.out
													.println("socket.io connection established");

										}

										@Override
										public void on(String event,
												IOAcknowledge ack,
												Object... args) {
											System.out
													.println("Server triggered event '"
															+ event + "'");
										}
									});

									// JSONObject j = new JSONObject();
									// //j.putOpt(name,
									// value)
									// socket.send()

								}

							}
						});
				post.execute();

			}
		});

		/*
		 * this.sayHelloButton = (Button) this.findViewById(R.id.bSayHello);
		 * this.sayHelloButton.setOnClickListener(new View.OnClickListener() {
		 * 
		 * @Override public void onClick(View v) { // send a message JSONObject
		 * json = new JSONObject();
		 * 
		 * try { json.putOpt("room", "adam_cherie"); json.putOpt("text",
		 * "hello from android"); socket.emit("message", json.toString()); }
		 * catch (JSONException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 * 
		 * } });
		 */
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_sure_spot, menu);
		return true;
	}

}
