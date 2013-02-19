package com.twofours.surespot.activities;

import java.util.List;

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
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.CookieResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotIdentity;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.FriendActivity;

public class LoginActivity extends SherlockActivity {

	private Button loginButton;
	private static final String TAG = "LoginActivity";
	MultiProgressDialog mMpd;
	private List<String> mIdentityNames;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		// if we're starting up from network controller because of 401 pull the intent out
		Intent intent = getIntent();
		Boolean is401 = intent.getBooleanExtra("401", false);

		if (is401) {
			SurespotLog.v(TAG, "using startup intent due to 401");
			setIntent(SurespotApplication.getStartupIntent());
		}

		Utils.logIntent(TAG, getIntent());

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

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);

		// select last user if there was one
		String to = getIntent().getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
		if (to == null) {
			to = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.LAST_USER);
		}

		if (to != null && mIdentityNames.contains(to)) {
			spinner.setSelection(adapter.getPosition(to));
		}

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
	}

	private void login() {

		final String username = mIdentityNames.get(((Spinner) LoginActivity.this.findViewById(R.id.spinnerUsername))
				.getSelectedItemPosition());
		// final String username = ((EditText) LoginActivity.this.findViewById(R.id.etUsername)).getText().toString();
		final String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			mMpd.incrProgress();

			SurespotIdentity identity = IdentityController.getIdentity(this, username, password);
			
			#TODO thread
			String signature = EncryptionController.sign(identity.getKeyPairECDSA().getPrivate(), username, password);
			SurespotApplication.getNetworkController().login(username, password, signature, new CookieResponseHandler() {
				@Override
				public void onSuccess(int responseCode, String arg0, Cookie cookie) {
					IdentityController.userLoggedIn(LoginActivity.this, username, password, cookie);
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
		String notificationType = getIntent().getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {
			Intent intent = new Intent(this, ChatActivity.class);
			intent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, getIntent()
					.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			this.startActivity(intent);
		}
		else {
			Intent intent = new Intent(this, FriendActivity.class);
			String action = getIntent().getAction();
			String type = getIntent().getType();

			if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
				intent.setAction(action);
				intent.setType(type);
				intent.putExtras(getIntent());
			}
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			this.startActivity(intent);
		}

		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_import_identities:
			Intent intent = new Intent(this, SignupActivity.class);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

}
