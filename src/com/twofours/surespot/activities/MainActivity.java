package com.twofours.surespot.activities;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.billing.BillingActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.EmojiAdapter;
import com.twofours.surespot.chat.EmojiParser;
import com.twofours.surespot.chat.MainActivityLayout;
import com.twofours.surespot.chat.MainActivityLayout.OnMeasureListener;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageCaptureHandler;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.UIUtils;
import com.viewpagerindicator.TitlePageIndicator;

public class MainActivity extends SherlockFragmentActivity implements OnMeasureListener {
	public static final String TAG = "MainActivity";

	private static CredentialCachingService mCredentialCachingService = null;
	private static NetworkController mNetworkController = null;

	private static Context mContext = null;
	private static Handler mMainHandler = null;
	private ArrayList<MenuItem> mMenuItems = new ArrayList<MenuItem>();
	private IAsyncCallback<String> m401Handler;
	private ChatController mChatController = null;
	private boolean mCacheServiceBound;
	private Menu mMenuOverflow;
	private BroadcastReceiver mExternalStorageReceiver;
	private boolean mExternalStorageAvailable = false;
	private boolean mExternalStorageWriteable = false;
	private ImageView mHomeImageView;
	private InputMethodManager mImm;
	private KeyboardStateHandler mKeyboardStateHandler;
	private MainActivityLayout mActivityLayout;
	private EditText mEtMessage;
	private EditText mEtInvite;
	private Button mSendButton;
	private GridView mEmojiView;
	private boolean mKeyboardShowing;
	private int mEmojiHeight;
	private int mInitialHeightOffset;
	private ImageView mEmojiButton;
	private ImageView mQRButton;
	private Friend mCurrentFriend;
	private boolean mShowEmoji = false;
	boolean mKeyboardWasOpen = false;
	private int mOrientation;

	@Override
	protected void onNewIntent(Intent intent) {

		super.onNewIntent(intent);
		SurespotLog.v(TAG, "onNewIntent.");
		Utils.logIntent(TAG, intent);
		// handle case where we deleted the identity we were logged in as
		boolean deleted = intent.getBooleanExtra("deleted", false);

		if (deleted) {
			// if we have any users or we don't need to create a user, figure out if we need to login
			if (!IdentityController.hasIdentity() || intent.getBooleanExtra("create", false)) {
				// otherwise show the signup activity

				SurespotLog.v(TAG, "I was deleted and there are no other users so starting signup activity.");
				Intent newIntent = new Intent(this, SignupActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(newIntent);
				finish();
			} else {

				SurespotLog.v(TAG, "I was deleted and there are different users so starting login activity.");
				Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);
				newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(newIntent);
				finish();
			}
		} else {
			if (!processIntent(intent)) {
				launch(intent);
			}
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SurespotLog.v(TAG, "onCreate");

		Intent intent = getIntent();
		Utils.logIntent(TAG, intent);

		mImm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
		mOrientation = getResources().getConfiguration().orientation;

		// PROD Gingerbread does not like FLAG_SECURE
		if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.FROYO
				|| android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);
		}

		mContext = this;

