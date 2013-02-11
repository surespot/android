package com.twofours.surespot.activities;

import java.io.File;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.Utils;

public class ImportIdentityActivity extends SherlockActivity {
	private List<String> mIdentityNames;
	private boolean mSignup;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_import_identity);
		Utils.configureActionBar(this, "identity", "import", true);

		mSignup = getIntent().getBooleanExtra("signup", false);

		ListView lvIdentities = (ListView) findViewById(R.id.lvIdentities);

		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_selectable_list_item);

		// query the filesystem for identities
		final File exportDir = FileUtils.getIdentityExportDir();

		for (String name : IdentityController.getIdentityNames(this, exportDir.getPath())) {
			adapter.add(name);
		}

		lvIdentities.setAdapter(adapter);

		Button importFromSdCardButton = (Button) findViewById(R.id.bImportSd);
		importFromSdCardButton.setEnabled(adapter.getCount() > 0);

		importFromSdCardButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				IdentityController.importIdentities(ImportIdentityActivity.this, exportDir);
				Utils.makeLongToast(ImportIdentityActivity.this, "Identities imported.");

				// if launched from signup and successful import, go to login screen
				if (mSignup) {
					Intent intent = new Intent(ImportIdentityActivity.this, LoginActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
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
