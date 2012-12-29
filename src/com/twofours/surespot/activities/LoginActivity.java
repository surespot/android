package com.twofours.surespot.activities;

import io.socket.SocketIO;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.IConnectCallback;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class LoginActivity extends Activity {

	private Button loginButton;
	private Button sayHelloButton;
	// TODO put this behind a factory or singleton or something
	private AbstractHttpClient _httpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		// TODO use HttpURLConnection (http://android-developers.blogspot.com/2011/09/androids-http-clients.html)
		// create thread safe http client
		// (http://foo.jasonhudgins.com/2010/03/http-connections-revisited.html)
		_httpClient = new DefaultHttpClient();
		ClientConnectionManager mgr = _httpClient.getConnectionManager();
		HttpParams params = _httpClient.getParams();
		_httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(params, mgr.getSchemeRegistry()), params);
		HttpConnectionParams.setConnectionTimeout(_httpClient.getParams(), 10000); // Timeout
																					// Limit

		//debug
		SurespotApplication.getNetworkController().login("jb", "jb",
				new IAsyncNetworkResultCallback<Boolean>() {

					@Override
					public void handleResponse(Boolean result) {
						if (result) {
							// go to friends
							SurespotApplication.getChatController().connect(new IConnectCallback() {

								@Override
								public void connectStatus(boolean status) {
									if (status)
										LoginActivity.this.startActivity(new Intent(LoginActivity.this,
												MainActivity.class));
								}

							});

						}
					}
				});
		//end debug
		
		this.loginButton = (Button) this.findViewById(R.id.bLogin);
		this.loginButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
				String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

				SurespotApplication.getNetworkController().login(username, password,
						new IAsyncNetworkResultCallback<Boolean>() {

							@Override
							public void handleResponse(Boolean result) {
								if (result) {
									// go to friends
									SurespotApplication.getChatController().connect(new IConnectCallback() {

										@Override
										public void connectStatus(boolean status) {
											if (status)
												LoginActivity.this.startActivity(new Intent(LoginActivity.this,
														MainActivity.class));
										}

									});

								}
							}
						});

			}
		});

		/*
		 * this.sayHelloButton = (Button) this.findViewById(R.id.bSayHello); this.sayHelloButton.setOnClickListener(new
		 * View.OnClickListener() {
		 * 
		 * @Override public void onClick(View v) { // send a message JSONObject json = new JSONObject();
		 * 
		 * try { json.putOpt("room", "adam_cherie"); json.putOpt("text", "hello from android"); socket.emit("message",
		 * json.toString()); } catch (JSONException e) { // TODO Auto-generated catch block e.printStackTrace(); }
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
