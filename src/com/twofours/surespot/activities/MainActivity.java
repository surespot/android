package com.twofours.surespot.activities;

import java.io.File;
import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageCaptureHandler;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.viewpagerindicator.TitlePageIndicator;

public class MainActivity extends SherlockFragmentActivity {
	public static final String TAG = "MainActivity";

	private static CredentialCachingService mCredentialCachingService = null;
	private static NetworkController mNetworkController = null;

	private static Context mContext = null;
	private static Handler mMainHandler = null;
	private ArrayList<MenuItem> mMenuItems = new ArrayList<MenuItem>();
	private IAsyncCallback<Void> m401Handler;
	private ChatController mChatController = null;
	private boolean mCacheServiceBound;
	private Menu mMenuOverflow;
	private BroadcastReceiver mExternalStorageReceiver;
	private boolean mExternalStorageAvailable = false;
	private boolean mExternalStorageWriteable = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		Intent intent = getIntent();
		Utils.logIntent(TAG, intent);

		m401Handler = new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				SurespotLog.v(TAG, "Got 401, checking authorization.");
				if (!MainActivity.this.getNetworkController().isUnauthorized()) {
					MainActivity.this.getNetworkController().setUnauthorized(true);
					IdentityController.logout();

					SurespotLog.v(TAG, "Got 401, launching login intent.");
					Intent intent = new Intent(MainActivity.this, LoginActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
					finish();
				}
			}
		};

		// if we have any users or we don't need to create a user, figure out if we need to login
		if (!IdentityController.hasIdentity() || intent.getBooleanExtra("create", false)) {
			// otherwise show the signup activity

			SurespotLog.v(TAG, "starting signup activity");
			Intent newIntent = new Intent(this, SignupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(newIntent);
			finish();
		}
		else {
			if (needsLogin()) {
				SurespotLog.v(TAG, "need a (different) user, logging out");

				if (mCredentialCachingService != null) {
					if (mCredentialCachingService.getLoggedInUser() != null) {
						if (mNetworkController != null) {
							mNetworkController.logout();
						}

						mCredentialCachingService.logout();
					}
				}

				Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);
				newIntent.setAction(intent.getAction());
				newIntent.setType(intent.getType());
				newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				Bundle extras = intent.getExtras();
				if (extras != null) {
					newIntent.putExtras(extras);
				}

				startActivity(newIntent);
				finish();
			}
			else {
				setContentView(R.layout.activity_main);

				SurespotLog.v(TAG, "binding cache service");
				Intent cacheIntent = new Intent(this, CredentialCachingService.class);
				bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);
				// create the chat controller here if we know we're not going to need to login
				// so that if we come back from a restart (for example a rotation), the automatically
				// created fragments have a chat controller instance

				mMainHandler = new Handler(getMainLooper());
				mNetworkController = new NetworkController(MainActivity.this, m401Handler);
				mChatController = new ChatController(MainActivity.this, getSupportFragmentManager(), m401Handler);
				mChatController.init((ViewPager) findViewById(R.id.pager), (TitlePageIndicator) findViewById(R.id.indicator), mMenuItems);
			}
		}
	}

	private boolean needsLogin() {
		String user = IdentityController.getLoggedInUser();

		Intent intent = getIntent();
		String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
		String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);

		SurespotLog.v(TAG, "user: " + user);
		SurespotLog.v(TAG, "type: " + notificationType);
		SurespotLog.v(TAG, "messageTo: " + messageTo);

		if ((user == null)
				|| ((SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)
						|| SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE
							.equals(notificationType)) && (!messageTo.equals(user)))) {
			return true;
		}
		return false;
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
			SurespotLog.v(TAG, "caching service bound");
			CredentialCachingBinder binder = (CredentialCachingBinder) service;
			mCredentialCachingService = binder.getService();
			mCacheServiceBound = true;
			launch();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void launch() {
		SurespotLog.v(TAG, "launch, mChatController: " + mChatController);

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
		String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

		boolean mSet = false;
		String name = null;

		// if we're coming from an invite notification, or we need to send to someone
		// then display friends
		if (SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType)
				|| SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType)) {
			SurespotLog.v(TAG, "started from invite");
			mSet = true;
			Utils.clearIntent(intent);
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);
		}

		// message received show chat activity for user
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {

			SurespotLog.v(TAG, "started from message, to: " + messageTo + ", from: " + messageFrom);
			name = messageFrom;
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);
			mSet = true;
			Utils.clearIntent(intent);
			Utils.logIntent(TAG, intent);

			if (mChatController != null) {
				mChatController.setCurrentChat(name);

			}
		}

		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
			Utils.configureActionBar(this, "send", "select recipient", false);
			SurespotLog.v(TAG, "started from SEND");
			// need to select a user so put the chat controller in select mode

			if (mChatController != null) {
				mChatController.setCurrentChat(null);
				mChatController.setMode(ChatController.MODE_SELECT);
			}
			mSet = true;
		}

		if (!mSet) {
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);
			String lastName = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
			if (lastName != null) {
				SurespotLog.v(TAG, "using LAST_CHAT");
				name = lastName;
			}
			if (mChatController != null) {
				mChatController.setCurrentChat(name);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");
		if (mChatController != null) {
			mChatController.onResume();
		}
		startWatchingExternalStorage();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mChatController != null) {
			mChatController.onPause();
		}
		stopWatchingExternalStorage();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);

		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE:
			if (resultCode == RESULT_OK) {
				Uri selectedImageUri = data.getData();

				String to = data.getStringExtra("to");
				SurespotLog.v(TAG, "to: " + to);
				final String filename = data.getStringExtra("filename");
				if (selectedImageUri != null) {

					Utils.makeToast(this, getString(R.string.uploading_image));
					ChatUtils.uploadPictureMessageAsync(this, selectedImageUri, to, false, new IAsyncCallback<Boolean>() {
						@Override
						public void handleResponse(Boolean result) {
							if (result) {
								Utils.makeToast(MainActivity.this, getString(R.string.image_successfully_uploaded));
							}
							else {
								Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_image));
							}

							new File(filename).delete();
						}
					});
				}
			}
			break;
		case SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE:
			if (resultCode == RESULT_OK) {
				mImageCaptureHandler.handleResult();
			}
			break;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		super.onCreateOptionsMenu(menu);
		SurespotLog.v(TAG, "onCreateOptionsMenu");

		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);
		mMenuOverflow = menu;

		mMenuItems.add(menu.findItem(R.id.menu_close_bar));
		mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));

		MenuItem captureItem = menu.findItem(R.id.menu_capture_image_bar);
		PackageManager pm = this.getPackageManager();
		if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			SurespotLog.v(TAG, "hiding capture image menu option");
			mMenuItems.add(captureItem);
			captureItem.setEnabled(FileUtils.isExternalStorageMounted());
		}
		else {
			menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
		}

		enableMenuItems();

		return true;
	}

	private ImageCaptureHandler mImageCaptureHandler;

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		Intent intent = null;
		String currentChat = mChatController.getCurrentChat();
		switch (item.getItemId()) {
		case android.R.id.home:
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			// showUi(!mChatsShowing);
			mChatController.setCurrentChat(null);
			return true;
		case R.id.menu_close_bar:

			mChatController.closeTab();
			return true;
		case R.id.menu_send_image_bar:
			if (currentChat == null) {
				return true;
			}
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("to", currentChat);
			// set start intent to avoid restarting every rotation
			intent.putExtra("start", true);
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
			return true;
		case R.id.menu_capture_image_bar:
			if (currentChat == null) {
				return true;
			}

			mImageCaptureHandler = new ImageCaptureHandler(this, currentChat);
			mImageCaptureHandler.capture();

			return true;
		case R.id.menu_settings_bar:
			intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		case R.id.menu_logout_bar:
			mChatController.logout();
			IdentityController.logout();
			Intent finalIntent = new Intent(MainActivity.this, MainActivity.class);
			finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			mChatController = null;
			MainActivity.this.startActivity(finalIntent);
			finish();
			return true;
		case R.id.menu_invite_external:
			intent = new Intent(this, ExternalInviteActivity.class);
			startActivity(intent);
			return true;
		case R.id.menu_clear_messages:
			mChatController.deleteMessages(currentChat);
			return true;
		default:
			return false;

		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		SurespotLog.v(TAG, "onDestroy");
		if (mCacheServiceBound && mConnection != null) {
			unbindService(mConnection);
		}
		mChatController = null;
	}

	public static NetworkController getNetworkController() {
		return mNetworkController;
	}

	public static Context getContext() {
		return mContext;
	}

	public ChatController getChatController() {
		return mChatController;
	}

	public static Handler getMainHandler() {
		return mMainHandler;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			mMenuOverflow.performIdentifierAction(R.id.item_overflow, 0);
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	private void startWatchingExternalStorage() {
		mExternalStorageReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				SurespotLog.v(TAG, "Storage: " + intent.getData());
				updateExternalStorageState();
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addDataScheme("file");
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		registerReceiver(mExternalStorageReceiver, filter);
		updateExternalStorageState();
	}

	private void stopWatchingExternalStorage() {
		unregisterReceiver(mExternalStorageReceiver);
	}

	private void updateExternalStorageState() {
		String state = Environment.getExternalStorageState();
		SurespotLog.v(TAG, "updateExternalStorageState:  " + state);
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		}
		else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		}
		else {
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		handleExternalStorageState(mExternalStorageAvailable, mExternalStorageWriteable);
	}

	private void handleExternalStorageState(boolean externalStorageAvailable, boolean externalStorageWriteable) {

		enableMenuItems();

	}

	public void enableMenuItems() {

		if (mMenuItems != null) {
			for (MenuItem menuItem : mMenuItems) {
				if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {
					menuItem.setEnabled(mExternalStorageWriteable);

				}
			}
		}

	}
}
