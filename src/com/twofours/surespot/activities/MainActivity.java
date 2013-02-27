package com.twofours.surespot.activities;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatPagerAdapter;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.viewpagerindicator.TitlePageIndicator;

public class MainActivity extends SherlockFragmentActivity {
	public static final String TAG = "MainActivity";

	private ChatPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private BroadcastReceiver mMessageBroadcastReceiver;
	private ChatController mChatController;
	private CredentialCachingService mCredentialCachingService;
	private TitlePageIndicator mIndicator;
	private FriendAdapter mMainAdapter;

	private static final int REQUEST_SETTINGS = 1;
	private MultiProgressDialog mMpdInviteFriend;
	private BroadcastReceiver mInvitationReceiver;
	private BroadcastReceiver InviteResponseReceiver;

	private ListView mListView;
	private NotificationManager mNotificationManager;
	private boolean mChatsShowing;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		setContentView(R.layout.activity_main);

		Intent intent = new Intent(this, CredentialCachingService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		SurespotApplication.setStartupIntent(getIntent());
		Utils.logIntent(TAG, getIntent());

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
			SurespotLog.v(TAG, "caching service bound");
			CredentialCachingBinder binder = (CredentialCachingBinder) service;
			mCredentialCachingService = binder.getService();

			// make sure these are there so startup code can execute
			SurespotApplication.setCachingService(mCredentialCachingService);
			SurespotApplication.setNetworkController(new NetworkController(MainActivity.this));
			startup();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void startup() {
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

			// if we have a current user we're logged in
			String user = IdentityController.getLoggedInUser();

			String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
			String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);

			SurespotLog.v(TAG, "user: " + user);
			SurespotLog.v(TAG, "type: " + notificationType);
			SurespotLog.v(TAG, "messageTo: " + messageTo);

			// if we have a message to the currently logged in user, set the from and start the chat activity
			if ((user == null)
					|| (intent.getBooleanExtra("401", false))
					|| ((SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)
							|| SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE
								.equals(notificationType)) && (!messageTo.equals(user)))) {

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
		if (requestCode == SurespotConstants.IntentRequestCodes.LOGIN) {
			if (resultCode == RESULT_OK) {
				launch(SurespotApplication.getStartupIntent());
			}
			else {
				finish();
			}
		}

	}

	private void launch(Intent intent) {
		SurespotLog.v(TAG, "launch");

		NetworkController networkController = SurespotApplication.getNetworkController();
		if (networkController != null) {
			// make sure the gcm is set

			// use case:
			// user signs-up without google account (unlikely)
			// user creates google account
			// user opens app again, we have session so neither login or add user is called (which would set the gcm)

			// so we need to upload the gcm here if we haven't already

			networkController.registerGcmId(new AsyncHttpResponseHandler() {

				@Override
				public void onSuccess(int arg0, String arg1) {
					SurespotLog.v(TAG, "GCM registered in surespot server");
				}

				@Override
				public void onFailure(Throwable arg0, String arg1) {
					SurespotLog.e(TAG, arg0.toString(), arg0);
				}
			});
		}

		String action = intent.getAction();
		String type = intent.getType();

		String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
		String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

		String lastName = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);

		boolean mSet = false;
		String name = null;

