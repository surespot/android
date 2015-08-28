package com.twofours.surespot.identity;

import android.app.AlertDialog;
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
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.ui.UIUtils;

import java.security.PrivateKey;
import java.util.List;

public class DeleteIdentityFromDeviceActivity extends SherlockActivity {
	private static final String TAG = null;
	private List<String> mIdentityNames;
	private Spinner mSpinner;
	private MultiProgressDialog mMpd;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_delete_identity_from_device);
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.delete), true);

		mMpd = new MultiProgressDialog(this, getString(R.string.delete_identity_from_device_progress), 250);

		Button deleteIdentityButton = (Button) findViewById(R.id.bDeleteIdentity);
		mSpinner = (Spinner) findViewById(R.id.identitySpinner);
		refreshSpinner();

		deleteIdentityButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String user = (String) mSpinner.getSelectedItem();
				mDialog = UIUtils.passwordDialog(DeleteIdentityFromDeviceActivity.this, getString(R.string.delete_identity_user, user),
						getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									deleteIdentity(user, result);
								}
								else {
									Utils.makeToast(DeleteIdentityFromDeviceActivity.this, getString(R.string.no_identity_deleted));
								}
							}
						});

			}
		});
	}

	private void refreshSpinner() {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		mSpinner.setAdapter(adapter);
		String loggedInUser = IdentityController.getLoggedInUser();
		if (loggedInUser != null) {
			mSpinner.setSelection(adapter.getPosition(loggedInUser));
		}

	}

	private void deleteIdentity(final String username, final String password) {

		mMpd.incrProgress();
		SurespotIdentity identity = IdentityController.getIdentity(this, username, password);

		if (identity == null) {
			mMpd.decrProgress();
			Utils.makeLongToast(DeleteIdentityFromDeviceActivity.this, getString(R.string.could_not_delete_identity_from_device));
			return;
		}

		// do we need to check in with the server at all?
		// delete the identity stuff locally
		IdentityController.deleteIdentity(DeleteIdentityFromDeviceActivity.this, username);
		refreshSpinner();
		mMpd.decrProgress();
		Utils.makeLongToast(DeleteIdentityFromDeviceActivity.this, getString(R.string.identity_deleted_from_device));
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