		m401Handler = new IAsyncCallback<String>() {

			@Override
			public void handleResponse(final String message) {
				SurespotLog.v(TAG, "Got 401, checking authorization.");
				if (!MainActivity.this.getNetworkController().isUnauthorized()) {
					MainActivity.this.getNetworkController().setUnauthorized(true);
					IdentityController.logout();

					SurespotLog.v(TAG, "Got 401, launching login intent.");
					Intent intent = new Intent(MainActivity.this, LoginActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
					finish();

					if (!TextUtils.isEmpty(message)) {
						Runnable runnable = new Runnable() {

							@Override
							public void run() {
								Utils.makeToast(MainActivity.this, message);

							}
						};

						MainActivity.this.runOnUiThread(runnable);
					}
				}
			}
		};

		if (!processIntent(intent)) {
			// we're loading so build the ui
			setContentView(R.layout.activity_main);

			mHomeImageView = (ImageView) findViewById(android.R.id.home);
			if (mHomeImageView == null) {
				mHomeImageView = (ImageView) findViewById(R.id.abs__home);
			}

			setHomeProgress(true);

			SurespotLog.v(TAG, "binding cache service");
			Intent cacheIntent = new Intent(this, CredentialCachingService.class);
			bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);
			// create the chat controller here if we know we're not going to need to login
			// so that if we come back from a restart (for example a rotation), the automatically
			// created fragments have a chat controller instance

			mMainHandler = new Handler(getMainLooper());
			mNetworkController = new NetworkController(MainActivity.this, m401Handler);

			mChatController = new ChatController(MainActivity.this, mNetworkController, getSupportFragmentManager(), m401Handler,
					new IAsyncCallback<Boolean>() {
						@Override
						public void handleResponse(Boolean inProgress) {
							setHomeProgress(inProgress);
						}
					}, new IAsyncCallback<Void>() {

						@Override
						public void handleResponse(Void result) {
							handleSendIntent();

						}
					}, new IAsyncCallback<Friend>() {

						@Override
						public void handleResponse(Friend result) {
							handleTabChange(result);

						}
					});

			mActivityLayout = (MainActivityLayout) findViewById(R.id.chatLayout);
			mActivityLayout.setOnSoftKeyboardListener(this);
			mActivityLayout.setMainActivity(this);

			final TitlePageIndicator titlePageIndicator = (TitlePageIndicator) findViewById(R.id.indicator);

			mKeyboardStateHandler = new KeyboardStateHandler();
			mActivityLayout.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardStateHandler);

			String autoInviteUser = getAutoInviteUser(intent);
			if (autoInviteUser != null) {
				SurespotLog.v(TAG, "auto inviting user: %s", autoInviteUser);
			}

