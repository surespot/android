package com.twofours.surespot.activities;

import java.io.File;
import java.security.Security;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.acra.annotation.ReportsCrashes;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.viewpagerindicator.TitlePageIndicator;

@ReportsCrashes(formKey = "dHBRcnQzWFR5c0JwZW9tNEdOLW9oNHc6MQ")
public class MainActivity extends SherlockFragmentActivity {
	public static final String TAG = "MainActivity";

	private NotificationManager mNotificationManager;
	private static CredentialCachingService mCredentialCachingService;
	private static NetworkController mNetworkController;
	private static StateController mStateController;
	private static Context mContext;

	private static ChatController mChatController;

	private static final int CORE_POOL_SIZE = 5;
	private static final int MAXIMUM_POOL_SIZE = 10;
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
		SurespotLog.v(TAG, "onCreate, chatController: " + mChatController);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		setContentView(R.layout.activity_main);

		Intent intent = new Intent(this, CredentialCachingService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		SurespotApplication.setStartupIntent(getIntent());
		Utils.logIntent(TAG, getIntent());

		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
		mContext = getApplicationContext();

		SurespotConfiguration.LoadConfigProperties(mContext);

		intent = new Intent(this, CredentialCachingService.class);
		SurespotLog.v(TAG, "starting cache service");
		startService(intent);

		mStateController = new StateController();
	
		// create the chat controller here if we know we're not going to need to login
		// so that if we come back from a restart (for example a rotation), the automatically
		// created fragments have a chat controller instance
		
		if (!needsLogin()) {
			mChatController = new ChatController(MainActivity.this, getSupportFragmentManager(), (ViewPager) findViewById(R.id.pager),
					(TitlePageIndicator) findViewById(R.id.indicator));		
			mChatController.init();
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
				|| (intent.getBooleanExtra("401", false))
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

			// make sure these are there so startup code can execute
			mNetworkController = new NetworkController(MainActivity.this);
			startup();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void startup() {
		SurespotLog.v(TAG, "startup, chatController: " + mChatController);
		try {
			// device without GCM throws exception
			GCMRegistrar.checkDevice(this);
			GCMRegistrar.checkManifest(this);

			final String regId = GCMRegistrar.getRegistrationId(this);
			// boolean registered = GCMRegistrar.isRegistered(this);
			// boolean registeredOnServer = GCMRegistrar.isRegisteredOnServer(this);
			if (regId.equals("")) {
				SurespotLog.v(TAG, "Registering for GCM.");
				GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
			}
			else {
				SurespotLog.v(TAG, "GCM already registered.");
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "onCreate", e);
		}

		// NetworkController.unregister(this, regId);
		Intent intent = getIntent();
		// if we have any users or we don't need to create a user, figure out if we need to login
		if (IdentityController.hasIdentity() && !intent.getBooleanExtra("create", false)) {
			// if we have a message to the currently logged in user, set the from and start the chat activity
			if (needsLogin()) {
				String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
				SurespotLog.v(TAG, "need a (different) user, showing login");
				Intent newIntent = new Intent(this, LoginActivity.class);
				newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, messageTo);
				startActivityForResult(newIntent, SurespotConstants.IntentRequestCodes.LOGIN);
			}
			else {

				launch(getIntent());
			}

		}
		// otherwise show the signup activity
		else {
			SurespotLog.v(TAG, "starting signup activity");
			Intent newIntent = new Intent(this, SignupActivity.class);
			// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

			startActivity(newIntent);
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);

		Uri selectedImageUri = null;
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.LOGIN:
				launch(SurespotApplication.getStartupIntent());
				break;

			case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:
				Intent intent = new Intent(this, ImageSelectActivity.class);
				intent.putExtra("source", ImageSelectActivity.SOURCE_EXISTING_IMAGE);				
				intent.putExtra("to", mChatController.getCurrentChat());				
				intent.setData(data.getData());
				startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
				break;

			case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE:
				selectedImageUri = data.getData();
				String to = data.getStringExtra("to");
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
					break;
				}
			}
		}
		else {
			finish();
		}

	}

	private void launch(Intent intent) {
		SurespotLog.v(TAG, "launch, chatController: " + mChatController);
		
		//if we haven't created a chat controller before, create it now
		if (mChatController == null) {
			SurespotLog.v(TAG, "chat controller null, creating new chat controller");
			mChatController = new ChatController(MainActivity.this, getSupportFragmentManager(), (ViewPager) findViewById(R.id.pager),
					(TitlePageIndicator) findViewById(R.id.indicator));		
			mChatController.init();
		}

		// make sure the gcm is set

		// use case:
		// user signs-up without google account (unlikely)
		// user creates google account
		// user opens app again, we have session so neither login or add user is called (which would set the gcm)

		// so we need to upload the gcm here if we haven't already

		mNetworkController.registerGcmId(new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int arg0, String arg1) {
				SurespotLog.v(TAG, "GCM registered in surespot server");
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				SurespotLog.e(TAG, arg0.toString(), arg0);
			}
		});

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
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);

		}
		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
			Utils.configureActionBar(this, "send", "select recipient", false);
			SurespotLog.v(TAG, "started from SEND");
			// need to select a user so put the chat controller in select mode
			mChatController.setCurrentChat(null);
			mChatController.setMode(ChatController.MODE_SELECT);
			mSet = true;
		}

		// message received show chat activity for user
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {

			SurespotLog.v(TAG, "started from message, to: " + messageTo + ", from: " + messageFrom);
			name = messageFrom;
			Utils.configureActionBar(this, "surespot", IdentityController.getLoggedInUser(), false);
			mSet = true;
			mChatController.setCurrentChat(name);
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
		// TODO Auto-generated method stub
		super.onPause();
		if (mChatController != null) {
			mChatController.onPause();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);

		if (Camera.getNumberOfCameras() == 0) {
			SurespotLog.v(TAG, "hiding capture image menu option");
			menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
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
			// case R.id.menu_capture_image_menu:
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("source", ImageSelectActivity.SOURCE_CAPTURE_IMAGE);
			intent.putExtra("to", mChatController.getCurrentChat());
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);

			return true;
		case R.id.menu_settings_bar:
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SETTINGS);
			return true;
		case R.id.menu_logout:
		case R.id.menu_logout_bar:
			IdentityController.logout();
			intent = new Intent(MainActivity.this, MainActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			MainActivity.this.startActivity(intent);
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
		mChatController = null;
		unbindService(mConnection);
	}

	public static CredentialCachingService getCachingService() {

		return mCredentialCachingService;
	}

	public static void setCachingService(CredentialCachingService cachingService) {
		mCredentialCachingService = cachingService;
	}

	public static NetworkController getNetworkController() {
		return mNetworkController;
	}

	public static void setNetworkController(NetworkController networkController) {
		mNetworkController = networkController;
	}

	public static StateController getStateController() {
		return mStateController;
	}

	public static Context getContext() {
		return mContext;
	}

	public static void setChatController(ChatController chatController) {
		mChatController = chatController;
	}

	public static ChatController getChatController() {
		return mChatController;
	}

}
