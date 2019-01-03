package com.twofours.surespot.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.rockerhieu.emojicon.EmojiconEditText;
import com.rockerhieu.emojicon.EmojiconsView;
import com.rockerhieu.emojicon.OnEmojiconClickedListener;
import com.rockerhieu.emojicon.emoji.Emojicon;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.billing.BillingActivity;
import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SoftKeyboardLayout;
import com.twofours.surespot.chat.SurespotDrawerLayout;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.filetransfer.FileTransferUtils;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.gifs.GifDetails;
import com.twofours.surespot.gifs.GifSearchHandler;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.GalleryModeHandler;
import com.twofours.surespot.images.ImageCaptureHandler;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.RegistrationIntentService;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;
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

import static android.view.View.GONE;
import static com.twofours.surespot.SurespotConstants.ExtraNames.MESSAGE_TO;

public class MainActivity extends FragmentActivity implements EmojiconsView.OnEmojiconBackspaceClickedListener, OnEmojiconClickedListener {
    public static final String TAG = "MainActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String MESSAGE_MODE_KEYBOARD = "keyboard";
    private static final String MESSAGE_MODE_EMOJI = "emoji";
    private static final String MESSAGE_MODE_GIF = "gif";
    private static final String MESSAGE_MODE_CAMERA = "camera";
    private static final String MESSAGE_MODE_GALLERY = "gallery";
    private static final String MESSAGE_MODE_MORE = "more";

    private ArrayList<MenuItem> mMenuItems = new ArrayList<MenuItem>();
    private IAsyncCallback<Object> m401Handler;

    private Menu mMenuOverflow;
    private BroadcastReceiver mExternalStorageReceiver;
    private boolean mExternalStorageAvailable = false;
    private boolean mExternalStorageWriteable = false;
    private ImageView mHomeImageView;

    private SoftKeyboardLayout mActivityLayout;
    private EmojiconEditText mEtMessage;
    private EditText mEtInvite;
    private View mSendButton;
    private EmojiconsView mEmojiView;
    private ImageView mQRButton;
    private ImageView mExpandButton;
    private ImageView mEmojiButton;
    private ImageView mGifButton;
    private ImageView mCameraButton;
    private ImageView mGalleryButton;
    private ImageView mMoreButton;


    private Friend mCurrentFriend;
    private boolean mFriendHasBeenSet;
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
    private boolean mSigningUp;
    private boolean mUnlocking = false;
    private boolean mPaused = false;
    // end control booleans

    private BillingController mBillingController;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ListView mDrawerList;
    private FrameLayout mContentFrame;
    private DrawerLayout mDrawerLayout;
    private LayoutParams mWindowLayoutParams;
    private GifSearchHandler mGifHandler;
    private GalleryModeHandler mGalleryModeHandler;
    private EditText mEtGifSearch;
    private View mGiphySearchFieldLayout;
    private boolean mWaitingForKeyboardToShow = false;
    private View mMessageModeView;
    private String mCurrentMessageMode;
    private View mGalleryView;
    private View mButtons;
    private boolean isCollapsed = true;
    private ImageView mPoweredByGiphyView;
    private View mOldView;

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
            if (!IdentityController.hasIdentity(this) || intent.getBooleanExtra("create", false)) {
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

        SurespotLog.d(TAG, "onCreate %d", this.hashCode());

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

        getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);

        SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
        mEnterToSend = sp.getBoolean("pref_enter_to_send", true);

        m401Handler = new IAsyncCallback<Object>() {

            @Override
            public void handleResponse(Object unused) {
                SurespotLog.d(TAG, "Got 401, launching login intent.");
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        };

        if (!needsSignup()) {
            processLaunch();
        }
        else {
            if (!mSigningUp) {
                mSigningUp = intent.getBooleanExtra("signingUp", false);

                if (!mSigningUp) {
                    processLaunch();
                }
            }
        }
    }

    private void processLaunch() {
        String user = getLaunchUser();
        SurespotLog.d(TAG, "processLaunch, launchUser: %s, mUser: %s", user, mUser);
        if (user == null) {
            launchLogin();
        }
        else {
            mUser = user;


            SurespotLog.d(TAG, "processLaunch calling postServiceProcess");
            postServiceProcess();
        }
    }

    private void launchLogin() {
        SurespotLog.d(TAG, "launchLogin, mUser: %s", mUser);
        Intent intent = getIntent();
        Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);

        Bundle extras = intent.getExtras();
        if (extras != null) {
            newIntent.putExtras(extras);
        }

        newIntent.putExtra("autoinviteuri", intent.getData());
        newIntent.setAction(intent.getAction());
        newIntent.setType(intent.getType());

        if (mUser != null) {
            newIntent.putExtra(MESSAGE_TO, mUser);
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

    private void setupChatControls(View mainView) {
        mIvInvite = (ImageView) mainView.findViewById(R.id.ivInvite);
        mIvVoice = (ImageView) mainView.findViewById(R.id.ivVoice);
        mIvSend = (ImageView) mainView.findViewById(R.id.ivSend);
        mIvHome = (ImageView) mainView.findViewById(R.id.ivHome);
        mSendButton = (View) mainView.findViewById(R.id.bSend);
        mButtons = mainView.findViewById(R.id.fButtons);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null) {

                    Friend friend = mCurrentFriend;
                    if (friend != null) {
                        String message = mEtMessage.getText().toString();
                        if (message.length() > 0 && !cc.isFriendDeleted(friend.getName())) {
                            sendMessage(friend.getName(), message);
                        }
                        else {
                            // go to home
                            cc.setCurrentChat(null);
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
                    ChatController cc = ChatManager.getChatController(mUser);
                    if (cc != null) {

                        // if they're deleted always close the tab
                        if (cc.isFriendDeleted(friend.getName())) {
                            cc.closeTab();
                        }
                        else {
                            String message = mEtMessage.getText().toString();
                            if (message.length() > 0) {
                                sendMessage(friend.getName(), message);
                            }
                            else {
                                SharedPreferences sp = MainActivity.this.getSharedPreferences(mUser, Context.MODE_PRIVATE);
                                boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);
                                if (!disableVoice) {
                                    checkPermissionVoice(MainActivity.this);
                                }
                                else {
                                    cc.closeTab();
                                }
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
                            ChatController cc = ChatManager.getChatController(mUser);
                            if (cc != null) {
                                if (cc.isFriendDeleted(friend.getName())) {
                                    return false;
                                }
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
                                        VoiceController.stopRecording(MainActivity.this, finalSend, false);

                                    }
                                }, 250);
                            }
                        }
                    }
                }

