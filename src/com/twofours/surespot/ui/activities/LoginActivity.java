package com.twofours.surespot.ui.activities;

import org.apache.http.client.HttpResponseException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.encryption.EncryptionController;
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
		usernameText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });
		String username = EncryptionController.getIdentityUsername();
		if (username != null) {
			usernameText.setText(username);
		}
		else {
			Log.w(TAG, "In login activity with no identity stored.");
		}

	}

	private void login() {
		final ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setIndeterminate(true);
		progressDialog.setMessage("Logging in...");
		progressDialog.show();

		final String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
		String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			NetworkController.login(username, password, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int responseCode, String arg0) {
					progressDialog.dismiss();
					nextActivity();
				}

				@Override
				public void onFailure(Throwable arg0, String message) {
					progressDialog.dismiss();

					Log.e(TAG, arg0.toString());

					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						if (statusCode == 401) {
							Toast.makeText(LoginActivity.this, "Could not login, please make sure your password is correct.",
									Toast.LENGTH_SHORT).show();
						}
						else {
							Toast.makeText(LoginActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
						}
					}
				}
			});
		}
	}

	private void nextActivity() {
		// if we have a chat name, we may have started from a
		// message, so in that case
		// go straight to the chat now we've logged in
		String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
		if (name == null) {
			Intent intent = new Intent(LoginActivity.this, MainActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			LoginActivity.this.startActivity(intent);
		}
		else {

			Intent intent = new Intent(LoginActivity.this, ChatActivity.class);
			intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			LoginActivity.this.startActivity(intent);

		}
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_sure_spot, menu);
		return true;
	}

}
