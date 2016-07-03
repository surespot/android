package com.twofours.surespot.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.billing.BillingActivity;
import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.EmojiAdapter;
import com.twofours.surespot.chat.EmojiParser;
import com.twofours.surespot.chat.MainActivityLayout;
import com.twofours.surespot.chat.MainActivityLayout.OnMeasureListener;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageCaptureHandler;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.services.CommunicationService;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.twofours.surespot.services.ITransmissionServiceListener;
import com.twofours.surespot.services.RegistrationIntentService;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.UIUtils;
import com.twofours.surespot.voice.VoiceController;
import com.viewpagerindicator.TitlePageIndicator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class MainActivity extends Activity implements OnMeasureListener {
    public static final String TAG = "MainActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static Context mContext = null;
    private static Handler mMainHandler = null;
    private ArrayList<MenuItem> mMenuItems = new ArrayList<MenuItem>();
    private IAsyncCallbackTuple<String, Boolean> m401Handler;

    private boolean mCacheServiceBound;
    private boolean mCommunicationServiceBound;
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
    private View mSendButton;
    private GridView mEmojiView;
    private boolean mKeyboardShowing;
    private int mEmojiHeight;
    private int mInitialHeightOffset;
    private ImageView mEmojiButton;
    private ImageView mQRButton;
    private Friend mCurrentFriend;
    private int mOrientation;
    private boolean mKeyboardShowingOnChatTab;
    private boolean mKeyboardShowingOnHomeTab;
    private boolean mEmojiShowingOnChatTab;
    private boolean mEmojiShowing;
    private boolean mFriendHasBeenSet;
    private int mEmojiResourceId = -1;
    private ImageView mIvInvite;
    private ImageView mIvVoice;
    private ImageView mIvSend;
    private ImageView mIvHome;
    private AlertDialog mHelpDialog;
    private AlertDialog mDialog;
    private String mUser;
    private boolean mEnterToSend;

    // control booleans
    private boolean mLaunched;
    private boolean mResumed;
    private boolean mStartWhenBothServicesBound;
    private boolean mSigningUp;
    private boolean mUnlocking = false;
    private boolean mPaused = false;
    // end control booleans

    private BillingController mBillingController;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        SurespotLog.d(TAG, "onNewIntent.");
        Utils.logIntent(TAG, intent);

        setIntent(intent);

        // handle case where we deleted the identity we were logged in as
        boolean deleted = intent.getBooleanExtra("deleted", false);

        if (deleted) {
            // if we have any users or we don't need to create a user, figure out if we need to login
            if (!IdentityController.hasIdentity() || intent.getBooleanExtra("create", false)) {
                // otherwise show the signup activity
                SurespotLog.d(TAG, "I was deleted and there are no other users so starting signup activity.");
                Intent newIntent = new Intent(this, SignupActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("signingUp", true);
                startActivity(newIntent);
                finish();
            }
            else {
                SurespotLog.d(TAG, "I was deleted and there are different users so starting login activity.");
                Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(newIntent);
                finish();
            }
        }
        else {
            if (!needsSignup()) {
                processLaunch();
            }
            else {
                mSigningUp = true;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UIUtils.setTheme(this);
        super.onCreate(savedInstanceState);

        SurespotLog.d(TAG, "onCreate");

        boolean keystoreEnabled = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED);
        if (keystoreEnabled) {
            IdentityController.initKeystore();
            if (!IdentityController.unlock(this)) {
                // we have to launch the unlock activity
                // so set a flag we can check in onresume and delay network until that point

                SurespotLog.d(TAG, "launching unlock activity");
                mUnlocking = true;
            }
        }

        Intent intent = getIntent();
        Utils.logIntent(TAG, intent);

        mImm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        mOrientation = getResources().getConfiguration().orientation;


        getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);


        mContext = this;

        SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
        mEnterToSend = sp.getBoolean("pref_enter_to_send", true);

        m401Handler = new IAsyncCallbackTuple<String, Boolean>() {

            @Override
            public void handleResponse(final String message, final Boolean timedOut) {
                SurespotLog.d(TAG, "Got 401, checking authorization.");
                if (!SurespotApplication.getNetworkController().isUnauthorized()) {

                    // if we just timed out, don't blow away the cookie or go to login screen
                    SurespotApplication.getNetworkController().setUnauthorized(true, !timedOut);

                    if (!timedOut) {
                        SurespotLog.d(TAG, "Got 401, launching login intent.");
                        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

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

        if (!needsSignup()) {
            if (savedInstanceState != null) {

                mKeyboardShowing = savedInstanceState.getBoolean("keyboardShowing", false);
                mEmojiShowing = savedInstanceState.getBoolean("emojiShowing", false);
                mEmojiShowingOnChatTab = savedInstanceState.getBoolean("emojiShowingChat", mEmojiShowing);
                mKeyboardShowingOnChatTab = savedInstanceState.getBoolean("keyboardShowingChat", mKeyboardShowing);
                mKeyboardShowingOnHomeTab = savedInstanceState.getBoolean("keyboardShowingHome", mKeyboardShowing);

                SurespotLog.v(TAG,
                        "loading from saved instance state, keyboardShowing: %b, emojiShowing: %b, keyboardShowingChat: %b, keyboardShowingHome: %b, emojiShowingChat: %b",
                        mKeyboardShowing, mEmojiShowing, mKeyboardShowingOnChatTab, mKeyboardShowingOnHomeTab, mEmojiShowingOnChatTab);
            }

            processLaunch();
        }
        else {
            if (!mSigningUp) {
                mSigningUp = intent.getBooleanExtra("signingUp", false);

                if (!mSigningUp) {
                    if (mCommunicationServiceBound && mCacheServiceBound) {
                        processLaunch();
                    }
                    else {
                        // one or more services needs to be bound
                        mStartWhenBothServicesBound = true;

                        if (!mCommunicationServiceBound) {
                            bindChatTransmissionService();
                        }

                        if (!mCacheServiceBound) {
                            bindCacheService();
                        }
                    }
                }
            }
        }
    }

    private void processLaunch() {
        String user = getLaunchUser();

        if (user == null) {
            launchLogin();
        }
        else {
            mUser = user;

            if (!mCommunicationServiceBound) {
                bindChatTransmissionService();
            }

            if (!mCacheServiceBound) {
                bindCacheService();
            }

            if (mCacheServiceBound && mCommunicationServiceBound) {
                SurespotLog.d(TAG, "cache and chat transmission services already bound");
                if (!mUnlocking) {
                    SurespotLog.d(TAG, "processLaunch calling postServiceProcess");
                    postServiceProcess();
                }
                else {
                    SurespotLog.d(TAG, "unlock activity launched, not post service processing until resume");
                }
            }
            else {
                mStartWhenBothServicesBound = true;
            }
        }
    }

    private void launchLogin() {
        SurespotLog.d(TAG, "launchLogin");
        Intent intent = getIntent();
        Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);
        newIntent.putExtra("autoinviteuri", intent.getData());
        newIntent.setAction(intent.getAction());
        newIntent.setType(intent.getType());

        Bundle extras = intent.getExtras();
        if (extras != null) {
            newIntent.putExtras(extras);
        }

        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(newIntent);
        finish();
    }

    private void setupBilling() {
        mBillingController = SurespotApplication.getBillingController();
        mBillingController.setup(getApplicationContext(), true, null);
    }

    private AutoInviteData getAutoInviteData(Intent intent) {
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
                    }
                    else {
                        intent.removeExtra("autoinviteurl");
                    }

                    try {
                        AutoInviteData aid = new AutoInviteData();
                        aid.setUsername(segments.get(1));
                        aid.setSource(segments.get(2));
                        return aid;
                    }
                    catch (IndexOutOfBoundsException e) {
                        SurespotLog.i(TAG, e, "getAutoInviteData");
                    }
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

            setButtonText();

        }
    }

    private void setupChatControls() {
        mIvInvite = (ImageView) findViewById(R.id.ivInvite);
        mIvVoice = (ImageView) findViewById(R.id.ivVoice);
        mIvSend = (ImageView) findViewById(R.id.ivSend);
        mIvHome = (ImageView) findViewById(R.id.ivHome);
        mSendButton = (View) findViewById(R.id.bSend);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SurespotApplication.getChatController() != null) {

                    Friend friend = mCurrentFriend;
                    if (friend != null) {

                        if (mEtMessage.getText().toString().length() > 0 && !SurespotApplication.getChatController().isFriendDeleted(friend.getName())) {
                            sendMessage(friend.getName());
                        }
                        else {
                            // go to home
                            SurespotApplication.getChatController().setCurrentChat(null);
                        }
                    }
                    else {
                        inviteFriend();
                    }
                }
            }
        });

        mSendButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //
                SurespotLog.d(TAG, "onLongClick voice");
                Friend friend = mCurrentFriend;
                if (friend != null) {
                    // if they're deleted always close the tab
                    if (SurespotApplication.getChatController().isFriendDeleted(friend.getName())) {
                        SurespotApplication.getChatController().closeTab();
                    }
                    else {
                        if (mEtMessage.getText().toString().length() > 0) {
                            sendMessage(friend.getName());
                        }
                        else {
                            SharedPreferences sp = MainActivity.this.getSharedPreferences(mUser, Context.MODE_PRIVATE);
                            boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);
                            if (!disableVoice) {
                                VoiceController.startRecording(MainActivity.this, mUser, friend.getName());
                            }
                            else {
                                SurespotApplication.getChatController().closeTab();
                            }
                        }
                    }
                }

                return true;
            }
        });

        mSendButton.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (VoiceController.isRecording()) {

                        Friend friend = mCurrentFriend;
                        if (friend != null) {
                            // if they're deleted do nothing
                            if (SurespotApplication.getChatController().isFriendDeleted(friend.getName())) {
                                return false;
                            }

                            if (mEtMessage.getText().toString().length() == 0) {

                                int width = mSendButton.getWidth();

                                // if user let go of send button out of send button + width (height) bounds, don't send the recording
                                Rect rect = new Rect(mSendButton.getLeft() - width, mSendButton.getTop() - width, mSendButton.getRight(), mSendButton
                                        .getBottom() + width);

                                boolean send = true;
                                if (!rect.contains(v.getLeft() + (int) event.getX(), v.getTop() + (int) event.getY())) {

                                    send = false;

                                    Utils.makeToast(MainActivity.this, getString(R.string.recording_cancelled));

                                }

                                final boolean finalSend = send;

                                SurespotLog.d(TAG, "voice record up");

                                // truncates without the delay for some reason
                                mSendButton.postDelayed(new Runnable() {

                                    @Override
                                    public void run() {
                                        VoiceController.stopRecording(MainActivity.this, finalSend);

                                    }
                                }, 250);
                            }
                        }
                    }
                }

                return false;
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

                toggleEmoji();
            }
        });

        setEmojiIcon(true);

        mQRButton = (ImageView) findViewById(R.id.bQR);
        mQRButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDialog = UIUtils.showQRDialog(MainActivity.this);
            }
        });

        mEtMessage = (EditText) findViewById(R.id.etMessage);
        mEtMessage.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;

                Friend friend = mCurrentFriend;
                if (friend != null) {
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        sendMessage(friend.getName());
                        handled = true;
                    }

                    if (mEnterToSend == true && actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                        sendMessage(friend.getName());
                        handled = true;
                    }
                }
                return handled;
            }
        });

        TextWatcher tw = new ChatTextWatcher();
        mEtMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConstants.MAX_MESSAGE_LENGTH)});
        mEtMessage.addTextChangedListener(tw);

        OnTouchListener editTouchListener = new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mKeyboardShowing) {
                    showSoftKeyboard(v);
                    return true;
                }

                return false;
            }
        };

        mEtMessage.setOnTouchListener(editTouchListener);

        mEtInvite = (EditText) findViewById(R.id.etInvite);
        mEtInvite.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter()});
        mEtInvite.setOnTouchListener(editTouchListener);

        mEtInvite.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;

                if (mCurrentFriend == null) {
                    if (actionId == EditorInfo.IME_ACTION_DONE || (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        inviteFriend();
                        handled = true;
                    }
                }
                return handled;
            }
        });


    }

    private boolean needsSignup() {
        Intent intent = getIntent();
        // figure out if we need to create a user
        if (!IdentityController.hasIdentity() || intent.getBooleanExtra("create", false)) {

            // otherwise show the signup activity

            SurespotLog.d(TAG, "starting signup activity");
            Intent newIntent = new Intent(this, SignupActivity.class);
            newIntent.putExtra("autoinviteuri", intent.getData());
            newIntent.putExtra("signingUp", true);
            newIntent.setAction(intent.getAction());
            newIntent.setType(intent.getType());
            mSigningUp = true;

            Bundle extras = intent.getExtras();
            if (extras != null) {
                newIntent.putExtras(extras);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(newIntent);

            finish();
            return true;
        }

        return false;
    }

    private String getLaunchUser() {
        Intent intent = getIntent();
        // String user = mUser;
        String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
        String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);

        // SurespotLog.d(TAG, "user: %s", user);
        SurespotLog.d(TAG, "type: %s", notificationType);
        SurespotLog.d(TAG, "messageTo: %s", messageTo);

        String user = null;
        // if started with user from intent

        if ("true".equals(intent.getStringExtra(SurespotConstants.ExtraNames.UNSENT_MESSAGES)) &&
                intent.getStringExtra(SurespotConstants.ExtraNames.NAME) != null) {

            user = intent.getStringExtra(SurespotConstants.ExtraNames.NAME);

        }
        else if (!TextUtils.isEmpty(messageTo)
                && (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)
                || SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE
                .equals(notificationType))) {

            user = messageTo;
            Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.LAST_USER, user);
        }
        else {
            user = IdentityController.getLastLoggedInUser(this);
        }

        SurespotLog.d(TAG, "got launch user: %s", user);
        return user;
    }

    private ServiceConnection mChatConnection = new ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            if (service instanceof CommunicationService.CommunicationServiceBinder) {
                CommunicationService.CommunicationServiceBinder binder = (CommunicationService.CommunicationServiceBinder) service;
                CommunicationService cts = binder.getService();

                SurespotApplication.setCommunicationService(cts);
                mCommunicationServiceBound = true;

                if (!mUnlocking && mCacheServiceBound && (mResumed || mStartWhenBothServicesBound)) {
                    SurespotLog.d(TAG, "transmission service calling postServiceProcess");
                    postServiceProcess();
                }
                else {
                    SurespotLog.d(TAG, "unlock activity launched, not post service processing until resume");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            SurespotApplication.getCommunicationService().clearServiceListener();
            SurespotApplication.setCommunicationService(null);
            mCommunicationServiceBound = false;
        }
    };


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            SurespotLog.d(TAG, "caching service bound");
            CredentialCachingBinder binder = (CredentialCachingBinder) service;
            CredentialCachingService ccs = binder.getService();

            SurespotApplication.setCachingService(ccs);
            mCacheServiceBound = true;

            if (!mUnlocking && mCommunicationServiceBound && (mResumed || mStartWhenBothServicesBound)) {
                SurespotLog.d(TAG, "caching service calling postServiceProcess");
                postServiceProcess();
            }
            else {
                SurespotLog.d(TAG, "unlock activity launched, not post service processing until resume");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void postServiceProcess() {
        if (!SurespotApplication.getCachingService().setSession(this, mUser)) {
            launchLogin();
            return;
        }

        //gcm crap
//	//	mRegistrationBroadcastReceiver = new BroadcastReceiver() {
//			@Override
//			public void onReceive(Context context, Intent intent) {
//			//	mRegistrationProgressBar.setVisibility(ProgressBar.GONE);
//				SharedPreferences sharedPreferences =
//						PreferenceManager.getDefaultSharedPreferences(context);
//				boolean sentToken = sharedPreferences
//						.getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);
////				if (sentToken) {
////					Utils.makeLongToast(getString(R.string.gcm_send_message));
////				} else {
////					Utils.makeLongToast(R.string.token_error_message));
////				}
//			}
//		};

        if (checkPlayServices()) {
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, RegistrationIntentService.class);
            startService(intent);
        }

        setupBilling();

        // set volume control buttons
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // we're loading so build the ui
        setContentView(R.layout.activity_main);

        mHomeImageView = (ImageView) findViewById(android.R.id.home);

        setHomeProgress(true);

        // create the chat controller here if we know we're not going to need to login
        // so that if we come back from a restart (for example a rotation), the automatically
        // created fragments have a chat controller instance
        mMainHandler = new Handler(getMainLooper());

        //set username
        SurespotApplication.getNetworkController().setUsernameAnd401Handler(mUser, m401Handler);

        mBillingController = SurespotApplication.getBillingController();

        SurespotApplication.setChatController(new ChatController(this, mUser, getFragmentManager(),
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
        }));

        SurespotApplication.getCommunicationService().initializeService(new CommunicationServiceListener());

        mActivityLayout = (MainActivityLayout) findViewById(R.id.chatLayout);
        mActivityLayout.setOnSoftKeyboardListener(MainActivity.this);
        mActivityLayout.setMainActivity(MainActivity.this);

        final TitlePageIndicator titlePageIndicator = (TitlePageIndicator) findViewById(R.id.indicator);

        mKeyboardStateHandler = new KeyboardStateHandler();
        mActivityLayout.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardStateHandler);

        SurespotApplication.getChatController().init((ViewPager) findViewById(R.id.pager), titlePageIndicator, mMenuItems);

        setupChatControls();
        launch();
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            }
            else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    private void launch() {
        SurespotLog.d(TAG, "launch");
        Intent intent = getIntent();
        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().setAutoInviteData(getAutoInviteData(intent));
        }

        String action = intent.getAction();
        String type = intent.getType();
        String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
        String messageFrom = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
        String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

        boolean userWasCreated = intent.getBooleanExtra("userWasCreated", false);
        intent.removeExtra("userWasCreated");

        boolean mSet = false;
        String name = null;

        // if we're coming from an invite notification, or we need to send to someone
        // then display friends
        if (SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE.equals(notificationType)) {
            SurespotLog.d(TAG, "started from invite");
            mSet = true;
            Utils.clearIntent(intent);
            Utils.configureActionBar(this, "", mUser, true);
        }

        // message received show chat activity for user
        if (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)) {

            SurespotLog.d(TAG, "started from message, to: " + messageTo + ", from: " + messageFrom);
            name = messageFrom;
            Utils.configureActionBar(this, "", mUser, true);
            mSet = true;
            Utils.clearIntent(intent);
            Utils.logIntent(TAG, intent);

            if (SurespotApplication.getChatController() != null) {
                SurespotApplication.getChatController().setCurrentChat(name);

            }
        }

        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
            // need to select a user so put the chat controller in select mode

            if (SurespotApplication.getChatController() != null) {
                // see if we can set the mode
                if (SurespotApplication.getChatController().setMode(ChatController.MODE_SELECT)) {
                    Utils.configureActionBar(this, getString(R.string.send), getString(R.string.main_action_bar_right), true);
                    SurespotLog.d(TAG, "started from SEND");

                    SurespotApplication.getChatController().setCurrentChat(null);
                    mSet = true;
                }
                else {
                    Utils.clearIntent(intent);
                }
            }
        }

        if (!mSet) {
            Utils.configureActionBar(this, "", mUser, true);
            String lastName = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
            if (lastName != null) {
                SurespotLog.d(TAG, "using LAST_CHAT");
                name = lastName;
            }
            if (SurespotApplication.getChatController() != null) {
                SurespotApplication.getChatController().setCurrentChat(name);
            }
        }

        setButtonText();

        // if this is the first time the app has been run, or they just created a user, show the help screen
        boolean helpShown = Utils.getSharedPrefsBoolean(this, "helpShownAgain");
        String justRestoredIdentity = Utils.getUserSharedPrefsString(this, mUser, SurespotConstants.ExtraNames.JUST_RESTORED_IDENTITY);

        if ((!helpShown || userWasCreated) && justRestoredIdentity == null) {
            Utils.removePref(this, "helpShown");
            mHelpDialog = UIUtils.showHelpDialog(this, (justRestoredIdentity == null || justRestoredIdentity.equals("")));
        }

        // only lollipop fixes for 59 so don't bother showing anything
        Utils.removePref(this, "whatsNewShown57");
        Utils.removePref(this, "whatsNewShown46");
        Utils.removePref(this, "whatsNewShown47");

        resume();
        mLaunched = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SurespotLog.d(TAG, "onResume, mUnlocking: %b, mLaunched: %b, mResumed: %b, mPaused: %b", mUnlocking, mLaunched, mResumed, mPaused);

        SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
        mEnterToSend = sp.getBoolean("pref_enter_to_send", true);

        CommunicationService cts = SurespotApplication.getCommunicationServiceNoThrow();

        if (cts == null) {
            SurespotLog.d(TAG, "binding chat transmission service");
            Intent chatIntent = new Intent(this, CommunicationService.class);
            startService(chatIntent);
            bindService(chatIntent, mChatConnection, Context.BIND_AUTO_CREATE);
        }

        // if we had to unlock and we're resuming for a 2nd time and we have the caching service
        if (mUnlocking && mPaused == true) {
            SurespotLog.d(TAG, "setting mUnlocking to false");
            mUnlocking = false;

            if (SurespotApplication.getCachingService() != null && SurespotApplication.getCommunicationServiceNoThrow() != null) {
                SurespotLog.d(TAG, "unlock activity was launched, resume calling postServiceProcess");
                postServiceProcess();
            }
        }

        if (mLaunched && !mResumed) {
            resume();
        }
    }

    private void resume() {
        SurespotLog.d(TAG, "resume");
        mResumed = true;
        if (SurespotApplication.getChatController() != null) {
            if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
                SurespotApplication.getChatController().onResume(SurespotApplication.getCommunicationService().isConnected());
            }
            else {
                SurespotApplication.getChatController().onResume(false);
            }
        }

        startWatchingExternalStorage();
        setBackgroundImage();
        setEditTextHints();

        if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
            SurespotApplication.getCommunicationService().setMainActivityPaused(false);
        }
        else {
            SurespotLog.d(TAG, "resume, Communication service was null");
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        SurespotLog.d(TAG, "onPause");

        mPaused = true;
        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().onPause();
        }

        VoiceController.pause();
        stopWatchingExternalStorage();
        BillingController bc = SurespotApplication.getBillingController();
        if (bc != null) {
            bc.dispose();
        }

        if (mHelpDialog != null && mHelpDialog.isShowing()) {
            mHelpDialog.dismiss();
        }

        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }

        if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
            SurespotApplication.getCommunicationService().setMainActivityPaused(true);
        }

        mResumed = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SurespotLog.d(TAG, "onActivityResult, requestCode: " + requestCode);

        switch (requestCode) {
            case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    final Uri selectedImageUri = data.getData();
                    String to = data.getStringExtra("to");
                    SurespotLog.d(TAG, "to: " + to);

                    String urisExtra = data.getStringExtra("uris");
                    String[] uris = urisExtra.split("~~~~");

                    if (uris == null || uris.length == 0) {
                        if (selectedImageUri != null) {
                            uploadPicture(selectedImageUri, to);
                        }
                    }
                    else {
                        for (String uri : uris) {
                            uploadPicture(Uri.parse(uri), to);
                        }
                    }
                }
                break;
            case SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE:
                if (resultCode == RESULT_OK) {
                    if (mImageCaptureHandler != null) {
                        mImageCaptureHandler.handleResult(this);
                        mImageCaptureHandler = null;
                    }
                }
                break;

            case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FRIEND_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    final Uri selectedImageUri = data.getData();

                    final String to = data.getStringExtra("to");

                    SurespotLog.d(TAG, "to: " + to);
                    if (selectedImageUri != null) {

                        // Utils.makeToast(this, getString(R.string.uploading_image));
                        ChatUtils.uploadFriendImageAsync(this, SurespotApplication.getNetworkController(), selectedImageUri, to, new IAsyncCallbackTriplet<String, String, String>() {

                            @Override
                            public void handleResponse(String url, String version, String iv) {
                                try {
                                    File file = new File(new URI(selectedImageUri.toString()));
                                    SurespotLog.d(TAG, "deleted temp image file: %b", file.delete());
                                }
                                catch (URISyntaxException e) {
                                }

                                if (SurespotApplication.getChatController() == null || url == null) {
                                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_friend_image));
                                }
                                else {
                                    SurespotApplication.getChatController().setImageUrl(to, url, version, iv, true);
                                }
                            }
                        });
                    }
                }
                break;

            case SurespotConstants.IntentRequestCodes.PURCHASE:
                // Pass on the activity result to the helper for handling
                if (!SurespotApplication.getBillingController().getIabHelper().handleActivityResult(requestCode, resultCode, data)) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                else {
                    // TODO upload token to server
                    SurespotLog.d(TAG, "onActivityResult handled by IABUtil.");
                }
                break;

            case SurespotConstants.IntentRequestCodes.QR_CODE_NOTIFICATION:
                if (resultCode == Activity.RESULT_OK ){
                    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

                    if (scanResult != null && scanResult.getContents() != null){
                        if (scanResult.getContents().contains(SurespotConstants.Url.INVITE_URL)){
                            // We have scanned a QR code that contains a surespot URL
                            // Use the data from the QR Code to initiate a friend request
                            String qrContents = scanResult.getContents();
                            int usernameEnd = qrContents.indexOf("/qr");
                            String qrUsername = qrContents.substring(SurespotConstants.Url.INVITE_URL.length() ,usernameEnd);

                            mEtInvite.setText(qrUsername);
                            inviteFriend();

                            //Reset the text field for cases where user invite is not completed
                            mEtInvite.setText("");
                        }else{
                            //An invalid QR code has been scanned and should be ignored
                            Utils.makeToast(MainActivity.this, getString(R.string.qr_scan_invalid));
                        }
                    }
                }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void uploadPicture(final Uri selectedImageUri, String to) {
        ChatUtils.uploadPictureMessageAsync(this, SurespotApplication.getChatController(), selectedImageUri, mUser, to, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        SurespotLog.d(TAG, "onCreateOptionsMenu");

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);

        mMenuOverflow = menu;

        mMenuItems.add(menu.findItem(R.id.menu_close_bar));
        mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));

        MenuItem captureItem = menu.findItem(R.id.menu_capture_image_bar);
        if (hasCamera()) {
            mMenuItems.add(captureItem);
            captureItem.setEnabled(FileUtils.isExternalStorageMounted());
        }
        else {
            SurespotLog.d(TAG, "hiding capture image menu option");
            menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
        }

        mMenuItems.add(menu.findItem(R.id.menu_clear_messages));
        // nag nag nag

        //mMenuItems.add(menu.findItem(R.id.menu_purchase_voice));

        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().enableMenuItems(mCurrentFriend);
        }

        //
        enableImageMenuItems();
        return true;
    }

    private boolean hasCamera() {
        //   if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
        return Camera.getNumberOfCameras() > 0;
//        }
//        else {
//            PackageManager pm = this.getPackageManager();
//            return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
//        }
    }

    public void uploadFriendImage(String name) {
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
        final String currentChat = SurespotApplication.getChatController().getCurrentChat();
        switch (item.getItemId()) {
            case android.R.id.home:
                // This is called when the Home (Up) button is pressed
                // in the Action Bar.
                // showUi(!mChatsShowing);
                SurespotApplication.getChatController().setCurrentChat(null);
                return true;
            case R.id.menu_close_bar:

                SurespotApplication.getChatController().closeTab();
                return true;
            case R.id.menu_send_image_bar:
                if (currentChat == null) {
                    return true;
                }

                // can't send images to deleted folk
                if (mCurrentFriend != null && mCurrentFriend.isDeleted()) {
                    return true;
                }

                new AsyncTask<Void, Void, Void>() {
                    protected Void doInBackground(Void... params) {
                        Intent intent = new Intent(MainActivity.this, ImageSelectActivity.class);
                        intent.putExtra("to", currentChat);
                        intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_LARGE);
                        // set start intent to avoid restarting every rotation
                        intent.putExtra("start", true);
                        startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
                        return null;
                    }

                    ;

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

                new AsyncTask<Void, Void, Void>() {
                    protected Void doInBackground(Void... params) {

                        mImageCaptureHandler = new ImageCaptureHandler(mUser, currentChat);
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
                if (SurespotApplication.getChatController() != null) {
                    SurespotApplication.getChatController().logout();
                }
                IdentityController.logout();

                // new AsyncTask<Void, Void, Void>() {
                // protected Void doInBackground(Void... params) {

                Intent finalIntent = new Intent(MainActivity.this, LoginActivity.class);
                finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                // mChatController = null;
                MainActivity.this.startActivity(finalIntent);
                finish();
                // return null;
                // }
                // }.execute();
                return true;
            case R.id.menu_invite_external:
                UIUtils.sendInvitation(MainActivity.this, SurespotApplication.getNetworkController());
                return true;
            case R.id.menu_clear_messages:
                SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
                boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
                if (confirm) {
                    mDialog = UIUtils.createAndShowConfirmationDialog(this, getString(R.string.delete_all_confirmation), getString(R.string.delete_all_title),
                            getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        SurespotApplication.getChatController().deleteMessages(currentChat);
                                    }
                                }

                                ;
                            });
                }
                else {
                    SurespotApplication.getChatController().deleteMessages(currentChat);
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
//            case R.id.menu_purchase_voice:
//                showVoicePurchaseDialog(false);
//                return true;
            default:
                return false;

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SurespotLog.d(TAG, "onDestroy");
        if (mCacheServiceBound && mConnection != null) {
            unbindService(mConnection);
        }

        if (mChatConnection != null) {
            if (mCommunicationServiceBound) {
                unbindService(mChatConnection);
            }

            // clear the service listener.  This lets the transmission service know it can shut down when it's done sending
            if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
                SurespotApplication.getCommunicationServiceNoThrow().clearServiceListener();
            }
        }
    }

    public static Context getContext() {
        return mContext;
    }

    public static ChatController getChatController() {
        return SurespotApplication.getChatController();
    }

    public static Handler getMainHandler() {
        return mMainHandler;
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mMenuOverflow != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMenuOverflow.performIdentifierAction(R.id.item_overflow, 0);
                    }
                });
            }

            return true;
        }

        return super.onKeyUp(keyCode, event);
    }


    public void openOptionsMenuDeferred() {
        mHandler.post(new Runnable() {
                          @Override
                          public void run() {
                              openOptionsMenu();
                          }
                      }
        );
    }


    private void startWatchingExternalStorage() {
        mExternalStorageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SurespotLog.d(TAG, "Storage: " + intent.getData());
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
        // don't puke if we can't unregister
        try {
            unregisterReceiver(mExternalStorageReceiver);
        }
        catch (java.lang.IllegalArgumentException e) {
        }
    }

    private void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        SurespotLog.d(TAG, "updateExternalStorageState:  " + state);
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
        SurespotLog.d(TAG, "onSaveInstanceState");
        if (mImageCaptureHandler != null) {
            SurespotLog.d(TAG, "onSaveInstanceState saving imageCaptureHandler, to: %s, path: %s", mImageCaptureHandler.getTo(),
                    mImageCaptureHandler.getImagePath());
            outState.putParcelable("imageCaptureHandler", mImageCaptureHandler);
        }

        SurespotLog.d(TAG, "onSaveInstanceState saving mKeyboardShowing: %b", mKeyboardShowing);
        outState.putBoolean("keyboardShowing", mKeyboardShowing);

        SurespotLog.d(TAG, "onSaveInstanceState saving emoji showing: %b", mEmojiShowing);
        outState.putBoolean("emojiShowing", mEmojiShowing);

        SurespotLog.d(TAG, "onSaveInstanceState saving emoji showing on chat tab: %b", mEmojiShowing);
        outState.putBoolean("emojiShowingChat", mEmojiShowing);

        SurespotLog.d(TAG, "onSaveInstanceState saving keyboard showing in chat tab: %b", mKeyboardShowingOnChatTab);
        outState.putBoolean("keyboardShowingChat", mKeyboardShowingOnChatTab);

        SurespotLog.d(TAG, "onSaveInstanceState saving keybard showing in home tab: %b", mKeyboardShowingOnHomeTab);
        outState.putBoolean("keyboardShowingHome", mKeyboardShowingOnHomeTab);

        if (mInitialHeightOffset > 0) {
            SurespotLog.d(TAG, "onSaveInstanceState saving heightOffset: %d", mInitialHeightOffset);
            outState.putInt("heightOffset", mInitialHeightOffset);
        }

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        SurespotLog.d(TAG, "onRestoreInstanceState");
        mImageCaptureHandler = savedInstanceState.getParcelable("imageCaptureHandler");
        if (mImageCaptureHandler != null) {
            SurespotLog.d(TAG, "onRestoreInstanceState restored imageCaptureHandler, to: %s, path: %s", mImageCaptureHandler.getTo(),
                    mImageCaptureHandler.getImagePath());
        }

        mKeyboardShowing = savedInstanceState.getBoolean("keyboardShowing");

        mInitialHeightOffset = savedInstanceState.getInt("heightOffset");
    }

    private void setHomeProgress(boolean inProgress) {
        if (mHomeImageView == null) {
            return;
        }

        SurespotLog.d(TAG, "progress status changed to: %b", inProgress);
        if (inProgress) {
            UIUtils.showProgressAnimation(this, mHomeImageView);
        }
        else {
            mHomeImageView.clearAnimation();
        }

        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().enableMenuItems(mCurrentFriend);
        }
    }

    public synchronized void hideSoftKeyboard() {

        SurespotLog.d(TAG, "hideSoftkeyboard");
        View view = null;
        if (mCurrentFriend == null) {
            view = mEtInvite;
        }
        else {
            view = mEtMessage;
        }

        hideSoftKeyboard(view);
    }

    private synchronized void hideSoftKeyboard(final View view) {
        mKeyboardShowing = false;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                mImm.hideSoftInputFromWindow(view.getWindowToken(), 0, new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != InputMethodManager.RESULT_HIDDEN && resultCode != InputMethodManager.RESULT_UNCHANGED_HIDDEN) {
                            mKeyboardShowing = true;

                        }
                    }
                });
            }
        };
        view.post(runnable);
    }

    private synchronized void showSoftKeyboard() {
        SurespotLog.d(TAG, "showSoftkeyboard");
        mKeyboardShowing = true;
        mEmojiShowing = false;

        View view = null;
        if (mCurrentFriend == null) {
            view = mEtInvite;
        }
        else {
            view = mEtMessage;
        }

        showSoftKeyboard(view);
    }

    private synchronized void showSoftKeyboard(final View view) {
        mKeyboardShowing = true;
        mEmojiShowing = false;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                mImm = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                mImm.restartInput(view);
                mImm.showSoftInput(view, 0, new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if ((resultCode != InputMethodManager.RESULT_SHOWN) && (resultCode != InputMethodManager.RESULT_UNCHANGED_SHOWN)) {
                            mKeyboardShowing = false;
                        }

                    }
                });

                // setEmojiIcon(true);
            }
        };
        view.post(runnable);
    }

    private synchronized void showSoftKeyboardThenHideEmoji(final View view) {
        mKeyboardShowing = true;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                mImm = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                mImm.showSoftInput(view, 0, new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if ((resultCode != InputMethodManager.RESULT_SHOWN) && (resultCode != InputMethodManager.RESULT_UNCHANGED_SHOWN)) {
                            mKeyboardShowing = false;
                        }
                        else {
                            Runnable runnable = new Runnable() {

                                @Override
                                public void run() {
                                    showEmoji(false, false);

                                }
                            };

                            view.post(runnable);

                        }

                    }
                });
            }
        };
        view.post(runnable);
    }

    private synchronized void hideSoftKeyboardThenShowEmoji(final View view) {
        mKeyboardShowing = false;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                showEmoji(true, true);
                mImm.hideSoftInputFromWindow(view.getWindowToken(), 0, new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != InputMethodManager.RESULT_HIDDEN && resultCode != InputMethodManager.RESULT_UNCHANGED_HIDDEN) {
                            mKeyboardShowing = true;
                        }
                        else {

                        }
                    }
                });
            }
        };

        view.post(runnable);
    }

    private synchronized void showEmoji(boolean showEmoji, boolean force) {
        int visibility = mEmojiView.getVisibility();
        if (showEmoji) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            if (visibility != View.VISIBLE && force) {
                SurespotLog.d(TAG, "showEmoji,  showing emoji view");
                mEmojiView.setVisibility(View.VISIBLE);
            }
        }
        else {
            if (visibility != View.GONE && force) {
                SurespotLog.d(TAG, "showEmoji,  hiding emoji view");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                mEmojiView.setVisibility(View.GONE);
            }
        }

        if (force) {
            setEmojiIcon(!showEmoji);
        }

        mEmojiShowing = showEmoji;
    }

    private void toggleEmoji() {
        if (mEmojiShowing) {
            showSoftKeyboard(mEtMessage);
        }
        else {
            if (mKeyboardShowing) {
                hideSoftKeyboard();
                showEmoji(true, false);
            }
            else {
                showEmoji(true, true);
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
            if (MainActivity.this.mEnterToSend && s.toString().contains("\n")) {
                int idx = s.toString().indexOf('\n');
                s.delete(idx, idx + 1);
                if (mEtMessage.getText().toString().length() > 0 && !SurespotApplication.getChatController().isFriendDeleted(mCurrentFriend.getName())) {
                    sendMessage(mCurrentFriend.getName());
                }
            }

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
            Utils.configureActionBar(this, "", mUser, true);

            if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
                String sharedText = intent.getExtras().get(Intent.EXTRA_TEXT).toString();
                SurespotLog.d(TAG, "received action send, data: %s", sharedText);
                mEtMessage.append(sharedText);
                // requestFocus();
                // clear the intent

            }
            else {
                if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

                    final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

                    // Utils.makeToast(getActivity(), getString(R.string.uploading_image));

                    SurespotLog.d(TAG, "received image data, upload image, uri: %s", imageUri);

                    ChatUtils.uploadPictureMessageAsync(
                            this,
                            SurespotApplication.getChatController(),
                            imageUri,
                            mUser,
                            mCurrentFriend.getName(),
                            true);
                }
            }
        }
        else {
            if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                Utils.configureActionBar(this, "", mUser, true);
                if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

                    ArrayList<Parcelable> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
                    for (Parcelable p : uris) {
                        final Uri imageUri = (Uri) p;

                        SurespotLog.d(TAG, "received image data, upload image, uri: %s", imageUri);

                        ChatUtils.uploadPictureMessageAsync(
                                this,
                                SurespotApplication.getChatController(),
                                imageUri,
                                mUser,
                                mCurrentFriend.getName(),
                                true);
                    }
                }
            }
        }


        Utils.clearIntent(getIntent());
    }


    private void sendMessage(String username) {
        final String message = mEtMessage.getText().toString();
        if (!message.isEmpty()) {
            SurespotApplication.getChatController().sendMessage(username, message, SurespotConstants.MimeTypes.TEXT);
            TextKeyListener.clear(mEtMessage.getText());
        }
    }

    public boolean backButtonPressed() {
        boolean handled = false;
        SurespotLog.d(TAG, "backButtonPressed");

        if (mEmojiShowing) {
            showEmoji(false, true);
            handled = true;
        }

        if (mKeyboardShowing) {

            hideSoftKeyboard();
            handled = true;
        }

        return handled;
    }

    @Override
    public void onLayoutMeasure() {
        SurespotLog.v(TAG, "onLayoutMeasure, emoji height: %d", mEmojiHeight);
        if (mEmojiShowing) {

            if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                mEmojiHeight = 200;
            }

            if (mEmojiHeight > 0) {
                android.view.ViewGroup.LayoutParams layoutParams = mEmojiView.getLayoutParams();
                layoutParams.height = mEmojiHeight;
            }

            mEmojiView.setVisibility(View.VISIBLE);
            setEmojiIcon(false);
        }
        else {
            mEmojiView.setVisibility(View.GONE);
            setEmojiIcon(true);
        }
    }

    private void inviteFriend() {

        final String friend = mEtInvite.getText().toString();

        if (friend.length() > 0) {
            if (friend.equals(mUser)) {
                // TODO let them be friends with themselves?
                Utils.makeToast(this, getString(R.string.friend_self_error));
                return;
            }

            setHomeProgress(true);
            SurespotApplication.getNetworkController().invite(friend, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    SurespotLog.i(TAG, e, "inviteFriend error");
                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
                }

                @Override
                public void onResponse(Call call, Response response, String responseString) throws IOException {
                    setHomeProgress(false);
                    if (response.isSuccessful()) {

                        TextKeyListener.clear(mEtInvite.getText());
                        if (SurespotApplication.getChatController().getFriendAdapter().addFriendInvited(friend)) {
                            Utils.makeToast(MainActivity.this, getString(R.string.has_been_invited, friend));
                        }
                        else {
                            Utils.makeToast(MainActivity.this, getString(R.string.has_accepted, friend));
                        }
                    }
                    else {
                        switch (response.code()) {
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
                                SurespotLog.i(TAG, "inviteFriend error");
                                Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
                        }
                    }
                }
            }));
        }else{
            // Button has been pressed with no text in the invite field
            // Launch QR reader to scan code
            UIUtils.showQRScanner(MainActivity.this);
        }
    }

    public void setButtonText() {
        if (mCurrentFriend == null) {
            mIvInvite.setVisibility(View.VISIBLE);
            mIvVoice.setVisibility(View.GONE);
            mIvHome.setVisibility(View.GONE);
            mIvSend.setVisibility(View.GONE);
        }
        else {
            if (mCurrentFriend.isDeleted()) {
                mIvInvite.setVisibility(View.GONE);
                mIvVoice.setVisibility(View.GONE);
                mIvHome.setVisibility(View.VISIBLE);
                mIvSend.setVisibility(View.GONE);
            }
            else {
                if (mEtMessage.getText().length() > 0) {
                    mIvInvite.setVisibility(View.GONE);
                    mIvVoice.setVisibility(View.GONE);
                    mIvHome.setVisibility(View.GONE);
                    mIvSend.setVisibility(View.VISIBLE);
                }
                else {
                    mIvInvite.setVisibility(View.GONE);
                    SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
                    boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);

                    if (disableVoice) {
                        mIvVoice.setVisibility(View.GONE);
                        mIvHome.setVisibility(View.VISIBLE);
                    }
                    else {
                        mIvVoice.setVisibility(View.VISIBLE);
                        mIvHome.setVisibility(View.GONE);
                    }

                    mIvSend.setVisibility(View.GONE);
                }
            }

        }
    }

    // this isn't brittle...NOT
    private void handleTabChange(Friend friend) {

        boolean showKeyboard = false;
        boolean showEmoji = false;
        SurespotLog
                .v(TAG,
                        "handleTabChange, mFriendHasBeenSet: %b, currentFriend is null: %b, keyboardShowing: %b, emojiShowing: %b, keyboardShowingChat: %b, keyboardShowingHome: %b, emojiShowingChat: %b",
                        mFriendHasBeenSet, mCurrentFriend == null, mKeyboardShowing, mEmojiShowing, mKeyboardShowingOnChatTab, mKeyboardShowingOnHomeTab,
                        mEmojiShowingOnChatTab);

        if (friend == null) {
            mEmojiButton.setVisibility(View.GONE);
            mEtMessage.setVisibility(View.GONE);
            mEtInvite.setVisibility(View.VISIBLE);
            mEmojiView.setVisibility(View.GONE);

            mQRButton.setVisibility(View.VISIBLE);
            mEtInvite.requestFocus();

            getActionBar().setDisplayHomeAsUpEnabled(false);

            SurespotLog.d(TAG, "handleTabChange, setting keyboardShowingOnChatTab: %b", mKeyboardShowing);
            if (mFriendHasBeenSet) {
                if (mCurrentFriend != null && !mCurrentFriend.isDeleted()) {
                    mKeyboardShowingOnChatTab = mKeyboardShowing;
                    mEmojiShowingOnChatTab = mEmojiShowing;
                }
                showKeyboard = mKeyboardShowingOnHomeTab;

            }
            else {
                showKeyboard = mKeyboardShowing;
            }
            showEmoji = false;

        }
        else {
            getActionBar().setDisplayHomeAsUpEnabled(true);

            if (friend.isDeleted()) {

                mEmojiButton.setVisibility(View.GONE);
                mEtMessage.setVisibility(View.GONE);

                if (mFriendHasBeenSet) {
                    // if we're coming from home tab
                    if (mCurrentFriend == null) {
                        mKeyboardShowingOnHomeTab = mKeyboardShowing;
                    }
                    else {
                        if (!mCurrentFriend.isDeleted()) {
                            mKeyboardShowingOnChatTab = mKeyboardShowing;
                            mEmojiShowingOnChatTab = mEmojiShowing;
                        }
                    }
                }

                showKeyboard = false;
                showEmoji = false;

            }
            else {
                mEtMessage.setVisibility(View.VISIBLE);
                mEmojiButton.setVisibility(View.VISIBLE);

                // if we moved back to chat tab from home hab show the keyboard if it was showing
                if ((mCurrentFriend == null || mCurrentFriend.isDeleted()) && mFriendHasBeenSet) {
                    SurespotLog.d(TAG, "handleTabChange, keyboardShowingOnChatTab: %b", mKeyboardShowingOnChatTab);

                    showKeyboard = mKeyboardShowingOnChatTab;
                    showEmoji = mEmojiShowingOnChatTab;

                    if (mCurrentFriend != null && !mCurrentFriend.isDeleted()) {
                        mKeyboardShowingOnHomeTab = mKeyboardShowing;
                    }

                }
                else {
                    showKeyboard = mKeyboardShowing;
                    showEmoji = mEmojiShowing;
                }
            }

            mEtInvite.setVisibility(View.GONE);
            mQRButton.setVisibility(View.GONE);
            mEtMessage.requestFocus();
        }

        // if keyboard is showing and we want to show emoji or vice versa, just toggle emoji
        mCurrentFriend = friend;
        if ((mKeyboardShowing && showEmoji) || (mEmojiShowing && showKeyboard)) {
            if (friend == null) {
                if (mEmojiShowing) {
                    showSoftKeyboardThenHideEmoji(mEtInvite);
                }
                else {
                    hideSoftKeyboard(mEtMessage);
                }
            }
            else {
                if (mEmojiShowing) {
                    showSoftKeyboard(mEtMessage);
                    showEmoji(false, true);
                }
                else {
                    showEmoji(true, true);
                    hideSoftKeyboard(mEtInvite);
                }
            }
        }
        else {
            if (showKeyboard && (mKeyboardShowing != showKeyboard || mEmojiShowing)) {
                showSoftKeyboard();
            }
            else {

                if (mKeyboardShowing != showKeyboard) {
                    showEmoji(showEmoji, true);
                    hideSoftKeyboard();
                }
                else {
                    showEmoji(showEmoji, true);
                }
            }
        }

        if (friend == null || !friend.isDeleted()) {
            mKeyboardShowing = showKeyboard;
        }

        setButtonText();

        mFriendHasBeenSet = true;
    }

    private void setEmojiIcon(final boolean keyboardShowing) {

        if (keyboardShowing) {
            if (mEmojiResourceId < 0) {
                mEmojiResourceId = EmojiParser.getInstance().getRandomEmojiResource();
            }
            mEmojiButton.setImageResource(mEmojiResourceId);
        }
        else {
            mEmojiButton.setImageResource(R.drawable.keyboard_icon);
        }

    }