                return false;
            }
        });

        View.OnClickListener messageModeClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMessageMode(v.getTag().toString());
            }
        };

        mEmojiButton = (ImageView) mainView.findViewById(R.id.bEmoji);
        mEmojiButton.setOnClickListener(messageModeClickListener);
        mEmojiButton.setTag("emoji");

        mExpandButton = (ImageView) mainView.findViewById(R.id.bExpand);
        mExpandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disableMessageMode(true);
            }
        });


        mGifButton = (ImageView) findViewById(R.id.bGIF);
        mGifButton.setOnClickListener(messageModeClickListener);
        mGifButton.setTag("gif");

        mCameraButton = (ImageView) mainView.findViewById(R.id.bCamera);
        mCameraButton.setOnClickListener(messageModeClickListener);
        mCameraButton.setTag("camera");

        mGalleryButton = (ImageView) mainView.findViewById(R.id.bGallery);
        mGalleryButton.setOnClickListener(messageModeClickListener);
        mGalleryButton.setTag("gallery");

        mMoreButton = (ImageView) mainView.findViewById(R.id.bPlus);
        mMoreButton.setOnClickListener(messageModeClickListener);
        mMoreButton.setTag("more");

        mQRButton = (ImageView) mainView.findViewById(R.id.bQR);
        mQRButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDialog = UIUtils.showQRDialog(MainActivity.this, mUser);
            }
        });

        mEtMessage = mainView.findViewById(R.id.etMessage);
        mEtMessage.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                SurespotLog.d(TAG, "on EditorAction (text)");

                Friend friend = mCurrentFriend;
                if (friend != null) {
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        SurespotLog.d(TAG, "on EditorAction ACTION_SEND (text)");
                        sendMessage(friend.getName(), v.getText().toString());
                        handled = true;
                    }
                    else {
                        //if we pasted the message it might have carriage returns which cause it to send, so suppress
                        if (mEnterToSend && actionId == EditorInfo.IME_NULL && event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                            SurespotLog.d(TAG, "on EditorAction ACTION_DOWN (text)");
                            sendMessage(friend.getName(), v.getText().toString());
                            handled = true;
                        }
                    }
                }
                return handled;
            }
        });

        TextWatcher tw = new ChatTextWatcher();
        mEtMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_MESSAGE_LENGTH)});
        SurespotLog.d(TAG, "adding text watcher");
        mEtMessage.addTextChangedListener(tw);
        mEtMessage.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    setMessageMode(MESSAGE_MODE_KEYBOARD);
                }
                return false;
            }
        });

        mEtInvite = (EditText) mainView.findViewById(R.id.etInvite);
        mEtInvite.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter()});
        mEtInvite.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;

                if (mCurrentFriend == null) {
                    if (actionId == EditorInfo.IME_ACTION_DONE || (actionId == EditorInfo.IME_NULL && event != null && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        inviteFriend();
                        handled = true;
                    }
                }
                return handled;
            }
        });

        mEtGifSearch = (EditText) mainView.findViewById(R.id.etGiphy);
        mEtGifSearch.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                SurespotLog.d(TAG, "onEditorAction, actionId: %d, keyEvent: %s", actionId, event);

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    final CharSequence text = v.getText();

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            //   showGifDrawer();
                            if (!TextUtils.isEmpty(text)) {
                                mGifHandler.searchGifs(text.toString());
                            }
                            v.setText("");
                        }
                    };

                    mHandler.post(runnable);
                    handled = true;
                }

                return handled;
            }
        });

        mEtGifSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setButtonText();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mEtGifSearch.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_SEARCH_LENGTH)});
        mGiphySearchFieldLayout = mainView.findViewById(R.id.giphySearchFieldLayout);

        mPoweredByGiphyView = mainView.findViewById(R.id.poweredByGiphy);
        mPoweredByGiphyView.setImageResource(UIUtils.isDarkTheme(this) ? R.drawable.powered_by_giphy_dark : R.drawable.powered_by_giphy_light);
        mPoweredByGiphyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        String searchText = mEtGifSearch.getText().toString();
                        if (!TextUtils.isEmpty(searchText)) {
                            mGifHandler.searchGifs(searchText);
                        }
                        mEtGifSearch.setText("");
                    }
                };

                mHandler.post(runnable);
            }
        });
    }

    private void switchUser(String identityName) {
        SurespotLog.d(TAG, "switchUser, mUser: %s, identityName: %s", mUser, identityName);
        if (!identityName.equals(mUser)) {
            ChatManager.pause(mUser, this.hashCode());
            ChatManager.detach(this, this.hashCode());
            mUser = identityName;

            CredentialCachingService ccs = SurespotApplication.getCachingService(this);
            if (ccs == null || !ccs.setSession(this, mUser)) {
                launchLogin();
                return;
            }

            IdentityController.setLastUser(this, mUser);
            setupUser();
            launch();
        }
        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
    }

    private boolean needsSignup() {
        Intent intent = getIntent();
        // figure out if we need to create a user
        if (!IdentityController.hasIdentity(this) || intent.getBooleanExtra("create", false)) {

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
        String messageTo = intent.getStringExtra(MESSAGE_TO);

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


    private void postServiceProcess() {
        CredentialCachingService ccs = SurespotApplication.getCachingService(this);
        if (ccs == null || !ccs.setSession(this, mUser)) {
            launchLogin();
            return;
        }

        // we're loading so build the ui
        setContentView(R.layout.activity_main);
        setupGlobal();
        setupUser();
        launch();
    }

    private void setupGlobal() {
        if (checkPlayServices()) {
            // Start IntentService to register this application with GCM.

            Intent intent = new Intent(this, RegistrationIntentService.class);
            RegistrationIntentService.enqueueWork(this, intent);
        }

        setupBilling();

        // set volume control buttons
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mHomeImageView = (ImageView) findViewById(android.R.id.home);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //drawer
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setScrimColor(Color.argb(224, 0, 0, 0));
        View header = getLayoutInflater().inflate(R.layout.drawer_header, mDrawerList, false);
        mDrawerList.addHeaderView(header, null, false);

        updateDrawer();
    }

    private void updateDrawer() {

        if (mDrawerList != null) {
            List<String> ids = IdentityController.getIdentityNames(this);
            final String[] identityNames = ids.toArray(new String[ids.size()]);

            mDrawerList.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    switchUser(identityNames[position - 1]);
                    mDrawerList.setItemChecked(position, true);
                }
            });

            mDrawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, identityNames));
            for (int i = 0; i < identityNames.length; i++) {
                if (identityNames[i].equals(mUser)) {
                    mDrawerList.setItemChecked(i + 1, true);
                    break;
                }
            }
        }
    }

    private void setupUser() {
        //set username
        NetworkManager.getNetworkController(this, mUser).set401Handler(m401Handler);

        mContentFrame = (FrameLayout) findViewById(R.id.content_frame);
        View currentMainView = mContentFrame.getChildAt(0);
        View mainView = getLayoutInflater().inflate(R.layout.main_view, mContentFrame, false);
        mContentFrame.addView(mainView);
        if (currentMainView != null) {
            mContentFrame.removeView(currentMainView);
        }

        SurespotDrawerLayout sdl = (SurespotDrawerLayout) findViewById(R.id.drawer_layout);
        sdl.setMainActivity(this);

        mActivityLayout = (SoftKeyboardLayout) mainView.findViewById(R.id.chatLayout);
        mActivityLayout.setOnKeyboardShownListener(new SoftKeyboardLayout.OnKeyboardShownListener() {
            @Override
            public void onKeyboardShown(boolean visible) {
                Configuration config = getResources().getConfiguration();


                //SurespotLog.d(TAG, "OnKeyboardShown: visible %b, height: %d", visible, mActivityLayout.getKeyboardHeight());
                //gif doesn't have a drawer so don't hide it
//                if (!visible &&
//                        mActivityLayout.getPaddingBottom() == 0 &&
//                        messageModeActive() && mCurrentMessageMode != null &&
//                        !mCurrentMessageMode.equals(MESSAGE_MODE_GIF)) {
//                    SurespotLog.d(TAG, "OnKeyboardShown: hiding emoji drawer");
//                    disableMessageMode(false);
//                }
//                else {


                if (visible && mWaitingForKeyboardToShow) {
                    if (MESSAGE_MODE_GALLERY.equals(mCurrentMessageMode)) {
                        setGalleryMode();
                        mWaitingForKeyboardToShow = false;
                    }
                    else {
                        if (MESSAGE_MODE_EMOJI.equals(mCurrentMessageMode)) {
                            switchViews();
                            mWaitingForKeyboardToShow = false;
                        }
                        else {
                            SurespotLog.d(TAG, "OnKeyboardShown: hiding emoji drawer waiting for keyboard to show");
                            disableMessageMode(false);
                            mWaitingForKeyboardToShow = false;
                        }
                    }


                }

                //  }
            }
        });

        TitlePageIndicator titlePageIndicator = (TitlePageIndicator) mainView.findViewById(R.id.indicator);
        ChatManager.attachChatController(
                this,
                mUser,
                this.hashCode(),
                (ViewPager) mainView.findViewById(R.id.pager),
                getSupportFragmentManager(),
                titlePageIndicator,
                mMenuItems,
                new IAsyncCallback<Boolean>() {
                    @Override
                    public void handleResponse(Boolean inProgress) {
                        setHomeProgress(inProgress);
                    }
                },
                new IAsyncCallback<Void>() {

                    @Override
                    public void handleResponse(Void result) {
                        handleSendIntent();

                    }
                },

                new IAsyncCallback<Friend>() {

                    @Override
                    public void handleResponse(Friend result) {
                        handleTabChange(result);
                    }
                },
                m401Handler
        );

        setupChatControls(mainView);
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

        ChatController cc = ChatManager.getChatController(mUser);
        if (cc == null) {
            SurespotLog.d(TAG, "launch, null chatcontroller, bailing");
            return;
        }
        cc.setAutoInviteData(getAutoInviteData(intent));


        String action = intent.getAction();
        String type = intent.getType();
        String messageTo = intent.getStringExtra(MESSAGE_TO);
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

            cc.setCurrentChat(name);
        }

        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null)) {
            // need to select a user so put the chat controller in select mode
            // see if we can set the mode
            if (cc.setMode(ChatController.MODE_SELECT)) {
                Utils.configureActionBar(this, getString(R.string.send), getString(R.string.main_action_bar_right), true);
                SurespotLog.d(TAG, "started from SEND");

                cc.setCurrentChat(null);
                mSet = true;
            }
            else {
                Utils.clearIntent(intent);
            }
        }

        if (!mSet) {
            Utils.configureActionBar(this, "", mUser, true);
            String lastName = Utils.getUserSharedPrefsString(getApplicationContext(), mUser, SurespotConstants.PrefNames.LAST_CHAT);
            if (lastName != null) {
                SurespotLog.d(TAG, "using LAST_CHAT");
                name = lastName;
            }

            cc.setCurrentChat(name);
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

        SurespotLog.d(TAG, "onResume %d, mUnlocking: %b, mLaunched: %b, mResumed: %b, mPaused: %b", this.hashCode(), mUnlocking, mLaunched, mResumed, mPaused);
        startWatchingExternalStorage();
        SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
        mEnterToSend = sp.getBoolean("pref_enter_to_send", true);

        // if we had to unlock and we're resuming for a 2nd time and we have the caching service
        if (mUnlocking && mPaused) {
            SurespotLog.d(TAG, "setting mUnlocking to false");
            mUnlocking = false;

            if (SurespotApplication.getCachingService(this) != null) {
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


        setBackgroundImage();
        setEditTextHints();
        mEtMessage.setText(Utils.getUserSharedPrefsString(this, mUser, "message_text"));
        ChatManager.resume(mUser, this.hashCode());
        VoiceController.setPlayCompletedCallback(new VoicePlayCompletedHandler());
    }

    private class VoicePlayCompletedHandler implements IAsyncCallback<SurespotMessage> {

        @Override
        public void handleResponse(SurespotMessage message) {
            SurespotLog.d(TAG, "voice message play completed");
            //if we're still on the same tab
            if (message != null && mCurrentFriend != null && message.getFrom().equals(mCurrentFriend.getName())) {
                //tell the adapter
                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null) {
                    cc.handleVoiceMessagePlayed(message);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SurespotLog.d(TAG, "onPause %d", this.hashCode());

        mPaused = true;

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

        if (messageModeActive()) {
            disableMessageMode(false);
        }

        try {
            hideKeyboard();
        }
        catch (Exception e) {
        }
        ChatManager.pause(mUser, this.hashCode());

        if (mEtMessage != null) {
            if (TextUtils.isEmpty(mEtMessage.getText())) {
                Utils.removeUserPref(this, mUser, "message_text");
            }
            else {
                Utils.putUserSharedPrefsString(this, mUser, "message_text", mEtMessage.getText().toString());
            }
        }

        mResumed = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SurespotLog.d(TAG, "onActivityResult, requestCode: " + requestCode);

        switch (requestCode) {
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
                        ChatUtils.uploadFriendImageAsync(this, selectedImageUri, mUser, to, new IAsyncCallbackTriplet<String, String, String>() {

                            @Override
                            public void handleResponse(String url, String version, String iv) {
                                try {
                                    File file = new File(new URI(selectedImageUri.toString()));
                                    SurespotLog.d(TAG, "deleted temp image file: %b", file.delete());
                                }
                                catch (URISyntaxException e) {
                                }

                                ChatController cc = ChatManager.getChatController(mUser);

                                if (cc == null || url == null) {
                                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_friend_image));
                                }
                                else {
                                    if (cc != null) {
                                        cc.setImageUrl(to, url, version, iv, true);
                                    }
                                }
                            }
                        });
                    }
                }
                break;

            case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FILE:

                if (resultCode == RESULT_OK) {
                    final ChatController cc = ChatManager.getChatController(mUser);
                    if (cc == null) {
                        SurespotLog.w(TAG, "onOptionItemSelected select File: chat controller null, bailing");
                        return;
                    }
                    String currentChat = cc.getCurrentChat();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && data.getClipData() != null) {
                        SurespotLog.d(TAG, "chose, clip data: %s", data.getClipData());
                        if (mUser != null && currentChat != null) {
                            FileTransferUtils.handleClipDataFileSelection(this, cc, mUser, currentChat, data);
                        }
                    }
                    else if (data.getData() != null) {
                        SurespotLog.d(TAG, "chose, data: %s", data);

                        if (mUser != null && currentChat != null) {

                            FileTransferUtils.uploadFileAsync(this, cc, mUser, currentChat, data.getData());
                        }
                    }
                    else {
                        SurespotLog.i(TAG, "Not able to support multiple file selection and no appropriate data returned from file picker");
                        Utils.makeLongToast(this, getString(R.string.could_not_select_image));
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
            case SurespotConstants.IntentRequestCodes.REQUEST_SETTINGS:
                if (SurespotApplication.getThemeChanged()) {
                    ChatManager.detach(this, this.hashCode());
                    finish();
                    final Intent intent = getIntent();
                    intent.putExtra("themeChanged", true);
                    startActivity(intent);
                }
                else {
                    //update drawer with identities as a new one may have been restored
                    updateDrawer();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        SurespotLog.d(TAG, "onCreateOptionsMenu");

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);

        mMenuOverflow = menu;

        //    mMenuItems.add(menu.findItem(R.id.menu_mute_bar));
        mMenuItems.add(menu.findItem(R.id.menu_close_all_tabs));
        mMenuItems.add(menu.findItem(R.id.menu_close_bar));
//        mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));
//
//        MenuItem captureItem = menu.findItem(R.id.menu_capture_image_bar);
//        if (hasCamera()) {
//            mMenuItems.add(captureItem);
//            captureItem.setEnabled(FileUtils.isExternalStorageMounted());
//        }
//        else {
//            SurespotLog.d(TAG, "hiding capture image menu option");
//            menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
//        }

        mMenuItems.add(menu.findItem(R.id.menu_clear_messages));
        // nag nag nag

        //mMenuItems.add(menu.findItem(R.id.menu_purchase_voice));


        if (mUser != null) {
            ChatController cc = ChatManager.getChatController(mUser);
            if (cc != null) {
                cc.enableMenuItems(mCurrentFriend);
            }
        }

        //
        enableImageMenuItems();
        return true;
    }

    private boolean hasCamera() {
        return Camera.getNumberOfCameras() > 0;
    }

    public void uploadFriendImage(String name, String alias) {
        Intent intent = new Intent(this, ImageSelectActivity.class);
        intent.putExtra("to", name);
        intent.putExtra("toAlias", alias);
        intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_SMALL);
        // set start intent to avoid restarting every rotation
        intent.putExtra("start", true);
        intent.putExtra("friendImage", true);
        startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FRIEND_IMAGE);
    }

    private ImageCaptureHandler mImageCaptureHandler;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        final ChatController cc = ChatManager.getChatController(mUser);
        if (cc == null) {
            SurespotLog.w(TAG, "onOptionItemSelected chat controller null, bailing");
            return false;
        }
        final String currentChat = cc.getCurrentChat();
        switch (item.getItemId()) {
            case android.R.id.home:
                // This is called when the Home (Up) button is pressed
                // in the Action Bar.
                // showUi(!mChatsShowing);
                if (TextUtils.isEmpty(cc.getCurrentChat())) {
                    if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                        mDrawerLayout.closeDrawer(GravityCompat.START);
                    }
                    else {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }
                }
                else {
                    cc.setCurrentChat(null);
                }
                return true;
