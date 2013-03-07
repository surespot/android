package com.twofours.surespot.activities;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
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

	private static final int CORE_POOL_SIZE = 10;
	private static final int MAXIMUM_POOL_SIZE = Integer.MAX_VALUE;
	private static final int KEEP_ALIVE = 1;

	// create our own thread factory to handle message decryption where we have potentially hundreds of messages to decrypt
	// we need a tall queue and a slim pipe
	private static final ThreadFactory sThreadFactory = new ThreadFactory() {
		private final AtomicInteger mCount = new AtomicInteger(1);

		public Thread newThread(Runnable r) {
			return new Thread(r, "surespot #" + mCount.getAndIncrement());
		}
	};

	private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<Runnable>();

	/**
	 * An {@link Executor} that can be used to execute tasks in parallel.
	 */
	public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
			TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		Intent intent = getIntent();
		Utils.logIntent(TAG, intent);

		m401Handler = new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				if (!MainActivity.this.getNetworkController().isUnauthorized()) {
					MainActivity.this.getNetworkController().setUnauthorized(true);

					mChatController.logout();
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
					mCredentialCachingService.logout();
				}
				if (mNetworkController != null) {
					mNetworkController.logout();
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
		SurespotLog.v(TAG, "launch");

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
			mChatController.setCurrentChat(name);
		}

		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
			Utils.configureActionBar(this, "send", "select recipient", false);
			SurespotLog.v(TAG, "started from SEND");
			// need to select a user so put the chat controller in select mode
			mChatController.setCurrentChat(null);
			mChatController.setMode(ChatController.MODE_SELECT);
			mSet = true;
		}

		if (!mSet) {
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);
			String lastName = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
			if (lastName != null) {
				SurespotLog.v(TAG, "using LAST_CHAT");
				name = lastName;
			}
			mChatController.setCurrentChat(name);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");
		if (mChatController != null) {
			mChatController.onResume();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mChatController != null) {
			mChatController.onPause();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);

		Uri selectedImageUri = null;

		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:
			if (resultCode == RESULT_OK) {
				Intent intent = new Intent(this, ImageSelectActivity.class);
				intent.putExtra("source", ImageSelectActivity.SOURCE_EXISTING_IMAGE);
				String to = mChatController.getCurrentChat();
				if (to == null) {
					return;
				}
				intent.putExtra("to", to);
				intent.setData(data.getData());
				startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
			}
			break;

		case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE:
			if (resultCode == RESULT_OK) {
				selectedImageUri = data.getData();

				String to = data.getStringExtra("to");
				SurespotLog.v(TAG, "to: " + to);
				final String filename = data.getStringExtra("filename");
				if (selectedImageUri != null) {

					Utils.makeToast(this, getString(R.string.uploading_image));
					ChatUtils.uploadPictureMessageAsync(this, selectedImageUri, to, false, filename, new IAsyncCallback<Boolean>() {
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
		}
	}

	@Override
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		super.onCreateOptionsMenu(menu);
		SurespotLog.v(TAG, "onCreateOptionsMenu");

		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);

		mMenuItems.add(menu.findItem(R.id.menu_close_bar));
		mMenuItems.add(menu.findItem(R.id.menu_close));
		mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));
		mMenuItems.add(menu.findItem(R.id.menu_send_image));
		mMenuItems.add(menu.findItem(R.id.menu_capture_image_bar));
		mMenuItems.add(menu.findItem(R.id.menu_capture_image));

		if (Camera.getNumberOfCameras() == 0) {
			SurespotLog.v(TAG, "hiding capture image menu option");
			menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
		}

		if (mChatController != null) {
			mChatController.enableMenuItems();
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		switch (item.getItemId()) {
		case android.R.id.home:
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			// showUi(!mChatsShowing);
			mChatController.setCurrentChat(null);
			return true;
		case R.id.menu_close_bar:
		case R.id.menu_close:
			mChatController.closeTab();
			return true;
		case R.id.menu_send_image_bar:
		case R.id.menu_send_image:
			intent = new Intent();
			// TODO paid version allows any file
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			// Utils.configureActionBar(this, getString(R.string.select_image), mChatController.getCurrentChat(), false);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
					SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
			return true;
		case R.id.menu_capture_image_bar:
		case R.id.menu_capture_image:
			String to = mChatController.getCurrentChat();
			if (to == null) {
				return true;
			}
			// case R.id.menu_capture_image_menu:
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("source", ImageSelectActivity.SOURCE_CAPTURE_IMAGE);
			intent.putExtra("to", to);
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
			return true;
		case R.id.menu_settings_bar:
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		case R.id.menu_logout:
		case R.id.menu_logout_bar:
			mChatController.logout();
			IdentityController.logout();
			Intent finalIntent = new Intent(MainActivity.this, MainActivity.class);
			finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			mChatController = null;
			MainActivity.this.startActivity(finalIntent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
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

}