//    public void showVoicePurchaseDialog(boolean comingFromButton) {
//        FragmentManager fm = getSupportFragmentManager();
//        SherlockDialogFragment dialog = VoicePurchaseFragment.newInstance(comingFromButton);
//        dialog.show(fm, "voice_purchase");
//
//    }

    private void setBackgroundImage() {
        // reset preference config for adapters
        SharedPreferences sp = MainActivity.this.getSharedPreferences(mUser, Context.MODE_PRIVATE);
        ImageView imageView = (ImageView) findViewById(R.id.backgroundImage);
        String backgroundImageUrl = sp.getString("pref_background_image", null);

        if (backgroundImageUrl != null) {
            SurespotLog.d(TAG, "setting background image %s", backgroundImageUrl);

            imageView.setImageURI(Uri.parse(backgroundImageUrl));
            imageView.setAlpha(125);
            SurespotConfiguration.setBackgroundImageSet(true);
        }
        else {
            imageView.setImageDrawable(null);
            SurespotConfiguration.setBackgroundImageSet(false);
        }
    }

    private void setEditTextHints() {
        // stop showing hints after 5 times
        SharedPreferences sp = Utils.getGlobalSharedPrefs(this);
        int messageHintShown = sp.getInt("messageHintShown", 0);
        int inviteHintShown = sp.getInt("inviteHintShown", 0);

        if (messageHintShown++ < 6) {
            mEtMessage.setHint(R.string.message_hint);

        }

        if (inviteHintShown++ < 6) {
            mEtInvite.setHint(R.string.invite_hint);
        }

        Editor editor = sp.edit();
        editor.putInt("messageHintShown", messageHintShown);
        editor.putInt("inviteHintShown", inviteHintShown);
        editor.commit();

    }

    public void setChildDialog(AlertDialog childDialog) {
        mDialog = childDialog;
    }

    public void assignFriendAlias(final String name) {
        // popup dialog and ask for alias
        UIUtils.aliasDialog(this, name, getString(R.string.enter_alias), getString(R.string.enter_alias_for, name), new IAsyncCallback<String>() {

            @Override
            public void handleResponse(String alias) {

                SurespotApplication.getChatController().assignFriendAlias(name, alias, new IAsyncCallback<Boolean>() {

                    @Override
                    public void handleResponse(Boolean result) {
                        if (!result) {
                            Utils.makeToast(MainActivity.this, getString(R.string.could_not_assign_friend_alias));
                        }
                    }
                });
            }
        });
    }

    public void removeFriendImage(final String name) {
        SurespotApplication.getChatController().removeFriendImage(name, new IAsyncCallback<Boolean>() {
            @Override
            public void handleResponse(Boolean result) {
                if (!result) {
                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_image));
                }
            }
        });
    }

    public void removeFriendAlias(final String name) {
        SurespotApplication.getChatController().removeFriendAlias(name, new IAsyncCallback<Boolean>() {
            @Override
            public void handleResponse(Boolean result) {
                if (!result) {
                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_alias));
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }


    private void bindChatTransmissionService() {
        SurespotLog.d(TAG, "binding chat transmission service");
        Intent chatIntent = new Intent(this, CommunicationService.class);
        startService(chatIntent);
        bindService(chatIntent, mChatConnection, Context.BIND_AUTO_CREATE);
    }

    private void bindCacheService() {
        SurespotLog.d(TAG, "binding cache service");
        Intent cacheIntent = new Intent(this, CredentialCachingService.class);
        startService(cacheIntent);
        bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private class CommunicationServiceListener implements ITransmissionServiceListener {
        // implementation goes here - or maybe we have a separate class if this gets too big

        @Override
        public void onConnected() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.setHomeProgress(false);
                    if (!logIfChatControllerNull()) {
                        SurespotApplication.getChatController().connected();
                    }
                }
            });

        }

        @Override
        public void onCouldNotConnectToServer() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.setHomeProgress(false);

                    if (mCurrentFriend != null) {
                        //notify current adapter as unsent messages are now errored
                        ChatController cc = SurespotApplication.getChatController();
                        if (cc != null) {
                            ChatAdapter ca = cc.getChatAdapter(mCurrentFriend.getName(), false);
                            if (ca != null) {
                                ca.notifyDataSetChanged();
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onNotConnected() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.setHomeProgress(true);
                }
            });
        }

        @Override
        public void on401() {
            m401Handler.handleResponse("socket 401", false);
        }

        private boolean logIfChatControllerNull() {
            if (SurespotApplication.getChatController() == null) {
                SurespotLog.w(TAG, "mChatController was null for tranmission service listener");
                return true;
            }
            return false;
        }
    }

}
