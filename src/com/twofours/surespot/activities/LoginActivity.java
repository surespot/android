package com.twofours.surespot.activities;

import java.util.List;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
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
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotIdentity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;

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
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					login();
					handled = false;
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
			to = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_USER);
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
		final String password = ((EditText) LoginActivity.this.findViewById(R.id.etPassword)).getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			mMpd.incrProgress();

			new AsyncTask<Void, Void, String>() {

				@Override
				protected String doInBackground(Void... params) {
					SurespotIdentity identity = IdentityController.getIdentity(LoginActivity.this, username, password);
					if (identity != null) {
						return EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, password);
					}
					return null;
				}

				protected void onPostExecute(String signature) {
					if (signature != null) {
						MainActivity.getNetworkController().login(username, password, signature, new CookieResponseHandler() {
							@Override
							public void onSuccess(int responseCode, String arg0, Cookie cookie) {
								IdentityController.userLoggedIn(LoginActivity.this, username, password, cookie);

								Intent intent = getIntent();
								Intent newIntent = new Intent(LoginActivity.this, MainActivity.class);
								newIntent.setAction(intent.getAction());
								newIntent.setType(intent.getType());
								Bundle extras = intent.getExtras();
								if (extras != null) {
									newIntent.putExtras(extras);
								}

								// if we logged in as someone else, remove the notification intent extras as we are no longer special
								// we are just an ordinary login now with no magical powers
								String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
								if (notificationType != null) {
									String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
									if (!messageTo.equals(username)) {
										SurespotLog.v(TAG,"user has elected to login as a different user than the notification, removing relevant intent extras");
										newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
										newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
										newIntent.removeExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
									}
								}
								
								Utils.logIntent(TAG, newIntent);

								startActivity(newIntent);
								finish();

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
					else {
						mMpd.decrProgress();
						Utils.makeToast(LoginActivity.this, "Could not login, please make sure your password is correct.");

					}

				};
			}.execute();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;
		switch (item.getItemId()) {

		case R.id.menu_import_identities:
		case R.id.menu_import_identities_bar:
			intent = new Intent(this, ImportIdentityActivity.class);
			startActivity(intent);
			return true;
		case R.id.menu_create_identity:
		case R.id.menu_create_identity_bar:
			intent = new Intent(this, SignupActivity.class);
			startActivity(intent);
			return true;
		case R.id.clear_local_cache:
		case R.id.clear_local_cache_bar:
			StateController.clearCache(LoginActivity.this, new IAsyncCallback<Void>() {

				@Override
				public void handleResponse(Void result) {
					Utils.makeToast(LoginActivity.this, "local cache cleared");
				}
			});
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

}
