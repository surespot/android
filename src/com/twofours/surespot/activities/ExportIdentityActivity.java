package com.twofours.surespot.activities;

import java.util.List;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.UIUtils;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ExportIdentityActivity extends SherlockActivity {
	private List<String> mIdentityNames;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_export_identity);
		Utils.configureActionBar(this, "identity", "export", true);

		final Spinner spinner = (Spinner) findViewById(R.id.identitySpinner);

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_dropdown_item);
		mIdentityNames = IdentityController.getIdentityNames(this);

		for (String name : mIdentityNames) {
			adapter.add(name);
		}

		spinner.setAdapter(adapter);

		Button exportToSdCardButton = (Button) findViewById(R.id.bExportSd);
		exportToSdCardButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String user = (String) spinner.getSelectedItem();
				UIUtils.passwordDialog(ExportIdentityActivity.this, user, new IAsyncCallback<String>() {

					@Override
					public void handleResponse(String result) {
						if (result != null && !result.isEmpty()) {
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
