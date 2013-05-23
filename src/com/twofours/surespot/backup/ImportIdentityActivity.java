package com.twofours.surespot.backup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.FileList;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.IdentityOperationResult;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

public class ImportIdentityActivity extends SherlockActivity {
	private static final String TAG = null;
	private boolean mSignup;

	private Account mAccount;

	private boolean mShowingLocal;
	private DriveHelper mDriveHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_import_identity);
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.restore), true);

		mSignup = getIntent().getBooleanExtra("signup", false);

		RadioButton rbRestoreLocal = (RadioButton) findViewById(R.id.rbRestoreLocal);
		rbRestoreLocal.setTag("local");
		rbRestoreLocal.setChecked(true);
		mShowingLocal = true;

		RadioButton rbRestoreDrive = (RadioButton) findViewById(R.id.rbRestoreDrive);
		rbRestoreDrive.setTag("drive");

		final ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.restoreViewSwitcher);
		OnClickListener rbClickListener = new OnClickListener() {

			@Override
			public void onClick(View view) {
				// Is the button now checked?
				boolean checked = ((RadioButton) view).isChecked();

				if (checked) {
					if (view.getTag().equals("drive")) {
						if (mShowingLocal) {

							switcher.showNext();
							mShowingLocal = false;
							if (setupDrive() != null) {
								TextView tvDriveAccount = (TextView) findViewById(R.id.restoreDriveAccount);
								tvDriveAccount.setText(mDriveHelper.getDriveAccount().name);

								new AsyncTask<Void, Void, Void>() {
									@Override
									protected Void doInBackground(Void... params) {
										Drive drive = mDriveHelper.getDriveService();
										if (drive != null) {
											populateDriveIdentities(true);
										}
										return null;
									}

								}.execute();
							}
						}
					} else {
						if (!mShowingLocal) {
							switcher.showPrevious();
							mShowingLocal = true;
						}
					}

				}
			}
		};

		rbRestoreDrive.setOnClickListener(rbClickListener);
		rbRestoreLocal.setOnClickListener(rbClickListener);

		setupLocal();
	}

	private void setupLocal() {

		ListView lvIdentities = (ListView) findViewById(R.id.lvLocalIdentities);

		List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();

		// query the filesystem for identities
		final File exportDir = FileUtils.getIdentityExportDir();
		File[] files = IdentityController.getIdentityFiles(this, exportDir.getPath());

		TextView tvLocalLocation = (TextView) findViewById(R.id.restoreLocalLocation);

		for (File file : files) {
			long lastModTime = file.lastModified();
			String date = DateFormat.getDateFormat(MainActivity.getContext()).format(lastModTime) + " "
					+ DateFormat.getTimeFormat(MainActivity.getContext()).format(lastModTime);

			HashMap<String, String> map = new HashMap<String, String>();
			map.put("name", IdentityController.getIdentityNameFromFile(file));
			map.put("date", date);
			items.add(map);
		}

		final SimpleAdapter adapter = new SimpleAdapter(this, items, R.layout.identity_item, new String[] { "name", "date" }, new int[] {
				R.id.identityBackupName, R.id.identityBackupDate });
		tvLocalLocation.setText(exportDir.toString());
		lvIdentities.setVisibility(View.VISIBLE);

		lvIdentities.setAdapter(adapter);
		lvIdentities.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				@SuppressWarnings("unchecked")
				Map<String, String> map = (Map<String, String>) adapter.getItem(position);

				final String user = map.get("name");

				if (IdentityController.identityFileExists(ImportIdentityActivity.this, user)) {
					Utils.makeToast(ImportIdentityActivity.this, getString(R.string.restore_identity_already_exists));
					return;
				}

				// make sure file we're going to save to is writable before we
				// start
				if (!IdentityController.ensureIdentityFile(ImportIdentityActivity.this, user, false)) {
					Utils.makeToast(ImportIdentityActivity.this, getString(R.string.could_not_import_identity));
					return;
				}

				UIUtils.passwordDialog(ImportIdentityActivity.this, getString(R.string.restore_identity, user), getString(R.string.enter_password_for, user),
						new IAsyncCallback<String>() {
							@Override
							public void handleResponse(String result) {
								if (!TextUtils.isEmpty(result)) {
									IdentityController.importIdentity(ImportIdentityActivity.this, exportDir, user, result,
											new IAsyncCallback<IdentityOperationResult>() {

												@Override
												public void handleResponse(IdentityOperationResult response) {

													Utils.makeLongToast(ImportIdentityActivity.this, user + " " + response.getResultText());

													if (response.getResultSuccess()) {
														// if launched from
														// signup and successful
														// import, go to login
														// screen
														if (mSignup) {
															IdentityController.logout();
															Intent intent = new Intent(ImportIdentityActivity.this, MainActivity.class);
															intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
															startActivity(intent);
														}
													}

												}

											});

								} else {
									Utils.makeToast(ImportIdentityActivity.this, getString(R.string.no_identity_imported));
								}
							}
						});

			}

		});

	}

	private Drive setupDrive() {
		mDriveHelper = new DriveHelper(this);
		if (mDriveHelper.getDriveService() != null) {
			return mDriveHelper.getDriveService();
		}
		return null;
	}

	private void populateDriveIdentities(boolean firstAttempt) {

		String identityDirId = ensureDriveIdentityDirectory();
		if (identityDirId == null) {
			if (!firstAttempt) {

				this.runOnUiThread(new Runnable() {

					@Override
					public void run() {
						Utils.makeToast(ImportIdentityActivity.this, getString(R.string.could_not_list_identities_from_google_drive));
					}
				});
			}
			return;
		}

		List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();
		try {
			// query the drive for identities
			ChildList fileList = getIdentityFiles(identityDirId);

			List<ChildReference> refs = fileList.getItems();

			if (refs.size() == 0) {
				// TODO tear progress down
				return;
			}
			for (ChildReference ref : refs) {
				com.google.api.services.drive.model.File file = mDriveHelper.getDriveService().files().get(ref.getId()).execute();

				if (!file.getLabels().getTrashed()) {

					DateTime lastModTime = file.getModifiedDate();

					String date = DateFormat.getDateFormat(this).format(lastModTime.getValue()) + " "
							+ DateFormat.getTimeFormat(this).format(lastModTime.getValue());
					HashMap<String, String> map = new HashMap<String, String>();
					map.put("name", IdentityController.getIdentityNameFromFilename(file.getTitle()));
					map.put("date", date);
					items.add(map);
				}
			}

		} catch (UserRecoverableAuthIOException e) {
			startActivityForResult(e.getIntent(), SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH);
			return;
		} catch (IOException e) {
			SurespotLog.w(TAG, e, "could not retrieve identities from google drive");
			this.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Utils.makeToast(ImportIdentityActivity.this, getString(R.string.could_not_list_identities_from_google_drive));
				}
			});

			return;
		}
		
		SurespotLog.v(TAG, "loaded %d identities from google drive", items.size());

		final ListView lvIdentities = (ListView) findViewById(R.id.lvDriveIdentities);

		final SimpleAdapter adapter = new SimpleAdapter(this, items, R.layout.identity_item, new String[] { "name", "date" }, new int[] {
				R.id.identityBackupName, R.id.identityBackupDate });

		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {

				lvIdentities.setAdapter(adapter);
				lvIdentities.setOnItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						@SuppressWarnings("unchecked")
						Map<String, String> map = (Map<String, String>) adapter.getItem(position);

						final String user = map.get("name");

						if (IdentityController.identityFileExists(ImportIdentityActivity.this, user)) {
							Utils.makeToast(ImportIdentityActivity.this, getString(R.string.restore_identity_already_exists));
							return;
						}

						// make sure file we're going to save to is writable
						// before we
						// start
						if (!IdentityController.ensureIdentityFile(ImportIdentityActivity.this, user, false)) {
							Utils.makeToast(ImportIdentityActivity.this, getString(R.string.could_not_import_identity));
							return;
						}

						UIUtils.passwordDialog(ImportIdentityActivity.this, getString(R.string.restore_identity, user),
								getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
									@Override
									public void handleResponse(String result) {
										if (!TextUtils.isEmpty(result)) {
											// IdentityController.importIdentity(ImportIdentityActivity.this,
											// exportDir, user, result,
											// new
											// IAsyncCallback<IdentityOperationResult>()
											// {
											//
											// @Override
											// public void
											// handleResponse(IdentityOperationResult
											// response) {
											//
											// Utils.makeLongToast(ImportIdentityActivity.this,
											// user + " " +
											// response.getResultText());
											//
											// if (response.getResultSuccess())
											// {
											// // if launched from
											// // signup and successful
											// // import, go to login
											// // screen
											// if (mSignup) {
											// IdentityController.logout();
											// Intent intent = new
											// Intent(ImportIdentityActivity.this,
											// MainActivity.class);
											// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
											// startActivity(intent);
											// }
											// }
											//
											// }
											//
											// });

										} else {
											Utils.makeToast(ImportIdentityActivity.this, getString(R.string.no_identity_imported));
										}
									}
								});

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

	private ChildList getIdentityFiles(String identityDirId) {
		ChildList identityFileList = null;
		try {
			identityFileList = mDriveHelper.getDriveService().children().list(identityDirId).execute();
		} catch (IOException e) {
			SurespotLog.w(TAG, e, "getIdentityFiles");
		}
		return identityFileList;
	}

	public String ensureDriveIdentityDirectory() {
		String identityDirId = null;
		try {
			// see if identities directory exists

			FileList identityDir = mDriveHelper.getDriveService().files().list().setQ("title = 'identities' and trashed = false").execute();
			List<com.google.api.services.drive.model.File> items = identityDir.getItems();

			if (items.size() > 0) {
				for (com.google.api.services.drive.model.File file : items) {
					if (!file.getLabels().getTrashed()) {
						SurespotLog.d(TAG, "identity folder already exists");
						identityDirId = file.getId();
					}
				}
			}
			if (identityDirId == null) {
				com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
				file.setTitle("identities");
				file.setMimeType(SurespotConstants.MimeTypes.DRIVE_FOLDER);

				com.google.api.services.drive.model.File insertedFile = mDriveHelper.getDriveService().files().insert(file).execute();

				identityDirId = insertedFile.getId();

			}

		} catch (UserRecoverableAuthIOException e) {
			startActivityForResult(e.getIntent(), SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH);
		} catch (IOException e) {
			SurespotLog.w(TAG, e, "createDriveIdentityDirectory");
		}
		return identityDirId;
	}

	// //////// DRIVE
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH:
			if (resultCode == Activity.RESULT_OK) {
				new AsyncTask<Void, Void, Void>() {

					@Override
					protected Void doInBackground(Void... params) {
						Drive drive = mDriveHelper.getDriveService();
						if (drive != null) {
							populateDriveIdentities(false);

						}
						return null;

					}
				}.execute();

			} else {

			}
		}
	}
}
