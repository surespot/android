package com.twofours.surespot.activities;

import java.security.KeyPair;

import org.spongycastle.jce.interfaces.ECPublicKey;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.IConnectCallback;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.AsyncCallbackWrapper;

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
				// generate key pair
				// TODO make threaded and add progress
				KeyPair keyPair = SurespotApplication.getEncryptionController().generateKeyPair();
				if (keyPair != null) {

					final String username = ((EditText) SignupActivity.this.findViewById(R.id.etUsername)).getText()
							.toString();
					String password = ((EditText) SignupActivity.this.findViewById(R.id.etPassword)).getText()
							.toString();

					SurespotApplication.getNetworkController().addUser(username, password,
							EncryptionController.encodePublicKey((ECPublicKey) keyPair.getPublic()),
							new AsyncCallbackWrapper<Boolean, KeyPair>(keyPair) {

								@Override
								public void handleResponse(Boolean result) {
									if (result) {
										// save key pair now that we've created a user successfully
										// TODO add setkey pair method to encryption controller to not have to pass it
										// into the callback
										// and back into the encryption controller
										SurespotApplication.getEncryptionController().saveKeyPair(state);

										SurespotApplication.getUserData().setUsername(username);
										Intent intent = new Intent(SignupActivity.this, MainActivity.class);
										//intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
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
