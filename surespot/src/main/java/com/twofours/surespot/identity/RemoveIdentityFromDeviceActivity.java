package com.twofours.surespot.identity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.SignupActivity;
import com.twofours.surespot.utils.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.utils.UIUtils;

import java.util.List;

public class RemoveIdentityFromDeviceActivity extends Activity {
	private static final String TAG = null;
	private List<String> mIdentityNames;
	private Spinner mSpinner;
	private MultiProgressDialog mMpd;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		UIUtils.setTheme(this);
		super.onCreate(savedInstanceState);
		String savedUsername = null;
		savedUsername = getIntent().getStringExtra("selectedUsername");

		setContentView(R.layout.activity_remove_identity_from_device);
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.remove_title_right), true);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		mMpd = new MultiProgressDialog(this, "removing identity from device", 250);

		Button deleteIdentityButton = (Button) findViewById(R.id.bRemoveIdentity);
		mSpinner = (Spinner) findViewById(R.id.identitySpinner);
		refreshSpinner(savedUsername);

		deleteIdentityButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String user = (String) mSpinner.getSelectedItem();
				mDialog = UIUtils.passwordDialog(RemoveIdentityFromDeviceActivity.this, getString(R.string.remove_identity_user, user),
						getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									if (IdentityController.loadIdentity(RemoveIdentityFromDeviceActivity.this, user, result) == null) {
										Utils.makeToast(RemoveIdentityFromDeviceActivity.this, getString(R.string.incorrect_password_or_key));
									}
									else {
										removeIdentity(user, result);
									}
								}
								else {
									Utils.makeToast(RemoveIdentityFromDeviceActivity.this, getString(R.string.no_identity_removed));
								}
							}
						});

			}
		});
	}

	private void refreshSpinner(String savedUsername) {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		if (mIdentityNames == null || mIdentityNames.size() == 0) {
			Intent intent = new Intent(RemoveIdentityFromDeviceActivity.this, SignupActivity.class);
			startActivity(intent);
			finish();
			return;
		}

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		mSpinner.setAdapter(adapter);
		String loggedInUser = IdentityController.getLoggedInUser();
		if (loggedInUser != null) {
			mSpinner.setSelection(adapter.getPosition(loggedInUser));
		}
		else {
			int pos = adapter.getPosition(savedUsername);
			if (pos >= 0) {
				mSpinner.setSelection(pos);
			}
		}
	}

	private void removeIdentity(final String username, final String password) {

		mMpd.incrProgress();
		SurespotIdentity identity = IdentityController.getIdentity(this, username, password);

		if (identity == null) {
			mMpd.decrProgress();
			Utils.makeLongToast(RemoveIdentityFromDeviceActivity.this, getString(R.string.could_not_remove_identity_from_device));
			return;
		}

		// do we need to check in with the server at all?
		// delete the identity stuff locally
		IdentityController.deleteIdentity(RemoveIdentityFromDeviceActivity.this, username, true);
		refreshSpinner(null);
		mMpd.decrProgress();
		Utils.makeLongToast(RemoveIdentityFromDeviceActivity.this, getString(R.string.identity_removed_from_device));
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
