package com.twofours.surespot.activities;

import java.security.KeyPair;

import org.spongycastle.jce.interfaces.ECPublicKey;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.ImportIdentityActivity;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.MultiProgressDialog;

public class SignupActivity extends SherlockActivity {
	private static final String TAG = "SignupActivity";
	private Button signupButton;
	private MultiProgressDialog mMpd;
	private boolean mSignupAttempted;
	private boolean mCacheServiceBound;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);
		Utils.configureActionBar(this, "identity", "create", false);

		SurespotLog.v(TAG, "binding cache service");
		Intent cacheIntent = new Intent(this, CredentialCachingService.class);
		bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);

		mMpd = new MultiProgressDialog(this, "creating a user and generating keys", 250);

		EditText editText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH),
				new LetterOrDigitInputFilter() });

		this.signupButton = (Button) this.findViewById(R.id.bSignup);
		this.signupButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				signup();
			}
		});

		
		editText = (EditText) findViewById(R.id.etSignupPassword);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });
		
		editText = (EditText) findViewById(R.id.etSignupPasswordConfirm);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					signup();
					handled = true;
				}
				return handled;
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
			if (mSignupAttempted) {
				mSignupAttempted = false;
				signup();
				mMpd.decrProgress();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void signup() {
		if (SurespotApplication.getCachingService() == null) {
			mSignupAttempted = true;
			mMpd.incrProgress();
			return;
		}

		final EditText userText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
		final String username = userText.getText().toString();

		final EditText pwText = (EditText) SignupActivity.this.findViewById(R.id.etSignupPassword);
		final String password = pwText.getText().toString();
		
		final EditText confirmPwText = (EditText) SignupActivity.this.findViewById(R.id.etSignupPasswordConfirm);
		String confirmPassword = confirmPwText.getText().toString();
		
		
		if (!(username.length() > 0 && password.length() > 0 && confirmPassword.length() > 0)) {
			return;
		}
		
		if (!confirmPassword.equals(password)) {
			Utils.makeToast(this, "passwords do not match");
			return;
		}

		mMpd.incrProgress();

		// see if the user exists
		final NetworkController networkController = new NetworkController(SignupActivity.this, null);
		networkController.userExists(username, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String arg1) {
				if (arg1.equals("true")) {
					Utils.makeToast(SignupActivity.this, "That username already exists, please choose another.");
					pwText.setText("");
					mMpd.decrProgress();
				}
				else {
					final String dPassword = EncryptionController.derivePassword(password);
					// generate key pair
					// TODO don't always regenerate if the signup was not
					// successful
					EncryptionController.generateKeyPairs(new IAsyncCallback<KeyPair[]>() {

						@Override
						public void handleResponse(final KeyPair[] keyPair) {
							if (keyPair != null) {

								// TODO use password derived from user's password
								// get the publick keys

								new AsyncTask<Void, Void, String[]>() {
									protected String[] doInBackground(Void... params) {

										String[] data = new String[3];
										data[0] = EncryptionController.encodePublicKey((ECPublicKey) keyPair[0].getPublic());
										data[1] = EncryptionController.encodePublicKey((ECPublicKey) keyPair[1].getPublic());
										data[2] = EncryptionController.sign(keyPair[1].getPrivate(), username, dPassword);
										return data;
									}

									protected void onPostExecute(String[] result) {
										String sPublicDH = result[0];
										String sPublicECDSA = result[1];
										String signature = result[2];

										String referrers = Utils.getSharedPrefsString(SignupActivity.this, SurespotConstants.PrefNames.REFERRERS);
										
										networkController.addUser(username, dPassword, sPublicDH, sPublicECDSA, signature, referrers,
												new CookieResponseHandler() {

													@Override
													public void onSuccess(int statusCode, String arg0, final Cookie cookie) {
														pwText.setText("");

														if (statusCode == 201) {
															// save key pair now
															// that we've created
															// a
															// user successfully
															// TODO add setkey pair
															// method to
															// encryption
															// controller to not
															// have to pass it
															// into the callback
															// and back into the
															// encryption
															// controller
															new AsyncTask<Void, Void, Void>() {

																@Override
																protected Void doInBackground(Void... params) {
																	Utils.putSharedPrefsString(SignupActivity.this, SurespotConstants.PrefNames.REFERRERS, null);
																	IdentityController.createIdentity(SignupActivity.this, username,
																			password, keyPair[0], keyPair[1], cookie);
																	return null;
																}

																protected void onPostExecute(Void result) {

																	// SurespotApplication.getUserData().setUsername(username);
																	Intent intent = new Intent(SignupActivity.this, MainActivity.class);
																	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
																			| Intent.FLAG_ACTIVITY_NEW_TASK);
																	startActivity(intent);
																	mMpd.decrProgress();
																	InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
																	imm.hideSoftInputFromWindow(pwText.getWindowToken(), 0);
																	finish();
																};
															}.execute();

														}
														else {
															SurespotLog.w(TAG, "201 not returned on user create.");
															pwText.setText("");
														}

													}

													public void onFailure(Throwable arg0, String arg1) {
														SurespotLog.e(TAG, arg0, "signup: %s", arg1);
														mMpd.decrProgress();
														if (arg0 instanceof HttpResponseException) {
															HttpResponseException error = (HttpResponseException) arg0;
															int statusCode = error.getStatusCode();
															if (statusCode == 409) {
																Utils.makeToast(SignupActivity.this,
																		"that username already exists, please choose another");
															}
															else {

																Utils.makeToast(SignupActivity.this,
																		"could not create user, please try again later");
															}
														}
														else {
															Utils.makeToast(SignupActivity.this,
																	"could not create user, please try again later");
														}
														pwText.setText("");

													}

												});
									};
								}.execute();

							}
						}
					});

				}

			}

			@Override
			public void onFailure(Throwable error, String content) {
				SurespotLog.w(TAG, "userExists", error);
				mMpd.decrProgress();
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mCacheServiceBound && mConnection != null) {
			unbindService(mConnection);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_signup, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();

			return true;
		case R.id.menu_import_identities:
			Intent intent = new Intent(this, ImportIdentityActivity.class);
			intent.putExtra("signup", true);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

}
