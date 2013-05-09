package com.twofours.surespot.identity;

import java.io.File;
import java.util.List;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class ExportIdentityActivity extends SherlockActivity {
	private List<String> mIdentityNames;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_export_identity);
		Utils.configureActionBar(this, "identity", "backup", true);
		final String identityDir = FileUtils.getIdentityExportDir().toString();

		final TextView tvPath = (TextView) findViewById(R.id.identityBackupPath);
		final Spinner spinner = (Spinner) findViewById(R.id.identitySpinner);

		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.sherlock_spinner_item);
		adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);
		spinner.setSelection(adapter.getPosition(IdentityController.getLoggedInUser()));
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

				String identityFile = identityDir + File.separator + adapter.getItem(position) + IdentityController.IDENTITY_EXTENSION;
				tvPath.setText(identityFile);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			
			}

		});

		Button exportToSdCardButton = (Button) findViewById(R.id.bExportSd);

		exportToSdCardButton.setEnabled(FileUtils.isExternalStorageMounted());

		exportToSdCardButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO progress
				final String user = (String) spinner.getSelectedItem();
				UIUtils.passwordDialog(ExportIdentityActivity.this, "backup " + user + " identity", "enter password for " + user,
						new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									exportIdentity(user, result);
								}
								else {
									Utils.makeToast(ExportIdentityActivity.this, getText(R.string.no_identity_exported).toString());
								}
							}
						});

			}
		});
	}

	private void exportIdentity(String user, String password) {
		IdentityController.exportIdentity(ExportIdentityActivity.this, user, password, new IAsyncCallback<String>() {
			@Override
			public void handleResponse(String response) {
				if (response == null) {
					Utils.makeToast(ExportIdentityActivity.this, getText(R.string.no_identity_exported).toString());
				}
				else {
					Utils.makeLongToast(ExportIdentityActivity.this, response);
				}

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