		// if we're coming from an invite notification, or we need to send to someone
		// then display friends
		if (SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType)
				|| SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType)
				|| (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
			mChatsShowing = false;

			Utils.configureActionBar(this, "send", "select recipient", true);
			mSet = true;
		}

		// message received show chat activity for user
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {

			SurespotLog.v(TAG, "found chat name, starting chat activity, to: " + messageTo + ", from: " + messageFrom);
			name = messageFrom;
			mChatsShowing = true;
			Utils.configureActionBar(this, "spots", IdentityController.getLoggedInUser(), true);
			mSet = true;
		}

		if (!mSet) {
			if (lastName == null) {

				mChatsShowing = false;
				Utils.configureActionBar(this, "home", IdentityController.getLoggedInUser(), true);
				name = lastName;
			}
			else {
				mChatsShowing = true;
				Utils.configureActionBar(this, "spots", IdentityController.getLoggedInUser(), true);
			}
		}

		SurespotApplication.setChatController(new ChatController(MainActivity.this, getSupportFragmentManager(), name));
		mChatController = SurespotApplication.getChatController();
		buildChatUi();
		buildFriendUi();
		mChatController.onResume();

	}

	private void showUi(boolean chats) {
		if (mChatsShowing && !chats) {
			mChatsShowing = false;
			findViewById(R.id.chatLayout).setVisibility(View.GONE);
			findViewById(R.id.friendLayout).setVisibility(View.VISIBLE);
			// clear invite response notifications
			mNotificationManager.cancel(IdentityController.getLoggedInUser(),
					SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
			Utils.setActionBarTitles(this, "home", IdentityController.getLoggedInUser());
		}
		else {
			if (!mChatsShowing && chats) {
				mChatsShowing = true;
				findViewById(R.id.chatLayout).setVisibility(View.VISIBLE);
				findViewById(R.id.friendLayout).setVisibility(View.GONE);
				Utils.setActionBarTitles(this, "spots", IdentityController.getLoggedInUser());
			}
		}
	}

	private void buildFriendUi() {			
		mMainAdapter = mChatController.getFriendAdapter();

		mMpdInviteFriend = new MultiProgressDialog(this, "inviting friend", 750);

		mListView = (ListView) findViewById(R.id.main_list);
		mListView.setEmptyView(findViewById(R.id.progressBar));
		// findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

		// click on friend to join chat
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Friend friend = (Friend) mMainAdapter.getItem(position);
				if (friend.isFriend()) {
					mChatController.setCurrentChat(friend.getName());
					showUi(true);
					int wantedPosition = mPagerAdapter.getChatFragmentPosition(friend.getName());
					if (wantedPosition != mViewPager.getCurrentItem()) {
						mViewPager.setCurrentItem(wantedPosition, true);
					}
				}
			}
		});

		Button addFriendButton = (Button) findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		EditText editText = (EditText) findViewById(R.id.etFriend);
		editText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					inviteFriend();
					handled = true;
				}
				return handled;
			}
		});

		// TODO adapter observer
		((ListView) findViewById(R.id.main_list)).setEmptyView(findViewById(R.id.main_list_empty));
		mListView.setAdapter(mMainAdapter);
		findViewById(R.id.progressBar).setVisibility(View.GONE);

	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) findViewById(R.id.etFriend));
		final String friend = etFriend.getText().toString();

		if (friend.length() > 0) {
			if (friend.equals(IdentityController.getLoggedInUser())) {
				// TODO let them be friends with themselves?
				Utils.makeToast(this, "You can't be friends with yourself, bro.");
				return;
			}

			mMpdInviteFriend.incrProgress();
			SurespotApplication.getNetworkController().invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String arg0) { // TODO
																		// indicate
																		// in
																		// the
																		// UI
					// that the request is
					// pending somehow
					TextKeyListener.clear(etFriend.getText());
					mMainAdapter.addFriendInvited(friend);
					Utils.makeToast(MainActivity.this, friend + " has been invited to be your friend.");
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
						case 404:
							Utils.makeToast(MainActivity.this, "User does not exist.");
							break;
						case 409:
							Utils.makeToast(MainActivity.this, "You are already friends.");
							break;
						case 403:
							Utils.makeToast(MainActivity.this, "You have already invited this user.");
							break;
						default:
							SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
							Utils.makeToast(MainActivity.this, "Could not invite friend, please try again later.");
						}
					}
					else {
						SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
						Utils.makeToast(MainActivity.this, "Could not invite friend, please try again later.");
					}
				}

				@Override
				public void onFinish() {
					mMpdInviteFriend.decrProgress();
				}
			});
		}
	}

	private void buildChatUi() {

		SurespotLog.v(TAG, "buildChatUi");

		String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		SurespotLog.v(TAG, "Intent contained name: " + name);

		// if we don't have an intent, see if we have saved chat
		if (name == null) {
			name = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
		}

		mPagerAdapter = mChatController.getChatPagerAdapter();

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mIndicator = (TitlePageIndicator) findViewById(R.id.indicator);
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				SurespotLog.v(TAG, "onPageSelected, position: " + position);
				String name = mPagerAdapter.getChatName(position);
				mChatController.setCurrentChat(name);

			}
		});
		mViewPager.setOffscreenPageLimit(2);

		findViewById(R.id.chatLayout).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		SurespotLog.v(TAG, "onResume");
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
			showUi(!mChatsShowing);
			return true;
		case R.id.menu_close_bar:
		case R.id.menu_close:
			closeTab(mViewPager.getCurrentItem());
			return true;

		case R.id.menu_send_image_bar:
		case R.id.menu_send_image:
			intent = new Intent();
			// TODO paid version allows any file
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			Utils.configureActionBar(this, getString(R.string.select_image), mChatController.getCurrentChat(), true);
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

	private void closeTab(int position) {
		// TODO remove saved messages

		if (mPagerAdapter.getCount() == 1) {
			mPagerAdapter.removeChat(0, false);
			showUi(false);
		}
		else {

			mPagerAdapter.removeChat(position, true);
			// when removing the 0 tab, onPageSelected is not fired for some reason so we need to set this stuff
			String name = mPagerAdapter.getChatName(mViewPager.getCurrentItem());
			// mChatController.setCurrentChat(name);

			// if they explicitly close the tab, remove the adapter
			mChatController.destroyChatAdapter(name);
			mIndicator.notifyDataSetChanged();
		}
	}

}
