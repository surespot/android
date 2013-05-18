package com.twofours.surespot.identity;

import java.io.File;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class ImportIdentityActivity extends SherlockActivity {
	private boolean mSignup;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_import_identity);
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.restore), true);

		mSignup = getIntent().getBooleanExtra("signup", false);

		ListView lvIdentities = (ListView) findViewById(R.id.lvIdentities);

		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

		// query the filesystem for identities
		final File exportDir = FileUtils.getIdentityExportDir();

		TextView tvFound = (TextView) findViewById(R.id.foundText);

		for (String name : IdentityController.getIdentityNames(this, exportDir.getPath())) {
			adapter.add(name);
		}

		if (adapter.getCount() > 0) {
			tvFound.setText(getString(R.string.restore_discovered_identities_location, exportDir));
			lvIdentities.setVisibility(View.VISIBLE);
		}
		else {
			tvFound.setText(getString(R.string.restore_identities_none_discovered, exportDir));
			lvIdentities.setVisibility(View.GONE);
		}

		lvIdentities.setAdapter(adapter);
		lvIdentities.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final String user = adapter.getItem(position).toString();

				if (IdentityController.identityFileExists(ImportIdentityActivity.this, user)) {
					Utils.makeToast(ImportIdentityActivity.this, getString(R.string.restore_identity_already_exists));
					return;
				}

				// make sure file we're going to save to is writable before we start
				if (!IdentityController.ensureIdentityFile(ImportIdentityActivity.this, user, false)) {
					Utils.makeToast(ImportIdentityActivity.this, getString(R.string.could_not_import_identity));
					return;
				}

				UIUtils.passwordDialog(ImportIdentityActivity.this, getString(R.string.restore_identity, user),
						getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									IdentityController.importIdentity(ImportIdentityActivity.this, exportDir, user, result,
											new IAsyncCallback<IdentityOperationResult>() {

												@Override
												public void handleResponse(IdentityOperationResult response) {

													Utils.makeLongToast(ImportIdentityActivity.this, user + " " + response.getResultText());

													if (response.getResultSuccess()) {
														// if launched from signup and successful import, go to login screen
														if (mSignup) {
															IdentityController.logout();
															Intent intent = new Intent(ImportIdentityActivity.this, MainActivity.class);
															intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
															startActivity(intent);
														}
													}

												}

											});

								}
								else {
									Utils.makeToast(ImportIdentityActivity.this, getString(R.string.no_identity_imported));
								}
							}
						});

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
