package com.twofours.surespot.ui.activities;

import java.security.KeyPair;

import org.spongycastle.jce.interfaces.ECPublicKey;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.AsyncCallbackWrapper;
import com.twofours.surespot.network.IAsyncCallback;

public class SignupActivity extends Activity {

	private Button signupButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);

		this.signupButton = (Button) this.findViewById(R.id.bSignup);
		this.signupButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				signup();
			}
		});

		EditText editText = (EditText) findViewById(R.id.etSignupPassword);
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
		final ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setIndeterminate(true);
		progressDialog.setMessage("initializing...");
		progressDialog.show();
		
		// generate key pair		
		SurespotApplication.getEncryptionController().generateKeyPair(new IAsyncCallback<KeyPair>() {

			@Override
			public void handleResponse(KeyPair keyPair) {				
				if (keyPair != null) {

					final String username = ((EditText) SignupActivity.this.findViewById(R.id.etSignupUsername)).getText()
							.toString();
					String password = ((EditText) SignupActivity.this.findViewById(R.id.etSignupPassword)).getText()
							.toString();

					SurespotApplication.getNetworkController().addUser(username, password,
							EncryptionController.encodePublicKey((ECPublicKey) keyPair.getPublic()),
							new AsyncCallbackWrapper<Boolean, KeyPair>(keyPair) {

								@Override
								public void handleResponse(Boolean result) {
									progressDialog.dismiss();
									if (result) {
										// save key pair now that we've created a user successfully
										// TODO add setkey pair method to encryption controller to not have to pass it
										// into the callback
										// and back into the encryption controller
										SurespotApplication.getEncryptionController().saveKeyPair(state);

										
										
										SurespotApplication.getUserData().setUsername(username);
										Intent intent = new Intent(SignupActivity.this, MainActivity.class);
										intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
										startActivity(intent);
										
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
