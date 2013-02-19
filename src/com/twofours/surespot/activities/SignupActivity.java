package com.twofours.surespot.activities;

import java.security.KeyPair;

import org.spongycastle.jce.interfaces.ECPublicKey;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
import com.twofours.surespot.CookieResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.IAsyncCallback;

public class SignupActivity extends SherlockActivity {
	private static final String TAG = "SignupActivity";
	private Button signupButton;
	private MultiProgressDialog mMpd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);
		mMpd = new MultiProgressDialog(this, "creating a user and generating keys", 750);

		EditText editText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
		editText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });

		this.signupButton = (Button) this.findViewById(R.id.bSignup);
		this.signupButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				signup();
			}
		});

		editText = (EditText) findViewById(R.id.etSignupPassword);
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

	private void signup() {
		final String username = ((EditText) SignupActivity.this.findViewById(R.id.etSignupUsername)).getText().toString();

		// TODO use char array
		final String password = ((EditText) SignupActivity.this.findViewById(R.id.etSignupPassword)).getText().toString();

		if (!(username.length() > 0 && password.length() > 0)) {
			return;
		}

		mMpd.incrProgress();
		// see if the user exists
		SurespotApplication.getNetworkController().userExists(username, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String arg1) {
				if (arg1.equals("true")) {
					Utils.makeToast(SignupActivity.this, "That username already exists, please choose another.");
					mMpd.decrProgress();
				}
				else {

					// generate key pair
					// TODO don't always regenerate if the signup was not
					// successful
					EncryptionController.generateKeyPairs(new IAsyncCallback<KeyPair[]>() {

						@Override
						public void handleResponse(final KeyPair[] keyPair) {
							if (keyPair != null) {

								// TODO use password derived from user's password
								// get the publick keys

								String sPublicDH = EncryptionController.encodePublicKey((ECPublicKey) keyPair[0].getPublic());
								String sPublicECDSA = EncryptionController.encodePublicKey((ECPublicKey) keyPair[1].getPublic());

								String signThis = username + password + sPublicDH;
								final String signature = EncryptionController.sign(keyPair[1].getPrivate(), username);

								SurespotApplication.getNetworkController().addUser(username, password, sPublicDH, sPublicECDSA, signature,
										new CookieResponseHandler() {

											@Override
											public void onSuccess(int statusCode, String arg0, Cookie cookie) {

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
													IdentityController.createIdentity(SignupActivity.this, username, password, keyPair[0],
															keyPair[1], signature, cookie);
													// SurespotApplication.getUserData().setUsername(username);
													Intent intent = new Intent(SignupActivity.this, FriendActivity.class);
													intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
													startActivity(intent);
													mMpd.decrProgress();
													finish();
												}
												else {
													SurespotLog.w(TAG, "201 not returned on user create.");
												}

											}

											public void onFailure(Throwable arg0, String arg1) {
												SurespotLog.e("SignupActivity", arg1, arg0);
												mMpd.decrProgress();
												if (arg0 instanceof HttpResponseException) {
													HttpResponseException error = (HttpResponseException) arg0;
													int statusCode = error.getStatusCode();
													if (statusCode == 409) {
														Utils.makeToast(SignupActivity.this,
																"That username already exists, please choose another.");
													}
													else {

														Utils.makeToast(SignupActivity.this,
																"Could not create user, please try again later.");
													}
												}
												else {
													Utils.makeToast(SignupActivity.this, "Could not create user, please try again later.");
												}

											}

										});
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
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_signup, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
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
