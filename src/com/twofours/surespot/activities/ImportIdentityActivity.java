package com.twofours.surespot.activities;

import java.io.File;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.IdentityOperationResult;
import com.twofours.surespot.R;
import com.twofours.surespot.UIUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ImportIdentityActivity extends SherlockActivity {
	private boolean mSignup;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_import_identity);
		Utils.configureActionBar(this, "identity", "import", true);

		mSignup = getIntent().getBooleanExtra("signup", false);

		ListView lvIdentities = (ListView) findViewById(R.id.lvIdentities);

		final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);

		// query the filesystem for identities
		final File exportDir = FileUtils.getIdentityExportDir();

		TextView tvFound = (TextView) findViewById(R.id.foundText);
		tvFound.setText("discovered the identities below in " + exportDir + ", click to import");

		for (String name : IdentityController.getIdentityNames(this, exportDir.getPath())) {
			adapter.add(name);
		}

		lvIdentities.setAdapter(adapter);

		lvIdentities.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final String user = adapter.getItem(position).toString();

				UIUtils.passwordDialog(ImportIdentityActivity.this, "import " + user + " identity", "enter password for " + user,
						new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (result != null && !result.isEmpty()) {
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
									Utils.makeToast(ImportIdentityActivity.this, getText(R.string.no_identity_imported).toString());
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
