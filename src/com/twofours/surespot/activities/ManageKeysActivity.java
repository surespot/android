package com.twofours.surespot.activities;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.ui.UIUtils;

public class ManageKeysActivity extends SherlockActivity {
	private static final String TAG = "ManageKeysActivity";
	private List<String> mIdentityNames;
	private MultiProgressDialog mMpd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_manage_keys);
		Utils.configureActionBar(this, "settings", "keys", true);
		mMpd = new MultiProgressDialog(this, "generating and uploading new keys", 750);

		final Spinner spinner = (Spinner) findViewById(R.id.identitySpinner);

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);

		Button rollKeysButton = (Button) findViewById(R.id.bRollKeys);
		rollKeysButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String user = (String) spinner.getSelectedItem();

				// make sure file we're going to save to is writable before we start
				if (!IdentityController.ensureIdentityFile(ManageKeysActivity.this, user)) {
					Utils.makeToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
					return;
				}

				UIUtils.passwordDialog(ManageKeysActivity.this, "create new keys for " + user, "enter password for " + user,
						new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									rollKeys(user, result);
								}
								else {
									Utils.makeToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
								}
							}
						});

			}
		});
	}

	private class RollKeysWrapper {

		public String tokenSig;
		public String authSig;
		public String keyVersion;
		public KeyPair[] keyPairs;

		public RollKeysWrapper(KeyPair[] keyPairs, String tokenSig, String authSig, String keyVersion) {
			super();
			this.keyPairs = keyPairs;
			this.tokenSig = tokenSig;
			this.authSig = authSig;
			this.keyVersion = keyVersion;
		}

	}

	private void rollKeys(final String username, final String password) {

		mMpd.incrProgress();
		SurespotIdentity identity = IdentityController.getIdentity(this, username, password);

		if (identity == null) {
			mMpd.decrProgress();
			Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
			return;
		}

		final PrivateKey pk = identity.getKeyPairDSA().getPrivate();

		// create auth sig
		final String dPassword = EncryptionController.derivePassword(password);
		final String authSignature = EncryptionController.sign(pk, username, dPassword);
		SurespotLog.v(TAG, "generatedAuthSig: " + authSignature);

		// get a key update token from the server
		MainActivity.getNetworkController().getKeyToken(username, dPassword, authSignature, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, final JSONObject response) {

				new AsyncTask<Void, Void, RollKeysWrapper>() {
					@Override
					protected RollKeysWrapper doInBackground(Void... params) {
						String keyToken = null;
						String keyVersion = null;

						try {
							keyToken = response.getString("token");
							SurespotLog.v(TAG, "received key token: " + keyToken);
							keyVersion = response.getString("keyversion");
						}
						catch (JSONException e) {
							return null;
						}

						// create token sig
						final String tokenSignature = EncryptionController.sign(pk, ChatUtils.base64Decode(keyToken), dPassword.getBytes());

						SurespotLog.v(TAG, "generatedTokenSig: " + tokenSignature);
						// generate new key pairs
						KeyPair[] keys = EncryptionController.generateKeyPairsSync();
						if (keys == null) {
							return null;
						}

						return new RollKeysWrapper(keys, tokenSignature, authSignature, keyVersion);
					}

					protected void onPostExecute(final RollKeysWrapper result) {
						if (result != null) {
							// upload all this crap to the server
							MainActivity.getNetworkController().updateKeys(username, dPassword,
									EncryptionController.encodePublicKey(result.keyPairs[0].getPublic()),
									EncryptionController.encodePublicKey(result.keyPairs[1].getPublic()), result.authSig, result.tokenSig,
									result.keyVersion, new AsyncHttpResponseHandler() {
										public void onSuccess(int statusCode, String content) {
											// save the key pairs
											IdentityController.rollKeys(ManageKeysActivity.this, username, password, result.keyVersion,
													result.keyPairs[0], result.keyPairs[1]);
											mMpd.decrProgress();
											Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.keys_created));
										};

										@Override
										public void onFailure(Throwable error, String content) {
											SurespotLog.w(TAG, "rollKeys", error);
											mMpd.decrProgress();
											Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));

										}
									});
						}
						else {
							mMpd.decrProgress();
							Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
						}

					};
				}.execute();

			}

			@Override
			public void onFailure(Throwable error, String content) {
				mMpd.decrProgress();
				Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));

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
}