//            case R.id.menu_mute_bar:
//                if (mCurrentFriend != null) {
//                    mCurrentFriend.setMuted(!mCurrentFriend.isMuted());
//                    cc.saveFriends();
//                }
//                return true;
            case R.id.menu_close_all_tabs:
                cc.closeAllTabs();
                return true;
            case R.id.menu_close_bar:

                cc.closeTab();
                return true;
//            case R.id.menu_send_file_bar:
//                if (currentChat == null || mCurrentFriend == null) {
//                    return true;
//                }
//
//                // can't send images to deleted folk
//                if (mCurrentFriend.isDeleted()) {
//                    return true;
//                }
//
//
//                String plural = "";
//                Intent intent = new Intent();
//                intent.setType("*/*");
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
//                    plural = "s";
//                }
//
//                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
//                intent.setAction(Intent.ACTION_GET_CONTENT);
//                SurespotLog.d(TAG, "startActivityForResult");
//                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_file) + plural), SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FILE);
//
//
//                return true;
            case R.id.menu_settings_bar:

                new AsyncTask<Void, Void, Void>() {
                    protected Void doInBackground(Void... params) {
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        intent.putExtra("username", mUser);
                        startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SETTINGS);
                        return null;
                    }
                }.execute();
                return true;
            case R.id.menu_logout_bar:
                SharedPreferences spl = Utils.getGlobalSharedPrefs(this);
                boolean confirmlogout = spl.getBoolean("pref_confirm_logout", true);
                if (confirmlogout) {
                    mDialog = UIUtils.createAndShowConfirmationDialog(this, getString(R.string.confirm_logout_message), getString(R.string.confirm_logout_title),
                            getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        logout();
                                    }
                                }
                            });
                }
                else {
                    logout();
                }

                return true;
            case R.id.menu_invite_external:
                UIUtils.sendInvitation(MainActivity.this, NetworkManager.getNetworkController(this, mUser), mUser);
                return true;
            case R.id.menu_clear_messages:
                SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
                boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
                if (confirm) {
                    mDialog = UIUtils.createAndShowConfirmationDialog(this, getString(R.string.delete_all_confirmation), getString(R.string.delete_all_title),
                            getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        cc.deleteMessages(currentChat);
                                    }
                                }
                            });
                }
                else {
                    cc.deleteMessages(currentChat);
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

    private void logout() {
        IdentityController.logout(this, mUser, false);

        Intent finalIntent = new Intent(MainActivity.this, LoginActivity.class);
        finalIntent.putExtra(MESSAGE_TO, mUser);
        finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MainActivity.this.startActivity(finalIntent);
        finish();
    }


    @Override
    protected void onDestroy() {
        //SurespotLog.d(TAG, "onDestroy");
        super.onDestroy();

        //calling finish and starting the activity again when we set the theme (see onActivityResult)
        //results in an onDestroy being called in the new instance (&$&*% AFTER it is loaded
        //use the global theme change flag to work around this
        SurespotLog.d(TAG, "onDestroy %d themeChanged: %b", this.hashCode(), SurespotApplication.getThemeChanged());

        if (!SurespotApplication.getThemeChanged()) {
            destroy();
        }
        else {
            SurespotApplication.setThemeChanged(null);
        }

    }

    private void destroy() {
        SurespotLog.d(TAG, "destroy unbinding");
        ChatManager.detach(this, this.hashCode());
        Utils.removeUserPref(this, mUser, "message_text");
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

//        if (mMenuItems != null) {
//            for (MenuItem menuItem : mMenuItems) {
//                if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {
//
//                    menuItem.setEnabled(mExternalStorageWriteable);
//
//                }
//            }
//        }
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
    }

    private boolean mInProgress;

    private void setHomeProgress(final boolean inProgress) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mHomeImageView == null) {
                    mInProgress = false;
                    return;
                }
                SurespotLog.d(TAG, "progress status changed to: %b", inProgress);
                if (inProgress) {
                    //if it's not already showing show it
                    if (!mInProgress) {
                        UIUtils.showProgressAnimation(MainActivity.this, mHomeImageView);
                    }
                }
                else {
                    mHomeImageView.clearAnimation();
                }

                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null) {
                    cc.enableMenuItems(mCurrentFriend);
                }
                mInProgress = inProgress;
            }
        };

        mHandler.post(runnable);
    }


    class ChatTextWatcher implements TextWatcher {
        private int mDelta;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            setButtonText();
            mDelta = count - before;
            SurespotLog.v(TAG, "onTextChanged, start: %d, before: %d, count: %d, delta: %d", start, before, count, mDelta);
        }

        @Override
        public void afterTextChanged(Editable s) {
            //if they pasted or shared the text the delta will be bigger than 1
            // don't do anything as they may want to edit first
            if (mDelta > 1) {
                return;
            }

            String message = s.toString();
            if (message.length() > 0 && MainActivity.this.mEnterToSend && message.contains("\n")) {

                //strip last carriage return before sending
                if (message.endsWith("\n")) {
                    message = message.substring(0, message.length() - 1);
                }

                //if the carriage return was the only character, there will be no characters left so reset the edit text
                if (message.length() == 0) {
                    s.clear();
                    return;
                }

                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null && mCurrentFriend != null && !cc.isFriendDeleted(mCurrentFriend.getName())) {
                    sendMessage(mCurrentFriend.getName(), message);
                }
            }
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
            }
            else {
                if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

                    final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

                    // Utils.makeToast(getActivity(), getString(R.string.uploading_image));

                    SurespotLog.d(TAG, "received image data, upload image, uri: %s", imageUri);
                    ChatController cc = ChatManager.getChatController(mUser);
                    if (cc != null) {
                        ChatUtils.uploadPictureMessageAsync(
                                this,
                                cc,
                                imageUri,
                                mUser,
                                mCurrentFriend.getName(),
                                true);
                    }
                    else {
                        //TODO
                    }
                }
            }
        }
        else {
            if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                Utils.configureActionBar(this, "", mUser, true);
                if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {
                    ChatController cc = ChatManager.getChatController(mUser);
                    if (cc != null) {
                        ArrayList<Parcelable> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);

                        for (Parcelable p : uris) {
                            final Uri imageUri = (Uri) p;

                            SurespotLog.d(TAG, "received image data, upload image, uri: %s", imageUri);

                            ChatUtils.uploadPictureMessageAsync(
                                    this,
                                    cc,
                                    imageUri,
                                    mUser,
                                    mCurrentFriend.getName(),
                                    true);
                        }
                    }
                }
            }
        }


        Utils.clearIntent(getIntent());
    }


    private void sendMessage(String username, String message) {
        if (!message.isEmpty()) {
            ChatController cc = ChatManager.getChatController(mUser);
            if (cc != null) {
                cc.sendMessage(username, message, SurespotConstants.MimeTypes.TEXT);
                TextKeyListener.clear(mEtMessage.getText());
            }
        }
    }

    public boolean backButtonPressed() {
        SurespotLog.d(TAG, "backButtonPressed, keyboardVisible: %b, message mode: %b, emoji visible: %b", mActivityLayout.isKeyboardVisible(), mCurrentMessageMode, messageModeActive());

        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        // if (messageModeActive()) {
        disableMessageMode(false);
        //  }

        //returning false will cause the keyboard to be hidden
        if (mActivityLayout.isKeyboardVisible()) {
            SurespotLog.d(TAG, "keyboard showing backButtonPressed returning false");
            return false;
        }

        //go to home page if we not
        if (mCurrentFriend != null)

        {
            ChatController cc = ChatManager.getChatController(mUser);
            if (cc != null) {
                cc.setCurrentChat(null);
                SurespotLog.d(TAG, "backButtonPressed returning true");
                return true;
            }
        }

        SurespotLog.d(TAG, "backButtonPressed returning false at the bottom");
        return false;
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
            NetworkManager.getNetworkController(this, mUser).invite(friend, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
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
                        ChatController cc = ChatManager.getChatController(mUser);
                        if (cc != null) {
                            if (cc.getFriendAdapter().addFriendInvited(friend)) {
                                Utils.makeToast(MainActivity.this, getString(R.string.has_been_invited, friend));
                            }
                            else {
                                Utils.makeToast(MainActivity.this, getString(R.string.has_accepted, friend));
                            }
                        }
                        else {
                            Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
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
        }
    }

    public void setButtonText() {
        if (mCurrentFriend == null) {
            mIvInvite.setVisibility(View.VISIBLE);
            mIvVoice.setVisibility(GONE);
            mIvHome.setVisibility(GONE);
            mIvSend.setVisibility(GONE);
        }
        else {
            if (mCurrentFriend.isDeleted()) {
                mIvInvite.setVisibility(GONE);
                mIvVoice.setVisibility(GONE);
                mIvHome.setVisibility(View.VISIBLE);
                mIvSend.setVisibility(GONE);
            }
            else {
                if (mEtMessage.getText().length() > 0) {
                    mIvInvite.setVisibility(GONE);
                    mIvVoice.setVisibility(GONE);
                    mIvHome.setVisibility(GONE);
                    mIvSend.setVisibility(View.VISIBLE);
                }
                else {
                    mIvInvite.setVisibility(GONE);
                    SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
                    boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);

                    if (disableVoice) {
                        mIvVoice.setVisibility(GONE);
                        mIvHome.setVisibility(View.VISIBLE);
                    }
                    else {
                        mIvVoice.setVisibility(View.VISIBLE);
                        mIvHome.setVisibility(GONE);
                    }

                    mIvSend.setVisibility(GONE);
                }
            }
        }
    }

    private void updateMessageBar() {
        SurespotLog.d(TAG, "updateMessageBar");
        boolean expand = false;
        View bHighlight = null;
        SharedPreferences settings = getSharedPreferences("surespot_preferences", android.content.Context.MODE_PRIVATE);
        boolean black = settings.getBoolean("pref_black", false);
        int selectedMask = ContextCompat.getColor(this, com.rockerhieu.emojicon.R.color.selectedMask);
        int unselectedMask = ContextCompat.getColor(this, black ? com.rockerhieu.emojicon.R.color.unselectedMaskDark : com.rockerhieu.emojicon.R.color.unselectedMaskLight);


        if (mCurrentMessageMode == null) {
            if (mCurrentFriend == null) {
                mQRButton.setVisibility(View.VISIBLE);
                mExpandButton.setVisibility(View.GONE);
                mEmojiButton.setVisibility(View.GONE);
                mGifButton.setVisibility(View.GONE);
                mCameraButton.setVisibility(View.GONE);
                mGalleryButton.setVisibility(View.GONE);
                mMoreButton.setVisibility(View.GONE);
            }
            else {
                mQRButton.setVisibility(View.GONE);
                mExpandButton.setVisibility(View.GONE);
                mEmojiButton.setVisibility(View.VISIBLE);
                mGifButton.setVisibility(View.VISIBLE);
                mCameraButton.setVisibility(View.VISIBLE);
                mGalleryButton.setVisibility(View.VISIBLE);
                mMoreButton.setVisibility(View.GONE);
                expand = true;
            }
        }
        else {

            View bShow;


            switch (mCurrentMessageMode) {
                case MESSAGE_MODE_KEYBOARD:
                    bShow = mExpandButton;
                    bHighlight = mExpandButton;
                    break;
                case MESSAGE_MODE_EMOJI:
                    bShow = mEmojiButton;
                    bHighlight = mEmojiButton;
                    break;
                case MESSAGE_MODE_GIF:
                    bShow = mGifButton;
                    bHighlight = mGifButton;
                    break;
                case MESSAGE_MODE_CAMERA:
                    bShow = mCameraButton;
                    bHighlight = mCameraButton;
                    expand = true;
                    break;
                case MESSAGE_MODE_GALLERY:
                    bShow = mGalleryButton;
                    bHighlight = mGalleryButton;
                    expand = true;
                    break;
                case MESSAGE_MODE_MORE:
                    bShow = mMoreButton;
                    bHighlight = mMoreButton;
                    expand = true;
                    break;
                default:
                    bShow = mEmojiButton;
                    break;

            }

            if (!expand) {
                mExpandButton.setVisibility(bShow == mExpandButton ? View.VISIBLE : View.GONE);
                mEmojiButton.setVisibility(bShow == mEmojiButton ? View.VISIBLE : View.GONE);
                mGifButton.setVisibility(bShow == mGifButton ? View.VISIBLE : View.GONE);
                mGalleryButton.setVisibility(bShow == mGalleryButton ? View.VISIBLE : View.GONE);
                mCameraButton.setVisibility(bShow == mCameraButton ? View.VISIBLE : View.GONE);
                mMoreButton.setVisibility(bShow == mMoreButton ? View.GONE : View.GONE);
            }
            else {
                mQRButton.setVisibility(View.GONE);
                mExpandButton.setVisibility(View.GONE);
                mEmojiButton.setVisibility(View.VISIBLE);
                mGifButton.setVisibility(View.VISIBLE);
                mCameraButton.setVisibility(View.VISIBLE);
                mGalleryButton.setVisibility(View.VISIBLE);
                mMoreButton.setVisibility(View.GONE);
            }
        }

        mExpandButton.setColorFilter(bHighlight == mExpandButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);
        mEmojiButton.setColorFilter(bHighlight == mEmojiButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);
        mGifButton.setColorFilter(bHighlight == mGifButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);
        mCameraButton.setColorFilter(bHighlight == mCameraButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);
        mGalleryButton.setColorFilter(bHighlight == mGalleryButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);
        mMoreButton.setColorFilter(bHighlight == mMoreButton ? selectedMask : unselectedMask, PorterDuff.Mode.SRC_IN);

        if (expand) {
            expand();
        }
        else {
            collapse();
        }
    }


    public void expand() {

        if (isCollapsed) {
            isCollapsed = false;
            // Older versions of android (pre API 21) cancel animations for views with a height of 0.
            mButtons.getLayoutParams().width = 1;

            ViewGroup.LayoutParams lp = mButtons.getLayoutParams();
            int widthSpec = View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.UNSPECIFIED);
            mButtons.measure(widthSpec, LayoutParams.MATCH_PARENT);
            final int initialWidth = mButtons.getWidth();
            final int targetWidth = mButtons.getMeasuredWidth();

            SurespotLog.d(TAG, "expand, targetWidth: %d, currentWidth: %d", targetWidth, mButtons.getWidth());

            Animation a = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int newWidth = (int) (initialWidth + ((targetWidth - initialWidth) * interpolatedTime));
                    SurespotLog.d(TAG, "expand, newWidth: %d, time: %f", newWidth, interpolatedTime);
                    mButtons.getLayoutParams().width = newWidth;
                    mButtons.requestLayout();
                }

                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };

            // 1dp/ms
            a.setDuration((int) (targetWidth / mButtons.getContext().getResources().getDisplayMetrics().density));
            mButtons.startAnimation(a);
        }
    }


    public void collapse() {
        if (!isCollapsed) {
            isCollapsed = true;

            mButtons.getLayoutParams().width = 1;

            ViewGroup.LayoutParams lp = mButtons.getLayoutParams();

            int widthSpec = View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.UNSPECIFIED);
            mButtons.measure(widthSpec, LayoutParams.MATCH_PARENT);
            final int initialWidth = mButtons.getWidth();
            final int finalWidth = mButtons.getMeasuredWidth();
            //SurespotLog.d(TAG, "collapse, initialWidth: %d, desiredWidth: %d", initialWidth, finalWidth);

            if (initialWidth < finalWidth) return;

            Animation a = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {

                    int newWidth = initialWidth - (int) ((initialWidth - finalWidth) * interpolatedTime);
                    //SurespotLog.d(TAG, "collapse, newWidth: %d", newWidth);
                    mButtons.getLayoutParams().width = newWidth;
                    mButtons.requestLayout();

                }

                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };

            // 1dp/ms

            a.setDuration((int) ((initialWidth - finalWidth) / mButtons.getContext().getResources().getDisplayMetrics().density));
            //a.setDuration(1000);
            mButtons.startAnimation(a);
        }
    }

    private void handleTabChange(Friend friend) {
        SurespotLog.v(TAG,
                "handleTabChange, mFriendHasBeenSet: %b, currentFriend is null: %b",
                mFriendHasBeenSet, mCurrentFriend == null);

        if (friend == null) {

            if (messageModeActive()) {
                disableMessageMode(false);
            }
            mEtMessage.setVisibility(GONE);
            mEtInvite.setVisibility(View.VISIBLE);


            if (mActivityLayout.isKeyboardVisible()) {
                SurespotLog.v(TAG, "handleTabChange requesting invite edit text focus");
                requestFocus(mEtInvite);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_drawer);
            }
            else {

                ViewGroup home = (ViewGroup) findViewById(android.R.id.home).getParent();
                // get the first child (up imageview)
                ((ImageView) home.getChildAt(0))
                        // change the icon according to your needs
                        .setImageResource(R.drawable.ic_drawer);
            }
        }
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_ab_back_holo_dark_am);
            }
            else {
                ViewGroup home = (ViewGroup) findViewById(android.R.id.home).getParent();
                // get the first child (up imageview)
                ((ImageView) home.getChildAt(0))
                        // change the icon according to your needs
                        .setImageResource(R.drawable.ic_ab_back_holo_dark_am);
            }

            if (friend.isDeleted()) {
                mEtMessage.setVisibility(GONE);


            }
            else {
                mEtMessage.setVisibility(View.VISIBLE);
            }

            mEtInvite.setVisibility(GONE);
            if (mActivityLayout.isKeyboardVisible()) {
                requestFocus(mEtMessage);
            }
        }

        // if keyboard is showing and we want to show emoji or vice versa, just toggle emoji
        mCurrentFriend = friend;
        setButtonText();
        updateMessageBar();
        mFriendHasBeenSet = true;
    }

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

        mEtGifSearch.setHint(R.string.search_gifs);

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
                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null) {
                    cc.assignFriendAlias(name, alias, new IAsyncCallback<Boolean>() {

                        @Override
                        public void handleResponse(Boolean result) {
                            if (!result) {
                                Utils.makeToast(MainActivity.this, getString(R.string.could_not_assign_friend_alias));
                            }
                        }
                    });
                }
                else {
                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_assign_friend_alias));
                }
            }
        });
    }

    public void removeFriendImage(final String name) {
        ChatController cc = ChatManager.getChatController(mUser);
        if (cc != null) {
            cc.removeFriendImage(name, new IAsyncCallback<Boolean>() {
                @Override
                public void handleResponse(Boolean result) {
                    if (!result) {
                        Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_image));
                    }
                }
            });
        }
        else {
            Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_image));
        }
    }

    public void removeFriendAlias(final String name) {
        ChatController cc = ChatManager.getChatController(mUser);
        if (cc != null) {
            cc.removeFriendAlias(name, new IAsyncCallback<Boolean>() {
                @Override
                public void handleResponse(Boolean result) {
                    if (!result) {
                        Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_alias));
                    }
                }
            });
        }
        else {
            Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_alias));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        SurespotLog.d(TAG, "onConfigurationChanged, screenHeight: %d, orientation: %d", newConfig.screenHeightDp, newConfig.orientation);
        hideKeyboard();
        disableMessageMode(false);
    }

    public boolean messageModeActive() {
        return !TextUtils.isEmpty(mCurrentMessageMode);
    }

    private void toggleMessageMode(String mode) {
        SurespotLog.d(TAG, "toggleMessageMode, mode: %s, currentMode: %s, emoji visible: %b", mode, mCurrentMessageMode, messageModeActive());

        if (messageModeActive()) {
            if (mode.equals(mCurrentMessageMode)) {


                mWaitingForKeyboardToShow = true;
                disableMessageMode(true);
                return;

            }
        }
        setMessageMode(mode);
    }

    private void createEmojiView() {
        if (mEmojiView == null) {
            mEmojiView = (EmojiconsView) LayoutInflater
                    .from(this).inflate(R.layout.emojicons, null, false);

            mEmojiView.setOnEmojiconBackspaceClickedListener(this);
            mEmojiView.setOnEmojiconClickedListener(this);
        }
    }

    private void setMessageMode(String messageMode) {
        SurespotLog.d(TAG, "setMessageMode, mode: %s", messageMode);

        final InputMethodManager input = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        final WindowManager wm = (WindowManager) this.getSystemService(Activity.WINDOW_SERVICE);
        mOldView = mMessageModeView;
        final View gifFrame = findViewById(R.id.gifFrame);

        switch (messageMode) {
            case MESSAGE_MODE_KEYBOARD:
                mCurrentMessageMode = MESSAGE_MODE_KEYBOARD;
                mMessageModeView = null;

                try {

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (mOldView != null && mOldView.getParent() != null && mOldView != mMessageModeView) {
                                wm.removeView(mOldView);
                            }
                        }
                    };
                    mHandler.postDelayed(runnable, 500);
                }
                catch (Exception e) {
                    SurespotLog.e(TAG, e, "error adding emoji view");
                    return;
                }

                requestFocus(mEtMessage);
                if (input != null) {
                    input.showSoftInput(mEtMessage, 0);
                }

                gifFrame.setVisibility(GONE);
                mGiphySearchFieldLayout.setVisibility(GONE);
                mEtMessage.setVisibility(View.VISIBLE);
                mSendButton.setVisibility(View.VISIBLE);

                mActivityLayout.findViewById(R.id.pager).setPadding(0, 0, 0, 0);
                updateMessageBar();
                break;
            case MESSAGE_MODE_EMOJI:
                mCurrentMessageMode = MESSAGE_MODE_EMOJI;
                createEmojiView();
                mMessageModeView = mEmojiView;
                gifFrame.setVisibility(GONE);
                mGiphySearchFieldLayout.setVisibility(GONE);
                mEtMessage.setVisibility(View.VISIBLE);
                mSendButton.setVisibility(View.VISIBLE);

                mActivityLayout.findViewById(R.id.pager).setPadding(0, 0, 0, 0);
                updateMessageBar();

                if (!mActivityLayout.isKeyboardVisible()) {
                    mWaitingForKeyboardToShow = true;
                    mEtMessage.setVisibility(View.VISIBLE);
                    requestFocus(mEtMessage);
                    if (input != null) {
                        input.showSoftInput(mEtMessage, 0);
                    }
                }
                else {
                    switchViews();
                }

                break;
            case MESSAGE_MODE_GIF:
                mCurrentMessageMode = MESSAGE_MODE_GIF;
                if (mGifHandler == null) {
                    mGifHandler = new GifSearchHandler(this, mUser, mActivityLayout);
                    mGifHandler.setGifSelectedCallback(new IAsyncCallback<GifDetails>() {
                        @Override
                        public void handleResponse(GifDetails result) {
                            if (result != null) {
                                sendGifMessage(result.getUrl());
                            }
                        }
                    });

                    mGifHandler.setGifSearchTextCallback(new IAsyncCallback<String>() {
                        @Override
                        public void handleResponse(String result) {
                            if (mEtGifSearch != null) {
                                mEtGifSearch.setText(result + " ");
                                mEtGifSearch.setSelection(mEtGifSearch.getText().length());
                            }
                        }
                    });
                }

                mGifHandler.refreshContextAndViews(this, mActivityLayout);

                try {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (mOldView != null && mOldView.getParent() != null) {
                                wm.removeView(mOldView);
                            }
                        }
                    };
                    mHandler.postDelayed(runnable, 500);
                }
                catch (Exception e) {
                    SurespotLog.e(TAG, e, "error adding emoji view");
                    return;
                }

                mGiphySearchFieldLayout.setVisibility(View.VISIBLE);
                requestFocus(mEtGifSearch);
                if (input != null) {
                    input.showSoftInput(mEtGifSearch, 0);
                }

                mEtInvite.setVisibility(View.INVISIBLE);
                mEtMessage.setVisibility(View.INVISIBLE);
                mSendButton.setVisibility(GONE);


                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        gifFrame.setVisibility(View.VISIBLE);

                        //hard coded to 150 in the view, easier than trying to measure the view here
                        int gifFrameHeight = (int) UIUtils.pxFromDp(MainActivity.this, 150);
                        mActivityLayout.findViewById(R.id.pager).setPadding(0, 0, 0, gifFrameHeight);
                        SurespotLog.d(TAG, "setMessageMode, gifFrameHeight: %d", gifFrameHeight);

                    }
                };

                mHandler.postDelayed(runnable, 100);
                mMessageModeView = null;

                updateMessageBar();
                break;
            case MESSAGE_MODE_GALLERY:
                // can't send images to deleted folk
                if (mCurrentFriend != null && mCurrentFriend.isDeleted()) {
                    return;
                }
                checkPermissionGallery(MainActivity.this);
                break;
            case MESSAGE_MODE_CAMERA:
                // can't send images to deleted folk
                if (mCurrentFriend != null && mCurrentFriend.isDeleted()) {
                    return;
                }

                //check permissions
                checkPermissionCamera(MainActivity.this);
                break;
        }

        //  SurespotLog.v(TAG, "setMode keyboard height: %d", keyboardHeight);
    }

    private void checkPermissionGallery(final Activity activity) {
        SurespotLog.d(TAG, "checkPermissionReadStorage");
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                UIUtils.createAndShowConfirmationDialog(
                        activity,
                        getString(R.string.need_storage_permission_gallery),
                        getString(R.string.permission_required),
                        getString(R.string.ok),
                        getString(R.string.cancel),
                        new IAsyncCallback<Boolean>() {
                            @Override
                            public void handleResponse(Boolean result) {
                                if (result) {
                                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
                                }
                            }
                        }
                );
            }
            else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
            }
        }
        else {
            showGallery();
        }
    }

    private void showGallery() {
        final InputMethodManager input = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        final View gifFrame = findViewById(R.id.gifFrame);

        mCurrentMessageMode = MESSAGE_MODE_GALLERY;
        gifFrame.setVisibility(GONE);
        mGiphySearchFieldLayout.setVisibility(GONE);
        mSendButton.setVisibility(View.VISIBLE);

        //wait until the keyboard's shown if it's not visible so we can get the height
        if (!mActivityLayout.isKeyboardVisible()) {
            mWaitingForKeyboardToShow = true;
            mEtMessage.setVisibility(View.VISIBLE);
            requestFocus(mEtMessage);
            if (input != null) {
                input.showSoftInput(mEtMessage, 0);
            }
        }
        else {
            setGalleryMode();
        }
    }


    private void checkPermissionCamera(final Activity activity) {
        SurespotLog.d(TAG, "checkPermissionReadStorage");
        //camera needs camera and write_external_storage
        if ((ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {

            captureImage();
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                UIUtils.createAndShowConfirmationDialog(
                        activity,
                        getString(R.string.need_camera_permission),
                        getString(R.string.permission_required),
                        getString(R.string.ok),
                        getString(R.string.cancel),
                        new IAsyncCallback<Boolean>() {
                            @Override
                            public void handleResponse(Boolean result) {
                                if (result) {
                                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
                                }
                            }
                        }
                );
            }
            else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE: {
                if ((grantResults.length > 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED) && (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    captureImage();
                }
                else {
                    UIUtils.createAndShowConfirmationDialog(
                            this,
                            getString(R.string.need_camera_permission),
                            getString(R.string.permission_required),
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            new IAsyncCallback<Boolean>() {
                                @Override
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
                                    }
                                }
                            });
                }
            }
            break;
            case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE: {
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    showGallery();
                }
                else {
                    UIUtils.createAndShowConfirmationDialog(
                            this,
                            getString(R.string.need_storage_permission_gallery),
                            getString(R.string.permission_required),
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            new IAsyncCallback<Boolean>() {
                                @Override
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);
                                    }
                                }
                            });
                }
            }
            break;
            case SurespotConstants.IntentRequestCodes.REQUEST_MICROPHONE: {
                if (grantResults.length ==0 || ((grantResults.length > 0) && (grantResults[0] != PackageManager.PERMISSION_GRANTED)) ) {
                    UIUtils.createAndShowConfirmationDialog(
                            this,
                            getString(R.string.need_mic_permission),
                            getString(R.string.permission_required),
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            new IAsyncCallback<Boolean>() {
                                @Override
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, SurespotConstants.IntentRequestCodes.REQUEST_MICROPHONE);
                                    }
                                    else {
                                        UIUtils.createAndShowConfirmationDialog(
                                                MainActivity.this,
                                                getString(R.string.disable_voice_messaging_permission),
                                                getString(R.string.pref_disable_voice),
                                                getString(R.string.ok),
                                                getString(R.string.cancel),
                                                new IAsyncCallback<Boolean>() {
                                                    @Override
                                                    public void handleResponse(Boolean result) {
                                                        if (result) {
                                                            //disable voice
                                                            Utils.putUserSharedPrefsBoolean(MainActivity.this, mUser,SurespotConstants.PrefNames.VOICE_DISABLED, true);
                                                            setButtonText();
                                                        }
                                                    }
                                                }
                                        );
                                    }
                                }
                            });
                }
            }
            break;
        }
    }

    private void captureImage() {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                if (mCurrentFriend == null || (mCurrentFriend != null && mCurrentFriend.isDeleted())) {
                    return null;
                }
                mImageCaptureHandler = new ImageCaptureHandler(mUser, mCurrentFriend.getName());
                mImageCaptureHandler.capture(MainActivity.this);
                return null;
            }
        }.execute();

    }

    private void checkPermissionVoice(final Activity activity) {
        SurespotLog.d(TAG, "checkPermissionMicrophone");
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                UIUtils.createAndShowConfirmationDialog(
                        activity,
                        getString(R.string.need_mic_permission),
                        getString(R.string.permission_required),
                        getString(R.string.ok),
                        getString(R.string.cancel),
                        new IAsyncCallback<Boolean>() {
                            @Override
                            public void handleResponse(Boolean result) {
                                if (result) {
                                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, SurespotConstants.IntentRequestCodes.REQUEST_MICROPHONE);
                                }
                                else {
                                    UIUtils.createAndShowConfirmationDialog(
                                            activity,
                                            getString(R.string.disable_voice_messaging_permission),
                                            getString(R.string.pref_disable_voice),
                                            getString(R.string.ok),
                                            getString(R.string.cancel),
                                            new IAsyncCallback<Boolean>() {
                                                @Override
                                                public void handleResponse(Boolean result) {
                                                    if (result) {
                                                        //disable voice
                                                        Utils.putUserSharedPrefsBoolean(MainActivity.this, mUser,SurespotConstants.PrefNames.VOICE_DISABLED, true);
                                                        setButtonText();
                                                    }
                                                }
                                            }
                                    );
                                }
                            }
                        }
                );
            }
            else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, SurespotConstants.IntentRequestCodes.REQUEST_MICROPHONE);
            }
        }
        else {
            if (mUser != null && mCurrentFriend != null) {
                VoiceController.startRecording(MainActivity.this, mUser, mCurrentFriend.getName());
            }
        }
    }

    private void switchViews() {
        int keyboardHeight = mActivityLayout.getKeyboardHeight();
        SurespotLog.d(TAG, "switchViews, mode: %s, keyboardHeight: %d", mCurrentMessageMode, keyboardHeight);
        mWindowLayoutParams = new WindowManager.LayoutParams();
        mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mWindowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        mWindowLayoutParams.token = this.getWindow().getDecorView().getWindowToken();
        mWindowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (mWindowLayoutParams != null) {
            mWindowLayoutParams.height = keyboardHeight;
            mWindowLayoutParams.width = UIUtils.getDisplaySize(this).x;
        }
        final WindowManager wm = (WindowManager) this.getSystemService(Activity.WINDOW_SERVICE);
        //mOldView = mMessageModeView;
        try {
            wm.addView(mMessageModeView, mWindowLayoutParams);
            Runnable runnable3 = new Runnable() {
                @Override
                public void run() {
                    if (mOldView != null && mOldView.getParent() != null && mOldView != mMessageModeView) {
                        wm.removeView(mOldView);
                    }
                }
            };
            mHandler.post(runnable3);
        }
        catch (Exception e) {
            SurespotLog.e(TAG, e, "error adding view");
            return;
        }

    }

    private void setGalleryMode() {
        int keyboardHeight = mActivityLayout.getKeyboardHeight();
        mGalleryModeHandler = new GalleryModeHandler(MainActivity.this, mUser, keyboardHeight, new IAsyncCallback<Uri>() {
            @Override
            public void handleResponse(final Uri uri) {
                if (uri != null) {
                    final ChatController cc = ChatManager.getChatController(mUser);
                    if (cc != null) {

                        SharedPreferences sp = MainActivity.this.getSharedPreferences(MainActivity.this.mUser, Context.MODE_PRIVATE);
                        boolean confirmSend = sp.getBoolean("pref_confirm_image_send", true);

                        if (confirmSend) {
                            mDialog = UIUtils.createAndShowConfirmationDialog(MainActivity.this, getString(R.string.confirm_image_send, mCurrentFriend.getNameOrAlias()), getString(R.string.send),
                                    getString(R.string.ok), getString(R.string.cancel), new IAsyncCallback<Boolean>() {
                                        public void handleResponse(Boolean result) {
                                            if (result) {
                                                ChatUtils.uploadPictureMessageAsync(
                                                        MainActivity.this,
                                                        cc,
                                                        uri,
                                                        mUser,
                                                        mCurrentFriend.getName(),
                                                        true);
                                            }
                                        }
                                    });
                        }
                        else {
                            ChatUtils.uploadPictureMessageAsync(
                                    MainActivity.this,
                                    cc,
                                    uri,
                                    mUser,
                                    mCurrentFriend.getName(),
                                    true);
                        }
                    }
                }
            }
        },
                new IAsyncCallback<Object>() {

                    @Override
                    public void handleResponse(Object result) {
                        final ChatController cc = ChatManager.getChatController(mUser);
                        final String currentChat = cc.getCurrentChat();
                        if (currentChat == null) {
                            return;
                        }
                        if (currentChat == null || mCurrentFriend == null) {
                            return;
                        }

                        // can't send images to deleted folk
                        if (mCurrentFriend.isDeleted()) {
                            return;
                        }

                        new AsyncTask<Void, Void, Void>() {
                            protected Void doInBackground(Void... params) {
                                if (mCurrentFriend == null) {
                                    return null;
                                }
                                Intent intent = new Intent(MainActivity.this, ImageSelectActivity.class);
                                intent.putExtra("to", currentChat);
                                intent.putExtra("toAlias", mCurrentFriend.getNameOrAlias());
                                intent.putExtra("from", mUser);
                                intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_LARGE);
                                // set start intent to avoid restarting every rotation
                                intent.putExtra("start", true);
                                intent.putExtra("friendImage", false);
                                startActivity(intent);
                                return null;
                            }
                        }.execute();
                    }
                }
        );

        mGalleryView = getLayoutInflater().inflate(R.layout.gallery_message_mode_view, null, false);
        mMessageModeView = mGalleryView;
        switchViews();
        mGalleryModeHandler.refreshContextAndViews(this, mGalleryView);
        updateMessageBar();
    }

    private void scanFiles(final IAsyncCallbackTuple<String, Uri> callback) {
        callback.handleResponse(null, null);
        return;
//        SurespotLog.d(TAG,"scanFiles");
//        ArrayList<String> toBeScanned = new ArrayList<String>();
//
//
//        String[] scanFolders = new String[]{Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES};
//
//        for (String scanFolder : scanFolders) {
//            File f = Environment.getExternalStoragePublicDirectory(scanFolder);
//            traverse(f, toBeScanned);
//        }
//
//        //traverse(Environment.getExternalStorageDirectory(), toBeScanned);
//
//        //scanning files
//        SurespotLog.d(TAG, "scanning %d files: ", toBeScanned.size());
//        MediaScannerConnection.scanFile(this, toBeScanned.toArray(new String[]{}), null, new MediaScannerConnection.OnScanCompletedListener() {
//            @Override
//            public void onScanCompleted(String path, Uri uri) {
//                SurespotLog.d(TAG, "scan completed");
//                callback.handleResponse(path, uri);
//            }
//        });
    }

    public void traverse(File dir, ArrayList<String> toBeScanned) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                if (file.isDirectory()) {
                    traverse(file, toBeScanned);
                }
                else {
                    // do something here with the file
                    String path = file.getAbsolutePath();
                    SurespotLog.d(TAG, "adding file to scanner: %s", path);
                    //toBeScanned.add(path);
                    MediaScannerConnection.scanFile(this, new String[]{path}, null, new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            SurespotLog.d(TAG, "scan completed: %s", path);
                            //  callback.handleResponse(path, uri);
                        }
                    });
                }
            }
        }
        else {
            SurespotLog.d(TAG, "404: %s", dir);
        }

    }


    public void disableMessageMode(boolean showKeyboard) {
        if (mEtMessage == null) {
            return;
        }

        mEtMessage.setVisibility(View.VISIBLE);
        requestFocus(mEtMessage);

        View gifFrame = findViewById(R.id.gifFrame);
        gifFrame.setVisibility(GONE);
        mGiphySearchFieldLayout.setVisibility(GONE);

        mEtGifSearch.setText("");
        mSendButton.setVisibility(View.VISIBLE);
        mActivityLayout.findViewById(R.id.pager).setPadding(0, 0, 0, 0);


        if (mMessageModeView != null && mMessageModeView.getParent() != null) {
            WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
            wm.removeViewImmediate(mMessageModeView);
        }

        mCurrentMessageMode = null;
        if (showKeyboard) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {

                    if (!mEtMessage.hasFocus()) {

                        if (!mActivityLayout.isKeyboardVisible()) {
                            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            input.showSoftInput(mEtMessage, 0);
                        }
                    }
                }
            };
            mHandler.post(runnable);

        }
        updateMessageBar();
        expand();
    }

    private void sendGifMessage(String result) {
        if (mUser == null) {
            return;
        }

        final ChatController cc = ChatManager.getChatController(mUser);
        if (cc == null) {
            return;
        }

        final String currentChat = cc.getCurrentChat();
        if (currentChat == null) {
            return;
        }

        ChatUtils.sendGifMessage(mUser, currentChat, result);
    }

    @Override
    public void onEmojiconBackspaceClicked(View v) {
        EmojiconsView.backspace(mEtMessage);
    }

    @Override
    public void onEmojiconClicked(Emojicon emojicon) {
        EmojiconsView.input(mEtMessage, emojicon);
    }

//    void sendBackPressed() {
//        this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
//    }

    void requestFocus(View view) {

        if (!view.hasFocus()) {
         //   SurespotLog.d(TAG, "requestFocus, view does not have focus, requesting focus");
            view.requestFocus();
        }
        else {
       //     SurespotLog.d(TAG, "requestFocus, view has focus, not requesting focus");
        }
    }

    void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null && mEtMessage != null) {
            input.hideSoftInputFromWindow(mEtMessage.getWindowToken(), 0);
        }
    }
}
