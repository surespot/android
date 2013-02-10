package com.twofours.surespot.activities;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.NetworkController;

public class LoginActivity extends Activity {

	private Button loginButton;
	private static final String TAG = "LoginActivity";
	MultiProgressDialog mMpd;
	private List<String> mIdentityNames;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		mMpd = new MultiProgressDialog(this, "logging in", 750);

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

		// set the identities

		Spinner spinner = (Spinner) findViewById(R.id.spinnerUsername);

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);

		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				((EditText) LoginActivity.this.findViewById(R.id.etPassword)).setText("");

			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO Auto-generated method stub

			}
		});

		// EditText usernameText = (EditText) findViewById(R.id.etUsername);
		// usernameText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });
		// String username = EncryptionController.getIdentityUsername();
		// if (username != null) {
		// usernameText.setText(username);
		// }
		// else {
		// SurespotLog.w(TAG, "In login activity with no identity stored.");
		// }

	}

	private void login() {

		final String username = mIdentityNames.get(((Spinner) LoginActivity.this.findViewById(R.id.spinnerUsername))
				.getSelectedItemPosition());
		// final String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
		String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			mMpd.incrProgress();

			NetworkController.login(username, password, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int responseCode, String arg0) {

					nextActivity();
				}

				@Override
				public void onFailure(Throwable arg0, String message) {
					SurespotLog.w(TAG, arg0.toString(), arg0);

					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						if (statusCode == 401) {
							Utils.makeToast(LoginActivity.this, "Could not login, please make sure your password is correct.");
						}
						else {
							Utils.makeToast(LoginActivity.this, "Error: " + message);
						}
					}
					else {
						Utils.makeToast(LoginActivity.this, "Error logging in, please try again later.");
					}
				}

				@Override
				public void onFinish() {
					mMpd.decrProgress();
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
			Intent intent = new Intent(LoginActivity.this, FriendActivity.class);
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

}
