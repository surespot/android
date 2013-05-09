package com.twofours.surespot.activities;

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.ImportIdentityActivity;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.twofours.surespot.ui.MultiProgressDialog;

public class LoginActivity extends SherlockActivity {

	private Button loginButton;
	private static final String TAG = "LoginActivity";
	MultiProgressDialog mMpd;
	private List<String> mIdentityNames;
	private boolean mLoginAttempted;
	private boolean mCacheServiceBound;
	private Menu mMenuOverflow;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		Utils.configureActionBar(this, "login", "", false);

		SurespotLog.v(TAG, "binding cache service");
		Intent cacheIntent = new Intent(this, CredentialCachingService.class);
		bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);

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

	}

	@Override
	protected void onResume() {

		super.onResume();

		// set the identities

		Spinner spinner = (Spinner) findViewById(R.id.spinnerUsername);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.sherlock_spinner_item);
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

			}
		});

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
			SurespotLog.v(TAG, "caching service bound");
			CredentialCachingBinder binder = (CredentialCachingBinder) service;

			CredentialCachingService credentialCachingService = binder.getService();
			mCacheServiceBound = true;

			SurespotApplication.setCachingService(credentialCachingService);

			// if they've already clicked login, login
			if (mLoginAttempted) {
				mLoginAttempted = false;
				login();
				mMpd.decrProgress();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private class IdSig {
		public SurespotIdentity identity;
		public String signature;
		protected String derivedPassword;
	}

	private void login() {
		if (SurespotApplication.getCachingService() == null) {
			mLoginAttempted = true;
			mMpd.incrProgress();
			return;
		}

		final String username = mIdentityNames.get(((Spinner) LoginActivity.this.findViewById(R.id.spinnerUsername))
				.getSelectedItemPosition());
		final EditText pwText = (EditText) LoginActivity.this.findViewById(R.id.etPassword);

		final String password = pwText.getText().toString();

		if (username != null && username.length() > 0 && password != null && password.length() > 0) {
			mMpd.incrProgress();

			new AsyncTask<Void, Void, IdSig>() {

				@Override
				protected IdSig doInBackground(Void... params) {

					SurespotIdentity identity = IdentityController.getIdentity(LoginActivity.this, username, password);
					if (identity != null) {
						byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
						final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));
						IdSig idSig = new IdSig();
						idSig.identity = identity;
						idSig.signature = EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword);
						idSig.derivedPassword = dPassword;
						return idSig;
					}
					return null;
				}

				protected void onPostExecute(final IdSig idSig) {
					if (idSig != null) {

						NetworkController networkController = new NetworkController(LoginActivity.this, null);

						String referrers = Utils.getSharedPrefsString(LoginActivity.this, SurespotConstants.PrefNames.REFERRERS);
						networkController.login(username, idSig.derivedPassword, idSig.signature, referrers, new CookieResponseHandler() {
							@Override
							public void onSuccess(int responseCode, String arg0, Cookie cookie) {
								Utils.putSharedPrefsString(LoginActivity.this, SurespotConstants.PrefNames.REFERRERS, null);
								IdentityController.userLoggedIn(LoginActivity.this, idSig.identity, cookie);

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
										SurespotLog
												.v(TAG,
														"user has elected to login as a different user than the notification, removing relevant intent extras");
										newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
										newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
										newIntent.removeExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

										Utils.putSharedPrefsString(LoginActivity.this, SurespotConstants.PrefNames.LAST_CHAT, null);
									}
								}

								Utils.logIntent(TAG, newIntent);

								startActivity(newIntent);
								InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
								imm.hideSoftInputFromWindow(pwText.getWindowToken(), 0);
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
										Utils.makeToast(LoginActivity.this, "Error logging in, please try again later.");
									}
								}
								else {
									Utils.makeToast(LoginActivity.this, "Error logging in, please try again later.");
								}
								pwText.setText("");
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
						pwText.setText("");
					}

				};
			}.execute();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_login, menu);
		mMenuOverflow = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_import_identities_bar:
			new AsyncTask<Void, Void, Void>() {

				@Override
				protected Void doInBackground(Void... params) {
					Intent intent = new Intent(LoginActivity.this, ImportIdentityActivity.class);
					startActivity(intent);
					return null;
				}
			}.execute();
			return true;
		case R.id.menu_create_identity_bar:
			if (IdentityController.getIdentityCount(this) < SurespotConstants.MAX_IDENTITIES) {
				new AsyncTask<Void, Void, Void>() {

					@Override
					protected Void doInBackground(Void... params) {
						Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
						startActivity(intent);
						return null;

					}

				}.execute();
			}
			else {
				Utils.makeLongToast(this, "sorry, you have already created the maximum (" + SurespotConstants.MAX_IDENTITIES
						+ ") number of identities\n\nidentities can be deleted from the settings menu after logging in");
			}
			return true;

		case R.id.clear_local_cache_bar:
			new AsyncTask<Void, Void, Void>() {

				@Override
				protected Void doInBackground(Void... params) {
					StateController.clearCache(LoginActivity.this, new IAsyncCallback<Void>() {

						@Override
						public void handleResponse(Void result) {
							LoginActivity.this.runOnUiThread(new Runnable() {
								public void run() {
									Utils.makeToast(LoginActivity.this, "local cache cleared");
								};
							});

						}
					});
					return null;
				}
			}.execute();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mCacheServiceBound && mConnection != null) {
			unbindService(mConnection);
		}

	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			mMenuOverflow.performIdentifierAction(R.id.item_overflow, 0);
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}
}
