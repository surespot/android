package com.twofours.surespot.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.main.MainActivity;
import com.twofours.surespot.network.NetworkController;

public class LoginActivity extends Activity {

	private Button loginButton;
	private static final String TAG = "LoginActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

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

		// set the username
		EditText usernameText = (EditText) findViewById(R.id.etUsername);
		String username = SurespotApplication.getEncryptionController().getIdentityUsername();
		if (username != null) {
			usernameText.setText(username);
		}
		else {
			Log.w(TAG,"In login activity with no identity stored.");
		}

	}

	private void login() {

		final String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
		String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			// TODO show progress

			NetworkController.login(username, password, new AsyncHttpResponseHandler() {

				@Override
				public void onSuccess(String arg0) {
					// start main activity
					SurespotApplication.getUserData().setUsername(username);
					Intent intent = new Intent(LoginActivity.this, MainActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					LoginActivity.this.startActivity(intent);

					finish();

				}

				@Override
				public void onFailure(Throwable arg0) {
					Toast.makeText(LoginActivity.this, "Login Error", Toast.LENGTH_SHORT).show();
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
