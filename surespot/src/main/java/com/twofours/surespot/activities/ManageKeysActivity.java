package com.twofours.surespot.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.backup.ExportIdentityActivity;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class ManageKeysActivity extends Activity {
	private static final String TAG = "ManageKeysActivity";
	private List<String> mIdentityNames;
	private MultiProgressDialog mMpd;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		UIUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_manage_keys);
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.keys), true);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		mMpd = new MultiProgressDialog(this, getString(R.string.generating_keys_progress), 750);

		TextView tvBackupWarning = (TextView) findViewById(R.id.newKeysBackup);

		Spannable warning = new SpannableString(getString(R.string.backup_identities_again_keys));

		warning.setSpan(new ForegroundColorSpan(Color.RED), 0, warning.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		tvBackupWarning.setText(TextUtils.concat(warning));

		final Spinner spinner = (Spinner) findViewById(R.id.identitySpinner);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);
		spinner.setSelection(adapter.getPosition(IdentityController.getLoggedInUser()));

		Button rollKeysButton = (Button) findViewById(R.id.bRollKeys);
		rollKeysButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String user = (String) spinner.getSelectedItem();

				// make sure file we're going to save to is writable before we start
				if (!IdentityController.ensureIdentityFile(ManageKeysActivity.this, user, true)) {
					Utils.makeToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
					return;
				}

				mDialog = UIUtils.passwordDialog(ManageKeysActivity.this, getString(R.string.create_new_keys_for, user),
						getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
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
		public String clientSig;
		public KeyPair[] keyPairs;

		public RollKeysWrapper(KeyPair[] keyPairs, String tokenSig, String authSig, String keyVersion, String clientSig) {
			super();
			this.keyPairs = keyPairs;
			this.tokenSig = tokenSig;
			this.authSig = authSig;
			this.keyVersion = keyVersion;
			this.clientSig = clientSig;
		}

	}

	private void rollKeys(final String username, final String password) {

		mMpd.incrProgress();
		final SurespotIdentity identity = IdentityController.getIdentity(this, username, password);

		if (identity == null) {
			mMpd.decrProgress();
			Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
			return;
		}

		final PrivateKey latestPk = identity.getKeyPairDSA().getPrivate();

		// create auth sig
		byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
		final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));
		final String authSignature = EncryptionController.sign(latestPk, username, dPassword);
		SurespotLog.v(TAG, "generatedAuthSig: " + authSignature);

		// get a key update token from the server
		NetworkManager.getNetworkController(ManageKeysActivity.this, username).getKeyToken(username, dPassword, authSignature, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback()
		{
			@Override
			public void onFailure(Call call, IOException e) {
				mMpd.decrProgress();
				Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
			}

			@Override
			public void onResponse(Call call, Response response, final String responseString) throws IOException {
				if (response.isSuccessful()) {
					new AsyncTask<Void, Void, RollKeysWrapper>() {
						@Override
						protected RollKeysWrapper doInBackground(Void... params) {
							String keyToken = null;
							String keyVersion = null;

							try {
								JSONObject json = new JSONObject(responseString);
								keyToken = json.getString("token");
								SurespotLog.v(TAG, "received key token: " + keyToken);
								keyVersion = json.getString("keyversion");
							} catch (JSONException e) {
								return null;
							}

							// create token sig
							final String tokenSignature = EncryptionController.sign(latestPk, ChatUtils.base64DecodeNowrap(keyToken),
									dPassword.getBytes());

							SurespotLog.v(TAG, "generatedTokenSig: " + tokenSignature);
							// generate new key pairs
							KeyPair[] keys = EncryptionController.generateKeyPairsSync();
							if (keys == null) {
								return null;
							}

							//sign new key with old key
							String dh = new String(ChatUtils.base64EncodeNowrap(keys[0].getPublic().getEncoded()));
							String dsa =  new String(ChatUtils.base64EncodeNowrap(keys[1].getPublic().getEncoded()));

							String clientSig = EncryptionController.sign(latestPk, username, Integer.parseInt(keyVersion, 10), dh, dsa);

							return new RollKeysWrapper(keys, tokenSignature, authSignature, keyVersion, clientSig);
						}

						protected void onPostExecute(final RollKeysWrapper result) {
							if (result != null) {
								// upload all this crap to the server
								NetworkManager.getNetworkController(ManageKeysActivity.this, username).updateKeys3(username, dPassword,
										EncryptionController.encodePublicKey(result.keyPairs[0].getPublic()),
										EncryptionController.encodePublicKey(result.keyPairs[1].getPublic()), result.authSig, result.tokenSig,
										result.keyVersion, result.clientSig, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

											@Override
											public void onFailure(Call call, IOException e) {
												SurespotLog.i(TAG, "error rollKeys");
												mMpd.decrProgress();
												Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
											}

											@Override
											public void onResponse(Call call, Response response, String responseString) throws IOException {
												if (response.isSuccessful()) {
													// save the key pairs
													IdentityController.rollKeys(ManageKeysActivity.this, identity, username, password, result.keyVersion,
															result.keyPairs[0], result.keyPairs[1]);
													mMpd.decrProgress();
													Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.keys_created));
													Intent intent = new Intent(ManageKeysActivity.this, ExportIdentityActivity.class);
													intent.putExtra("backupUsername", username);
													ManageKeysActivity.this.startActivity(intent);
												}
												else {
													SurespotLog.i(TAG, "error rollKeys");
													mMpd.decrProgress();
													Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
												}
											}


										}));

							} else {
								mMpd.decrProgress();
								Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
							}

						}


					}.execute();

				}
				else {
					mMpd.decrProgress();
					Utils.makeLongToast(ManageKeysActivity.this, getString(R.string.could_not_create_new_keys));
				}
			}




		}));
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
	
	@Override
	public void onPause() {		
		super.onPause();
		if (mDialog != null && mDialog.isShowing()) {
			mDialog.dismiss();		
		}
	}
}