			mChatController.init((ViewPager) findViewById(R.id.pager), titlePageIndicator, mMenuItems, autoInviteUser);
			setupChatControls();
		}

	}

	private boolean processIntent(Intent intent) {
		// if we have any users or we don't need to create a user, figure out if we need to login
		if (!IdentityController.hasIdentity() || intent.getBooleanExtra("create", false)) {
			// otherwise show the signup activity

			SurespotLog.v(TAG, "starting signup activity");
			Intent newIntent = new Intent(this, SignupActivity.class);
			newIntent.putExtra("autoinviteuri", intent.getData());
			newIntent.setAction(intent.getAction());
			newIntent.setType(intent.getType());

			Bundle extras = intent.getExtras();
			if (extras != null) {
				newIntent.putExtras(extras);
			}

			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(newIntent);

			finish();
			return true;
		} else {
			if (needsLogin(intent)) {
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
				newIntent.putExtra("autoinviteuri", intent.getData());
				newIntent.setAction(intent.getAction());
				newIntent.setType(intent.getType());

				Bundle extras = intent.getExtras();
				if (extras != null) {
					newIntent.putExtras(extras);
				}

				newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

				startActivity(newIntent);
				finish();
				return true;

			}
		}

		return false;
	}

	private String getAutoInviteUser(Intent intent) {
		Uri uri = intent.getData();
		boolean dataUri = true;

		if (uri == null) {
			Bundle extras = intent.getExtras();
			if (extras != null) {
				uri = extras.getParcelable("autoinviteuri");
				dataUri = false;
			}
		}

		if (uri == null) {
			return null;
		}

		String uriPath = uri.getPath();
		if (uriPath != null) {
			if (uriPath.startsWith("/autoinvite")) {

				List<String> segments = uri.getPathSegments();

				if (segments.size() > 1) {
					if (dataUri) {
						intent.setData(null);
					} else {
						intent.removeExtra("autoinviteurl");
					}

					return segments.get(1);
				}
			}
		}

		return null;
	}

	class KeyboardStateHandler implements OnGlobalLayoutListener {
		@Override
		public void onGlobalLayout() {
			final View activityRootView = findViewById(R.id.chatLayout);
			int activityHeight = activityRootView.getHeight();
			int heightDelta = activityRootView.getRootView().getHeight() - activityHeight;

			if (mInitialHeightOffset == 0) {
				mInitialHeightOffset = heightDelta;
			}

			// set the emoji view to the keyboard height
			mEmojiHeight = Math.abs(heightDelta - mInitialHeightOffset);

			SurespotLog.v(TAG, "onGlobalLayout, root Height: %d, activity height: %d, emoji: %d, initialHeightOffset: %d", activityRootView.getRootView()
					.getHeight(), activityRootView.getHeight(), heightDelta, mInitialHeightOffset);
		}
	}

	private void setupChatControls() {
		mSendButton = (Button) findViewById(R.id.bSend);
		mSendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				if (mCurrentFriend != null) {
					if (mEtMessage.getText().toString().length() > 0 && !mChatController.isFriendDeleted()) {
						sendMessage();
					} else {
						// go to friends
						mChatController.setCurrentChat(null);
					}
				} else {
					inviteFriend();
				}
			}
		});

		mEmojiView = (GridView) findViewById(R.id.fEmoji);
		mEmojiView.setAdapter(new EmojiAdapter(this));

		mEmojiView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {

				int start = mEtMessage.getSelectionStart();
				int end = mEtMessage.getSelectionEnd();
				CharSequence insertText = EmojiParser.getInstance().getEmojiChar(position);
				mEtMessage.getText().replace(Math.min(start, end), Math.max(start, end), insertText);
				mEtMessage.setSelection(Math.max(start, end) + insertText.length());

			}
		});

		mEmojiButton = (ImageView) findViewById(R.id.bEmoji);
		mEmojiButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				showEmoji();
			}
		});

		mEmojiButton.setImageResource(EmojiParser.getInstance().getRandomEmojiResource());

		mQRButton = (ImageView) findViewById(R.id.bQR);
		mQRButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				UIUtils.showQRDialog(MainActivity.this);
			}
		});

		mSendButton.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if (mEtMessage.getText().toString().length() == 0) {
					mChatController.closeTab();
				}
				return true;
			}
		});
		mEtMessage = (EditText) findViewById(R.id.etMessage);
		mEtMessage.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;

				if (mCurrentFriend != null) {
					if (actionId == EditorInfo.IME_ACTION_SEND) {
						sendMessage();
						handled = true;
					}
				}

				return handled;
			}
		});

		TextWatcher tw = new ChatTextWatcher();
		mEtMessage.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_MESSAGE_LENGTH) });
		mEtMessage.addTextChangedListener(tw);
		mEtMessage.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {

				if (!mKeyboardShowing) {
					mEtMessage.requestFocus();
					showSoftKeyboard(v);

					mShowEmoji = false;
					return true;
				} else {
					return false;
				}

			}
		});

		mEtInvite = (EditText) findViewById(R.id.etInvite);
		mEtInvite.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter() });
		mEtInvite.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;

				if (mCurrentFriend == null) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						inviteFriend();
						handled = true;
					}
				}
				return handled;
			}
		});
	}

	private boolean needsLogin(Intent intent) {
		String user = IdentityController.getLoggedInUser();

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
			launch(getIntent());
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private void launch(Intent intent) {
		// SurespotLog.v(TAG, "launch, mChatController: " + mChatController);

		String action = intent.getAction();
		String type = intent.getType();
		String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
		String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

		boolean mSet = false;
		String name = null;

		// if we're coming from an invite notification, or we need to send to someone
		// then display friends
		if (SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType)) {
			SurespotLog.v(TAG, "started from invite");
			mSet = true;
			Utils.clearIntent(intent);
			Utils.configureActionBar(this, getString(R.string.surespot), IdentityController.getLoggedInUser(), false);
		}

		// message received show chat activity for user
		if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {

			SurespotLog.v(TAG, "started from message, to: " + messageTo + ", from: " + messageFrom);
			name = messageFrom;
			Utils.configureActionBar(this, getString(R.string.surespot), IdentityController.getLoggedInUser(), false);
			mSet = true;
			Utils.clearIntent(intent);
			Utils.logIntent(TAG, intent);

			if (mChatController != null) {
				mChatController.setCurrentChat(name);

			}
		}

		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
			Utils.configureActionBar(this, getString(R.string.send), getString(R.string.main_action_bar_right), false);
			SurespotLog.v(TAG, "started from SEND");
			// need to select a user so put the chat controller in select mode

			if (mChatController != null) {
				mChatController.setCurrentChat(null);
				mChatController.setMode(ChatController.MODE_SELECT);
			}
			mSet = true;
		}

		if (!mSet) {
			Utils.configureActionBar(this, getString(R.string.surespot), IdentityController.getLoggedInUser(), false);
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
				if (selectedImageUri != null) {

					// Utils.makeToast(this, getString(R.string.uploading_image));
					ChatUtils.uploadPictureMessageAsync(this, mChatController, mNetworkController, selectedImageUri, to, false, new IAsyncCallback<Boolean>() {
						@Override
						public void handleResponse(Boolean result) {
							if (!result) {

								Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_image));
							}
						}
					});
				}
			}
			break;
		case SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE:
			if (resultCode == RESULT_OK) {
				mImageCaptureHandler.handleResult(this);
				mImageCaptureHandler = null;
			}
			break;

		case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FRIEND_IMAGE:
			if (resultCode == Activity.RESULT_OK) {
				Uri selectedImageUri = data.getData();

				final String to = data.getStringExtra("to");

				SurespotLog.v(TAG, "to: " + to);
				if (selectedImageUri != null) {

					// Utils.makeToast(this, getString(R.string.uploading_image));
					ChatUtils.uploadFriendImageAsync(this, getNetworkController(), selectedImageUri, to, new IAsyncCallbackTriplet<String, String, String>() {
						@Override
						public void handleResponse(String url, String version, String iv) {
							if (url == null) {
								Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_friend_image));
							} else {
								mChatController.setImageUrl(to, url, version, iv);

							}
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
		mMenuOverflow = menu;

		mMenuItems.add(menu.findItem(R.id.menu_close_bar));
		mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));

		MenuItem captureItem = menu.findItem(R.id.menu_capture_image_bar);
		if (hasCamera()) {
			mMenuItems.add(captureItem);
			captureItem.setEnabled(FileUtils.isExternalStorageMounted());
		} else {
			SurespotLog.v(TAG, "hiding capture image menu option");
			menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
		}

		mMenuItems.add(menu.findItem(R.id.menu_clear_messages));

		if (mChatController != null) {
			mChatController.enableMenuItems(mCurrentFriend);
		}

		// TODO PROD enable for release
		// menu.findItem(R.id.menu_invite_external).setEnabled(false);

		enableImageMenuItems();
		return true;
	}

	@SuppressLint("NewApi")
	private boolean hasCamera() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
			return Camera.getNumberOfCameras() > 0;
		} else {
			PackageManager pm = this.getPackageManager();
			return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
		}
	}

	public void uploadFriendImage(String name) {
		MessageImageDownloader.evictCache();
		Intent intent = new Intent(this, ImageSelectActivity.class);
		intent.putExtra("to", name);
		intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_SMALL);
		// set start intent to avoid restarting every rotation
		intent.putExtra("start", true);
		startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FRIEND_IMAGE);

	}

	private ImageCaptureHandler mImageCaptureHandler;

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		final String currentChat = mChatController.getCurrentChat();
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

			// can't send images to deleted folk
			if (mCurrentFriend != null && mCurrentFriend.isDeleted()) {
				return true;
			}

			setHomeProgress(true);
			new AsyncTask<Void, Void, Void>() {
				protected Void doInBackground(Void... params) {
					MessageImageDownloader.evictCache();
					Intent intent = new Intent(MainActivity.this, ImageSelectActivity.class);
					intent.putExtra("to", currentChat);
					intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_LARGE);
					// set start intent to avoid restarting every rotation
					intent.putExtra("start", true);
					startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
					return null;
				};

			}.execute();

			return true;
		case R.id.menu_capture_image_bar:
			if (currentChat == null) {
				return true;
			}
			// can't send images to deleted folk
			if (mCurrentFriend != null && mCurrentFriend.isDeleted()) {
				return true;
			}

			setHomeProgress(true);
			new AsyncTask<Void, Void, Void>() {
				protected Void doInBackground(Void... params) {

					MessageImageDownloader.evictCache();
					mImageCaptureHandler = new ImageCaptureHandler(currentChat);
					mImageCaptureHandler.capture(MainActivity.this);
					return null;
				}
			}.execute();

			return true;
		case R.id.menu_settings_bar:

			new AsyncTask<Void, Void, Void>() {
				protected Void doInBackground(Void... params) {

					Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
					startActivity(intent);
					return null;
				}
			}.execute();
			return true;
		case R.id.menu_logout_bar:
			mChatController.logout();
			IdentityController.logout();

			// new AsyncTask<Void, Void, Void>() {
			// protected Void doInBackground(Void... params) {

			Intent finalIntent = new Intent(MainActivity.this, LoginActivity.class);
			finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			mChatController = null;
			MainActivity.this.startActivity(finalIntent);
			finish();
			// return null;
			// }
			// }.execute();
			return true;
		case R.id.menu_invite_external:

			new AsyncTask<Void, Void, Void>() {
				protected Void doInBackground(Void... params) {

					Intent intent = new Intent(MainActivity.this, ExternalInviteActivity.class);
					startActivity(intent);
					return null;
				}
			}.execute();
			return true;
		case R.id.menu_clear_messages:
			SharedPreferences sp = getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
			boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
			if (confirm) {
				UIUtils.createAndShowConfirmationDialog(this, getString(R.string.delete_all_confirmation), getString(R.string.delete_all_title),
						getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
							public void handleResponse(Boolean result) {
								if (result) {
									mChatController.deleteMessages(currentChat);
								}
							};
						});
			} else {
				mChatController.deleteMessages(currentChat);
			}

			return true;
		case R.id.menu_pwyl:

			new AsyncTask<Void, Void, Void>() {
				protected Void doInBackground(Void... params) {

					Intent intent = new Intent(MainActivity.this, BillingActivity.class);
					startActivity(intent);
					return null;
				}
			}.execute();
			return true;
		default:
			return false;

		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		MessageImageDownloader.evictCache();
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
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		handleExternalStorageState(mExternalStorageAvailable, mExternalStorageWriteable);
	}

	private void handleExternalStorageState(boolean externalStorageAvailable, boolean externalStorageWriteable) {

		enableImageMenuItems();

	}

	public void enableImageMenuItems() {

		if (mMenuItems != null) {
			for (MenuItem menuItem : mMenuItems) {
				if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {

					menuItem.setEnabled(mExternalStorageWriteable);

				}
			}
		}

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);
		SurespotLog.v(TAG, "onSaveInstanceState");
		if (mImageCaptureHandler != null) {
			SurespotLog.v(TAG, "onSaveInstanceState saving imageCaptureHandler, to: %s, path: %s", mImageCaptureHandler.getTo(),
					mImageCaptureHandler.getImagePath());
			outState.putParcelable("imageCaptureHandler", mImageCaptureHandler);
		}

		if (mKeyboardShowing) {
			SurespotLog.v(TAG, "onSaveInstanceState saving mKeyboardShowing: %b", mKeyboardShowing);
			outState.putBoolean("keyboardShowing", mKeyboardShowing);
		}

		if (mInitialHeightOffset > 0) {
			SurespotLog.v(TAG, "onSaveInstanceState saving heightOffset: %d", mInitialHeightOffset);
			outState.putInt("heightOffset", mInitialHeightOffset);
		}

	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		SurespotLog.v(TAG, "onRestoreInstanceState");
		mImageCaptureHandler = savedInstanceState.getParcelable("imageCaptureHandler");
		if (mImageCaptureHandler != null) {
			SurespotLog.v(TAG, "onRestoreInstanceState restored imageCaptureHandler, to: %s, path: %s", mImageCaptureHandler.getTo(),
					mImageCaptureHandler.getImagePath());
		}

		mKeyboardShowing = savedInstanceState.getBoolean("keyboardShowing");

		mInitialHeightOffset = savedInstanceState.getInt("heightOffset");
	}

	private void setHomeProgress(boolean inProgress) {
		if (mHomeImageView == null) {
			return;
		}

		SurespotLog.v(TAG, "progress status changed to: %b", inProgress);
		if (inProgress) {

			Animation a = AnimationUtils.loadAnimation(this, R.anim.progress_anim);
			a.setDuration(1000);
			mHomeImageView.clearAnimation();
			mHomeImageView.startAnimation(a);
		} else {
			mHomeImageView.clearAnimation();
		}
	}

	public synchronized void hideSoftKeyboard() {
		SurespotLog.v(TAG, "hideSoftkeyboard");
		mKeyboardShowing = false;

		mImm.hideSoftInputFromWindow(mEtMessage.getWindowToken(), 0, new ResultReceiver(null) {
			@Override
			protected void onReceiveResult(int resultCode, Bundle resultData) {
				if (resultCode != InputMethodManager.RESULT_HIDDEN && resultCode != InputMethodManager.RESULT_UNCHANGED_HIDDEN) {
					mKeyboardShowing = true;
				}
			}
		});
	}

	private synchronized void showSoftKeyboard(View view) {
		SurespotLog.v(TAG, "showSoftkeyboard");
		mKeyboardShowing = true;
		mImm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
		mImm.showSoftInput(view, 0, new ResultReceiver(null) {
			@Override
			protected void onReceiveResult(int resultCode, Bundle resultData) {
				if ((resultCode != InputMethodManager.RESULT_SHOWN) && (resultCode != InputMethodManager.RESULT_UNCHANGED_SHOWN)) {
					mKeyboardShowing = false;
				}
			}
		});
	}

	private void showEmoji() {
		SurespotLog.v(TAG, "keyboardState,  showing: %b", mKeyboardShowing);
		if (mKeyboardShowing) {

			SurespotLog.v(TAG, "keyboardState,  hidingKeyboard and showing emoji");

			mShowEmoji = true;
			// mEmojiView.setVisibility(View.VISIBLE);
			hideSoftKeyboard();
			mKeyboardWasOpen = true;
		} else {

			int visibility = mEmojiView.getVisibility();
			if (visibility == View.VISIBLE) {
				SurespotLog.v(TAG, "keyboardState,  hiding emoji");

				if (mKeyboardWasOpen) {
					SurespotLog.v(TAG, "keyboardState,  showing keyboard");
					showSoftKeyboard(mEtMessage);
					mKeyboardWasOpen = false;
				} else {
					mEmojiView.setVisibility(View.GONE);
				}

				mShowEmoji = false;
				//
			} else {
				// if we don't have a height, get it
				// if (mEmojiHeight == 0) {
				//
				// showSoftKeyboard(mEtMessage);
				// hideSoftKeyboard();
				// }
				//

				SurespotLog.v(TAG, "keyboardState,  showing emoji");
				mEmojiView.setVisibility(View.VISIBLE);
				mShowEmoji = true;

			}

		}

	}

	class ChatTextWatcher implements TextWatcher {

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {

			// mEtMessage.removeTextChangedListener(this);

			setButtonText();
			// mEtMessage.setText(EmojiParser.getInstance().addEmojiSpans(s.toString()));
			// mEtMessage.addTextChangedListener(this);
		}

		@Override
		public void afterTextChanged(Editable s) {
			// mEtMessage.setSelection(s.length());
		}
	}

	// populate the edit box
	public void handleSendIntent() {
		Intent intent = this.getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		Bundle extras = intent.getExtras();

		if (action == null) {
			return;
		}

		if (action.equals(Intent.ACTION_SEND)) {
			Utils.configureActionBar(this, getString(R.string.surespot), IdentityController.getLoggedInUser(), false);

			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = intent.getExtras().get(Intent.EXTRA_TEXT).toString();
				SurespotLog.v(TAG, "received action send, data: " + sharedText);
				mEtMessage.append(sharedText);
				// requestFocus();
				// clear the intent

			}

			else {
				if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

					final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

					// Utils.makeToast(getActivity(), getString(R.string.uploading_image));

					SurespotLog.v(TAG, "received image data, upload image, uri: " + imageUri);

					ChatUtils.uploadPictureMessageAsync(this, mChatController, mNetworkController, imageUri, mCurrentFriend.getName(), true,
							new IAsyncCallback<Boolean>() {

								@Override
								public void handleResponse(final Boolean result) {
									SurespotLog.v(TAG, "upload picture response: " + result);

									if (!result) {

										Runnable runnable = new Runnable() {

											@Override
											public void run() {
												Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_image));

											}
										};

										getMainHandler().post(runnable);

									}

									// scrollToEnd();
								}
							});
					// }
				}

				else {
					if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
						// TODO implement
					}
				}

			}
			Utils.clearIntent(getIntent());
		}
	}

	private void sendMessage() {
		final String message = mEtMessage.getText().toString();
		mChatController.sendMessage(message, SurespotConstants.MimeTypes.TEXT);

		//
		TextKeyListener.clear(mEtMessage.getText());

		// scroll to end
		// scrollToEnd();
	}

	public boolean backButtonPressed() {
		boolean handled = false;
		SurespotLog.v(TAG, "backButtonPressed");
		if (mKeyboardShowing) {

			hideSoftKeyboard();
			mEmojiView.setVisibility(View.GONE);
			mShowEmoji = false;
			handled = true;
		} else {

			if (mEmojiView.getVisibility() == View.VISIBLE) {
				mEmojiView.setVisibility(View.GONE);
				mShowEmoji = false;
				handled = true;
			}
		}

		return handled;
	}

	@Override
	public void onLayoutMeasure() {
		SurespotLog.v(TAG, "onLayoutMeasure, emoji height: %d", mEmojiHeight);
		if (mShowEmoji) {

			if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
				mEmojiHeight = 200;
			}

			if (mEmojiHeight > 0) {
				android.view.ViewGroup.LayoutParams layoutParams = mEmojiView.getLayoutParams();
				layoutParams.height = mEmojiHeight;
			}

			mEmojiView.setVisibility(View.VISIBLE);
		} else {
			mEmojiView.setVisibility(View.GONE);
		}

	}

	private void inviteFriend() {

		final String friend = mEtInvite.getText().toString();

		if (friend.length() > 0) {
			if (friend.equals(IdentityController.getLoggedInUser())) {
				// TODO let them be friends with themselves?
				Utils.makeToast(this, getString(R.string.friend_self_error));
				return;
			}

			setHomeProgress(true);
			mNetworkController.invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String arg0) {
					setHomeProgress(false);
					TextKeyListener.clear(mEtInvite.getText());
					if (mChatController.getFriendAdapter().addFriendInvited(friend)) {
						Utils.makeToast(MainActivity.this, getString(R.string.has_been_invited, friend));
					} else {
						Utils.makeToast(MainActivity.this, getString(R.string.has_accepted, friend));
					}

				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					setHomeProgress(false);
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
						case 404:
							Utils.makeToast(MainActivity.this, getString(R.string.user_does_not_exist));
							break;
						case 409:
							Utils.makeToast(MainActivity.this, getString(R.string.you_are_already_friends));
							break;
						case 403:
							Utils.makeToast(MainActivity.this, getString(R.string.already_invited));
							break;
						default:
							SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
							Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
						}
					} else {
						SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
						Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
					}
				}
			});
		}
	}

	private void setButtonText() {
		if (mCurrentFriend == null) {
			mSendButton.setText(R.string.invite);
		} else {
			if (mCurrentFriend.isDeleted()) {
				mSendButton.setText(R.string.home);
			} else {
				if (mEtMessage.getText().length() > 0) {
					mSendButton.setText(R.string.send);
				} else {
					mSendButton.setText(R.string.home);
				}
			}

		}
	}

	private void handleTabChange(Friend friend) {
		// SurespotLog.v(TAG, "cursor position %d", mEtMessage.getSelectionStart());
		if (friend == null) {
			mEmojiButton.setVisibility(View.GONE);
			mEtMessage.setVisibility(View.GONE);
			mEtInvite.setVisibility(View.VISIBLE);
			mEmojiView.setVisibility(View.GONE);
			mShowEmoji = false;
			mQRButton.setVisibility(View.VISIBLE);
			mEtInvite.requestFocus();

		} else {
			if (friend.isDeleted()) {
				mEmojiButton.setVisibility(View.GONE);
				mEtMessage.setVisibility(View.GONE);
				mEmojiView.setVisibility(View.GONE);
				hideSoftKeyboard();
				mShowEmoji = false;
			} else {
				mEtMessage.setVisibility(View.VISIBLE);
				mEmojiButton.setVisibility(View.VISIBLE);
			}

			mEtInvite.setVisibility(View.GONE);
			mQRButton.setVisibility(View.GONE);
			mEtMessage.requestFocus();
		}

		mCurrentFriend = friend;
		setButtonText();

	}
}
