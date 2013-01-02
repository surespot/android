package com.twofours.surespot.ui.activities;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncCallback;

public class LoginActivity extends Activity {

	private Button loginButton;
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

		// debug
		/*
		 * SurespotApplication.getNetworkController().login("jb", "jb", new IAsyncNetworkResultCallback<Boolean>() {
		 * 
		 * @Override public void handleResponse(Boolean result) { if (result) { // go to friends
		 * SurespotApplication.getChatController().connect(new IConnectCallback() {
		 * 
		 * @Override public void connectStatus(boolean status) { if (status) LoginActivity.this.startActivity(new
		 * Intent(LoginActivity.this, MainActivity.class)); }
		 * 
		 * });
		 * 
		 * } } });
		 */
		// end debug

		this.loginButton = (Button) this.findViewById(R.id.bLogin);
		this.loginButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				login();

			}
		});

		EditText editText = (EditText) findViewById(R.id.etPassword);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					login();
					handled = true;
				}
				return handled;
			}

		});
	}

	private void login() {

		final String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
		String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			//TODO show progress
			
			SurespotApplication.getNetworkController().login(username, password, new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(Boolean result) {
					if (result) {
						// start main activity
						SurespotApplication.getUserData().setUsername(username);
						Intent intent = new Intent(LoginActivity.this, MainActivity.class);
						LoginActivity.this.startActivity(intent);
					}

				}

			});
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_sure_spot, menu);
		return true;
	}

}
