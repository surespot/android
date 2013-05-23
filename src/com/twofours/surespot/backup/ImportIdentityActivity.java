package com.twofours.surespot.backup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.common.AccountPicker;
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
import com.twofours.surespot.ui.SingleProgressDialog;
import com.twofours.surespot.ui.UIUtils;

public class ImportIdentityActivity extends SherlockActivity {
	private static final String TAG = null;
	private boolean mSignup;

	private TextView mAccountNameDisplay;
	private boolean mShowingLocal;
	private DriveHelper mDriveHelper;
	private ListView mDriveListview;
	private SingleProgressDialog mSpd;
	private SingleProgressDialog mSpdLoadIdentities;

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
		mSpdLoadIdentities = new SingleProgressDialog(ImportIdentityActivity.this, getString(R.string.progress_loading_identities), 0);
		
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
							if (mDriveHelper.getDriveAccount() != null) {
								Drive drive = mDriveHelper.getDriveService();
								if (drive != null) {									
									mSpdLoadIdentities.show();
									new AsyncTask<Void, Void, Void>() {
										@Override
										protected Void doInBackground(Void... params) {
											populateDriveIdentities(true);

											return null;
										}

									}.execute();
								}
							} else {
								Utils.makeToast(ImportIdentityActivity.this, getString(R.string.select_google_drive_account));
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

		mDriveHelper = new DriveHelper(this);
		Account account = mDriveHelper.getDriveAccount();

		mAccountNameDisplay = (TextView) findViewById(R.id.restoreDriveAccount);
		mAccountNameDisplay.setText(account == null ? "" : account.name);

		Button chooseAccountButton = (Button) findViewById(R.id.bSelectDriveAccount);
		chooseAccountButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				chooseAccount();
			}
		});
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
					String name = IdentityController.getIdentityNameFromFilename(file.getTitle());
					map.put("name", name);
					map.put("date", date);
					map.put("url", file.getDownloadUrl());
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

		} catch (SecurityException e) {
			SurespotLog.w(TAG, e, "createDriveIdentityDirectory");
			// when key is revoked on server this happens...should return userrecoverable it seems
			// was trying to figure out how to test this
			// seems like the only way around this is to remove and re-add android account:
			// http://stackoverflow.com/questions/5805657/revoke-account-permission-for-an-app
			this.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Utils.makeLongToast(ImportIdentityActivity.this, getString(R.string.re_add_google_account));

				}
			});

			return;
		}

		SurespotLog.v(TAG, "loaded %d identities from google drive", items.size());

		mDriveListview = (ListView) findViewById(R.id.lvDriveIdentities);

		final SimpleAdapter adapter = new SimpleAdapter(this, items, R.layout.identity_item, new String[] { "name", "date" }, new int[] {
				R.id.identityBackupName, R.id.identityBackupDate });

		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {

				mSpdLoadIdentities.hide();
				mDriveListview.setAdapter(adapter);
				mDriveListview.setOnItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
						@SuppressWarnings("unchecked")
						final Map<String, String> map = (Map<String, String>) adapter.getItem(position);

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
									public void handleResponse(final String password) {
										if (!TextUtils.isEmpty(password)) {
											if (mSpd == null) {
												mSpd = new SingleProgressDialog(ImportIdentityActivity.this, getString(R.string.progress_restoring_identity), 0);
											}
											mSpd.show();

											final String url = map.get("url");

											new AsyncTask<Void, Void, Void>() {

												@Override
												protected Void doInBackground(Void... params) {
													byte[] identityBytes = mDriveHelper.getFileContent(url);

													IdentityController.importIdentityBytes(ImportIdentityActivity.this, user, password, identityBytes,
															new IAsyncCallback<IdentityOperationResult>() {

																@Override
																public void handleResponse(final IdentityOperationResult response) {

																	ImportIdentityActivity.this.runOnUiThread(new Runnable() {

																		@Override
																		public void run() {
																			mSpd.hide();
																			Utils.makeLongToast(ImportIdentityActivity.this,
																					user + " " + response.getResultText());

																			if (response.getResultSuccess()) {
																				// if launched
																				// from
																				// signup and
																				// successful
																				// import, go to
																				// login
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

																}

															});
													return null;
												}

											}.execute();

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

			FileList identityDir = mDriveHelper.getDriveService().files().list()
					.setQ("title = '" + GoogleDriveBackupActivity.DRIVE_IDENTITY_FOLDER + "' and trashed = false").execute();
			List<com.google.api.services.drive.model.File> items = identityDir.getItems();

			if (items.size() > 0) {
				for (com.google.api.services.drive.model.File file : items) {
					if (!file.getLabels().getTrashed()) {
						SurespotLog.d(TAG, "identity folder already exists");
						identityDirId = file.getId();
						break;
					}
				}
			}
			if (identityDirId == null) {
				com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();
				file.setTitle(GoogleDriveBackupActivity.DRIVE_IDENTITY_FOLDER);
				file.setMimeType(SurespotConstants.MimeTypes.DRIVE_FOLDER);

				com.google.api.services.drive.model.File insertedFile = mDriveHelper.getDriveService().files().insert(file).execute();

				identityDirId = insertedFile.getId();

			}

		} catch (UserRecoverableAuthIOException e) {			
			SurespotLog.w(TAG, e, "createDriveIdentityDirectory");
			startActivityForResult(e.getIntent(), SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH);
		} catch (IOException e) {			
			SurespotLog.w(TAG, e, "createDriveIdentityDirectory");
		} catch (SecurityException e) {
			SurespotLog.e(TAG, e, "createDriveIdentityDirectory");
			// when key is revoked on server this happens...should return userrecoverable it seems
			// was trying to figure out how to test this
			// seems like the only way around this is to remove and re-add android account:
			// http://stackoverflow.com/questions/5805657/revoke-account-permission-for-an-app
			this.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Utils.makeLongToast(ImportIdentityActivity.this, getString(R.string.re_add_google_account));

				}
			});

		}

		return identityDirId;
	}

	// //////// DRIVE
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.CHOOSE_GOOGLE_ACCOUNT:

			if (resultCode == Activity.RESULT_OK && data != null) {

				SurespotLog.w("Preferences", "SELECTED ACCOUNT WITH EXTRA: %s", data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
				Bundle b = data.getExtras();

				String accountName = b.getString(AccountManager.KEY_ACCOUNT_NAME);

				SurespotLog.d("Preferences", "Selected account: " + accountName);
				if (accountName != null && accountName.length() > 0) {

					mDriveHelper.setDriveAccount(accountName);
					mAccountNameDisplay.setText(accountName);
					if (mDriveListview != null) {
						mDriveListview.setAdapter(null);
					}
					mSpdLoadIdentities.show();
					new AsyncTask<Void, Void, Void>() {
						@Override
						protected Void doInBackground(Void... params) {
							populateDriveIdentities(true);
							return null;
						}

					}.execute();
				}
			}
			break;

		case SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH:
			if (resultCode == Activity.RESULT_OK) {
				mSpdLoadIdentities.show();
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

	private void chooseAccount() {
		Intent accountPickerIntent = AccountPicker.newChooseAccountIntent(null, null, GoogleDriveBackupActivity.ACCOUNT_TYPE, true, null, null, null, null);
		startActivityForResult(accountPickerIntent, SurespotConstants.IntentRequestCodes.CHOOSE_GOOGLE_ACCOUNT);

	}

}
