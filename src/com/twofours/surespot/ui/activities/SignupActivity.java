package com.twofours.surespot.ui.activities;

import java.security.KeyPair;

import org.apache.http.client.HttpResponseException;
import org.spongycastle.jce.interfaces.ECPublicKey;

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
import com.twofours.surespot.SurespotIdentity;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.main.MainActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class SignupActivity extends Activity {
	private static final String TAG = "SignupActivity";
	private Button signupButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);

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

		if (!(username.length() > 0 && password.length() > 0)) { return; }

		// see if the user exists
		NetworkController.userExists(username, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String arg1) {
				if (arg1.equals("true")) {
					Toast.makeText(SignupActivity.this, "That username already exists, please choose another.", Toast.LENGTH_LONG).show();

				}
				else {
					final ProgressDialog progressDialog = new ProgressDialog(SignupActivity.this);
					progressDialog.setIndeterminate(true);
					progressDialog.setMessage("Generating Keys...");
					progressDialog.show();

					// generate key pair
					// TODO don't always regenerate if the signup was not
					// successful
					EncryptionController.generateKeyPair(new IAsyncCallback<KeyPair>() {

						@Override
						public void handleResponse(final KeyPair keyPair) {
							if (keyPair != null) {

								NetworkController.addUser(username, password,
										EncryptionController.encodePublicKey((ECPublicKey) keyPair.getPublic()),
										new AsyncHttpResponseHandler() {

											@Override
											public void onSuccess(int statusCode, String arg0) {

												progressDialog.dismiss();

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
													EncryptionController.saveIdentity(new SurespotIdentity(username, keyPair));

													// SurespotApplication.getUserData().setUsername(username);
													Intent intent = new Intent(SignupActivity.this, MainActivity.class);
													intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
													startActivity(intent);

													finish();
												}
												else {
													Log.e(TAG, "201 not returned on user create.");
												}

											}

											public void onFailure(Throwable arg0, String arg1) {
												progressDialog.dismiss();
												Log.e("SignupActivity", arg0.toString());

												if (arg0 instanceof HttpResponseException) {
													HttpResponseException error = (HttpResponseException) arg0;
													int statusCode = error.getStatusCode();
													if (statusCode == 409) {
														Toast.makeText(SignupActivity.this,
																"That username already exists, please choose another.", Toast.LENGTH_LONG)
																.show();
													}
													else {

														Toast.makeText(SignupActivity.this, "Error, could not create user.",
																Toast.LENGTH_SHORT).show();
													}
												}
											};

										});
							}
						}
					});

				}

			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_credential_management, menu);
		return true;
	}

}
