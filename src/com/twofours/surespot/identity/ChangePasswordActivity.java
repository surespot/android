package com.twofours.surespot.identity;

import java.security.PrivateKey;
import java.util.List;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.ui.MultiProgressDialog;

public class ChangePasswordActivity extends SherlockActivity {
	private static final String TAG = null;
	private List<String> mIdentityNames;
	private MultiProgressDialog mMpd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_change_password);
		Utils.configureActionBar(this, "change", "password", true);

		mMpd = new MultiProgressDialog(this, "changing password", 500);

		final Spinner spinner = (Spinner) findViewById(R.id.identitySpinner);

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);
		spinner.setSelection(adapter.getPosition(IdentityController.getLoggedInUser()));

		final EditText etCurrent = (EditText) this.findViewById(R.id.etChangePasswordCurrent);
		etCurrent.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

		final EditText etNew = (EditText) findViewById(R.id.etChangePasswordNew);
		etNew.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

		final EditText etConfirm = (EditText) findViewById(R.id.etChangePasswordConfirm);
		etConfirm.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

		Button changePasswordButton = (Button) findViewById(R.id.bChangePassword);

		changePasswordButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO progress
				final String user = (String) spinner.getSelectedItem();

				changePassword(user, etCurrent.getText().toString(), etNew.getText().toString(), etConfirm.getText().toString());

			}
		});
	}

	private void changePassword(final String username, final String currentPassword, final String newPassword, final String confirmPassword) {
		if (!(username.length() > 0 && currentPassword.length() > 0 && newPassword.length() > 0 && confirmPassword.length() > 0)) {			
			return;
		}

		if (!confirmPassword.equals(newPassword)) {
			resetFields();
			Utils.makeToast(this, "passwords do not match");
			return;
		}
		
		mMpd.incrProgress();
		SurespotIdentity identity = IdentityController.getIdentity(this, username, currentPassword);

		if (identity == null) {
			mMpd.decrProgress();
			Utils.makeLongToast(ChangePasswordActivity.this, "could not change password");
			return;
		}

		final String version = identity.getLatestVersion();
		final PrivateKey pk = identity.getKeyPairDSA().getPrivate();

		// create auth sig
		final String dPassword = EncryptionController.derivePassword(currentPassword);

		final String authSignature = EncryptionController.sign(pk, username, dPassword);
		SurespotLog.v(TAG, "generatedAuthSig: " + authSignature);

		// get a key update token from the server
		MainActivity.getNetworkController().getPasswordToken(username, dPassword, authSignature, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, final String passwordToken) {

				new AsyncTask<Void, Void, ChangePasswordWrapper>() {
					@Override
					protected ChangePasswordWrapper doInBackground(Void... params) {
						SurespotLog.v(TAG, "received password token: " + passwordToken);

						final String dNewPassword = EncryptionController.derivePassword(newPassword);
						// create token sig
						final String tokenSignature = EncryptionController.sign(pk, ChatUtils.base64Decode(passwordToken),
								dNewPassword.getBytes());

						SurespotLog.v(TAG, "generatedTokenSig: " + tokenSignature);

						return new ChangePasswordWrapper(dNewPassword, tokenSignature, authSignature, version);
					}

					protected void onPostExecute(final ChangePasswordWrapper result) {
						if (result != null) {

							// upload all this crap to the server
							MainActivity.getNetworkController().changePassword(username, dPassword, result.password, result.authSig,
									result.tokenSig, result.keyVersion, new AsyncHttpResponseHandler() {
										public void onSuccess(int statusCode, String content) {
											// update the password
											IdentityController.updatePassword(ChangePasswordActivity.this, username, currentPassword,
													newPassword);
											resetFields();
											mMpd.decrProgress();											
											Utils.makeLongToast(ChangePasswordActivity.this, "password changed");
											finish();
										};

										@Override
										public void onFailure(Throwable error, String content) {
											SurespotLog.w(TAG, "changePassword", error);											
											mMpd.decrProgress();
											resetFields();
											Utils.makeLongToast(ChangePasswordActivity.this, "could not change password");

										}
									});
						}
						else {
							mMpd.decrProgress();
							resetFields();
							Utils.makeLongToast(ChangePasswordActivity.this, "could not change password");
						}

					};
				}.execute();

			}

			@Override
			public void onFailure(Throwable error, String content) {
				mMpd.decrProgress();
				resetFields();
				Utils.makeLongToast(ChangePasswordActivity.this, "could not change password");

			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	private class ChangePasswordWrapper {

		public String tokenSig;
		public String authSig;
		public String keyVersion;
		public String password;

		public ChangePasswordWrapper(String password, String tokenSig, String authSig, String keyVersion) {
			super();
			this.password = password;
			this.tokenSig = tokenSig;
			this.authSig = authSig;
			this.keyVersion = keyVersion;
		}

	}
	
	private void resetFields() {
		final EditText etCurrent = (EditText) this.findViewById(R.id.etChangePasswordCurrent);
		etCurrent.setText("");

		final EditText etNew = (EditText) findViewById(R.id.etChangePasswordNew);
		etNew.setText("");

		final EditText etConfirm = (EditText) findViewById(R.id.etChangePasswordConfirm);
		etConfirm.setText("");
		
		etCurrent.requestFocus();
		
	}

}
