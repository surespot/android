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
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
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
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SoftKeyboardLayout;
import com.twofours.surespot.chat.SurespotDrawerLayout;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageCaptureHandler;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.CredentialCachingBinder;
import com.twofours.surespot.services.RegistrationIntentService;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.utils.FileUtils;
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

import static com.twofours.surespot.SurespotConstants.ExtraNames.MESSAGE_TO;

public class MainActivity extends Activity implements EmojiconsView.OnEmojiconBackspaceClickedListener, OnEmojiconClickedListener {
    public static final String TAG = "MainActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private Context mContext = null;
    private ArrayList<MenuItem> mMenuItems = new ArrayList<MenuItem>();
    private IAsyncCallback<Object> m401Handler;

    private boolean mCacheServiceBound;
    private Menu mMenuOverflow;
    private BroadcastReceiver mExternalStorageReceiver;
    private boolean mExternalStorageAvailable = false;
    private boolean mExternalStorageWriteable = false;
    private ImageView mHomeImageView;

    private SoftKeyboardLayout mActivityLayout;
    private EditText mEtMessage;
    private EditText mEtInvite;
    private View mSendButton;
    private EmojiconsView mEmojiView;
    private ImageView mEmojiButton;
    private boolean mEmojiShowing;
    private ImageView mQRButton;
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
            } else {
                SurespotLog.d(TAG, "I was deleted and there are different users so starting login activity.");
                Intent newIntent = new Intent(MainActivity.this, LoginActivity.class);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(newIntent);
                finish();
            }
        } else {
            if (!needsSignup()) {
                processLaunch();
            } else {
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

        getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);


        mContext = this;

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
        } else {
            if (!mSigningUp) {
                mSigningUp = intent.getBooleanExtra("signingUp", false);

                if (!mSigningUp) {
                    if (mCacheServiceBound) {
                        processLaunch();
                    } else {
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
        SurespotLog.d(TAG, "processLaunch, launchUser: %s, mUser: %s", user, mUser);
        if (user == null) {
            launchLogin();
        } else {
            mUser = user;

            if (!mCacheServiceBound) {
                bindCacheService();
            }

            if (mCacheServiceBound) {
                SurespotLog.d(TAG, "cache service already bound");
                if (!mUnlocking) {
                    SurespotLog.d(TAG, "processLaunch calling postServiceProcess");
                    postServiceProcess();
                } else {
                    SurespotLog.d(TAG, "unlock activity launched, not post service processing until resume");
                }
            }

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
                    } else {
                        intent.removeExtra("autoinviteurl");
                    }

                    try {
                        AutoInviteData aid = new AutoInviteData();
                        aid.setUsername(segments.get(1));
                        aid.setSource(segments.get(2));
                        return aid;
                    } catch (IndexOutOfBoundsException e) {
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

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatController cc = ChatManager.getChatController(mUser);
                if (cc != null) {

                    Friend friend = mCurrentFriend;
                    if (friend != null) {

                        if (mEtMessage.getText().toString().length() > 0 && !cc.isFriendDeleted(friend.getName())) {
                            sendMessage(friend.getName());
                        } else {
                            // go to home
                            cc.setCurrentChat(null);
                        }
                    } else {
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
                        } else {
                            if (mEtMessage.getText().toString().length() > 0) {
                                sendMessage(friend.getName());
                            } else {
                                SharedPreferences sp = MainActivity.this.getSharedPreferences(mUser, Context.MODE_PRIVATE);
                                boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);
                                if (!disableVoice) {
                                    VoiceController.startRecording(MainActivity.this, mUser, friend.getName());
                                } else {
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

        mEmojiButton = (ImageView) mainView.findViewById(R.id.bEmoji);
        mEmojiButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                toggleEmojiDrawer();
            }
        });

        setEmojiIcon(true);

        mQRButton = (ImageView) mainView.findViewById(R.id.bQR);
        mQRButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDialog = UIUtils.showQRDialog(MainActivity.this, mUser);
            }
        });

        mEtMessage = (EditText) mainView.findViewById(R.id.etMessage);
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
                if (mEmojiShowing) {
                    return true;
                }

                return false;
            }
        };

        mEtMessage.setOnTouchListener(editTouchListener);

        mEtInvite = (EditText) mainView.findViewById(R.id.etInvite);
        mEtInvite.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter()});
        //mEtInvite.setOnTouchListener(editTouchListener);

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

    private void switchUser(String identityName) {
        SurespotLog.d(TAG, "switchUser, mUser: %s, identityName: %s", mUser, identityName);
        if (!identityName.equals(mUser)) {
            ChatManager.pause(mUser);
            ChatManager.detach(this);
            mUser = identityName;

            if (!SurespotApplication.getCachingService().setSession(this, mUser)) {
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

        } else if (!TextUtils.isEmpty(messageTo)
                && (SurespotConstants.IntentFilters.MESSAGE_RECEIVED.equals(notificationType)
                || SurespotConstants.IntentFilters.INVITE_REQUEST.equals(notificationType) || SurespotConstants.IntentFilters.INVITE_RESPONSE
                .equals(notificationType))) {

            user = messageTo;
            Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.LAST_USER, user);
        } else {
            user = IdentityController.getLastLoggedInUser(this);
        }

        SurespotLog.d(TAG, "got launch user: %s", user);
        return user;
    }


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            SurespotLog.d(TAG, "caching service bound");
            CredentialCachingBinder binder = (CredentialCachingBinder) service;
            CredentialCachingService ccs = binder.getService();

            SurespotApplication.setCachingService(ccs);
            mCacheServiceBound = true;

            if (!mUnlocking) {
                SurespotLog.d(TAG, "caching service calling postServiceProcess");
                postServiceProcess();
            } else {
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
            startService(intent);
        }

        setupBilling();

        // set volume control buttons
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mHomeImageView = (ImageView) findViewById(android.R.id.home);
        setHomeProgress(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //drawer
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setScrimColor(Color.argb(224, 0, 0, 0));
        View header = getLayoutInflater().inflate(R.layout.drawer_header, mDrawerList, false);
        mDrawerList.addHeaderView(header, null, false);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                //    SurespotLog.d(TAG, "slideOffset: %f", slideOffset);
                if (slideOffset > 0.5) {
//                    if (mActivityLayout.isKeyboardVisible()) {
//                        sendBackPressed();
//                    }
//                    else {
                    if (isEmojiVisible()) {
                        hideEmojiDrawer(false);
                    }
                    //   }
                    //hideSoftKeyboard();
                }
            }
        });

        updateDrawer();
    }

    private void updateDrawer() {

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
                //  SurespotLog.d(TAG, "OnKeyboardShown: visible %b", visible);
                if (!visible && mActivityLayout.getPaddingBottom() == 0 && isEmojiVisible()) {
                    hideEmojiDrawer(false);
                }
            }
        });

        TitlePageIndicator titlePageIndicator = (TitlePageIndicator) mainView.findViewById(R.id.indicator);
        ChatManager.attachChatController(
                this,
                mUser,
                (ViewPager) mainView.findViewById(R.id.pager),
                getFragmentManager(),
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
            } else {
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
            } else {
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

        SurespotLog.d(TAG, "onResume, mUnlocking: %b, mLaunched: %b, mResumed: %b, mPaused: %b", mUnlocking, mLaunched, mResumed, mPaused);
        startWatchingExternalStorage();
        SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
        mEnterToSend = sp.getBoolean("pref_enter_to_send", true);

        // if we had to unlock and we're resuming for a 2nd time and we have the caching service
        if (mUnlocking && mPaused) {
            SurespotLog.d(TAG, "setting mUnlocking to false");
            mUnlocking = false;

            if (SurespotApplication.getCachingService() != null) {
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
        ChatManager.resume(mUser);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SurespotLog.d(TAG, "onPause");

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

        ChatManager.pause(mUser);


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
                                } catch (URISyntaxException e) {
                                }

                                ChatController cc = ChatManager.getChatController(mUser);

                                if (cc == null || url == null) {
                                    Utils.makeToast(MainActivity.this, getString(R.string.could_not_upload_friend_image));
                                } else {
                                    if (cc != null) {
                                        cc.setImageUrl(to, url, version, iv, true);
                                    }
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
                } else {
                    // TODO upload token to server
                    SurespotLog.d(TAG, "onActivityResult handled by IABUtil.");
                }
                break;
            case SurespotConstants.IntentRequestCodes.REQUEST_SETTINGS:
                if (SurespotApplication.getThemeChanged()) {
                    destroy();
                    finish();
                    final Intent intent = getIntent();
                    intent.putExtra("themeChanged", true);
                    startActivity(intent);
                } else {
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

        mMenuItems.add(menu.findItem(R.id.menu_close_bar));
        mMenuItems.add(menu.findItem(R.id.menu_send_image_bar));

        MenuItem captureItem = menu.findItem(R.id.menu_capture_image_bar);
        if (hasCamera()) {
            mMenuItems.add(captureItem);
            captureItem.setEnabled(FileUtils.isExternalStorageMounted());
        } else {
            SurespotLog.d(TAG, "hiding capture image menu option");
            menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
        }

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
                    } else {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }
                } else {
                    cc.setCurrentChat(null);
                }
                return true;
            case R.id.menu_close_bar:

                cc.closeTab();
                return true;
            case R.id.menu_send_image_bar:
                if (currentChat == null || mCurrentFriend == null) {
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
                        intent.putExtra("toAlias", mCurrentFriend.getNameOrAlias());
                        intent.putExtra("from", mUser);
                        intent.putExtra("size", ImageSelectActivity.IMAGE_SIZE_LARGE);
                        // set start intent to avoid restarting every rotation
                        intent.putExtra("start", true);
                        intent.putExtra("friendImage", false);
                        startActivity(intent);
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
                } else {
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
                } else {
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
//            case R.id.menu_purchase_voice:
//                showVoicePurchaseDialog(false);
//                return true;
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
        SurespotLog.d(TAG, "onDestroy, themeChanged: %b", SurespotApplication.getThemeChanged());

        if (!SurespotApplication.getThemeChanged()) {
            ChatManager.pause(mUser);
            destroy();
        } else {
            SurespotApplication.setThemeChanged(null);
        }

    }

    private void destroy() {
        SurespotLog.d(TAG, "destroy unbinding");

        if (mCacheServiceBound && mConnection != null) {
            unbindService(mConnection);
        }

        ChatManager.detach(this);
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
        } catch (java.lang.IllegalArgumentException e) {
        }
    }

    private void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        SurespotLog.d(TAG, "updateExternalStorageState:  " + state);
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

    private void setHomeProgress(boolean inProgress) {
        if (mHomeImageView == null) {
            return;
        }

        SurespotLog.d(TAG, "progress status changed to: %b", inProgress);
        if (inProgress) {
            UIUtils.showProgressAnimation(this, mHomeImageView);
        } else {
            mHomeImageView.clearAnimation();
        }

        ChatController cc = ChatManager.getChatController(mUser);
        if (cc != null) {
            cc.enableMenuItems(mCurrentFriend);
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
                ChatController cc = ChatManager.getChatController(mUser);

                if (cc != null && mEtMessage.getText().toString().length() > 0 && !cc.isFriendDeleted(mCurrentFriend.getName())) {
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

            } else {
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
                    } else {
                        //TODO
                    }
                }
            }
        } else {
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


    private void sendMessage(String username) {
        final String message = mEtMessage.getText().toString();
        if (!message.isEmpty()) {
            ChatController cc = ChatManager.getChatController(mUser);
            if (cc != null) {
                cc.sendMessage(username, message, SurespotConstants.MimeTypes.TEXT);
                TextKeyListener.clear(mEtMessage.getText());
            }
        }
    }

    public boolean backButtonPressed() {

        SurespotLog.d(TAG, "backButtonPressed");

        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        //returning false will cause the keyboard to be hidden
        if (mActivityLayout.isKeyboardVisible()) {
            return false;
        }

        if (isEmojiVisible()) {
            hideEmojiDrawer(false);
            return true;
        }


        //go to home page if we not
        if (mCurrentFriend != null) {
            ChatController cc = ChatManager.getChatController(mUser);
            if (cc != null) {
                cc.setCurrentChat(null);
                return true;
            }
        }

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
                            } else {
                                Utils.makeToast(MainActivity.this, getString(R.string.has_accepted, friend));
                            }
                        } else {
                            Utils.makeToast(MainActivity.this, getString(R.string.could_not_invite));
                        }
                    } else {
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
            mIvVoice.setVisibility(View.GONE);
            mIvHome.setVisibility(View.GONE);
            mIvSend.setVisibility(View.GONE);
        } else {
            if (mCurrentFriend.isDeleted()) {
                mIvInvite.setVisibility(View.GONE);
                mIvVoice.setVisibility(View.GONE);
                mIvHome.setVisibility(View.VISIBLE);
                mIvSend.setVisibility(View.GONE);
            } else {
                if (mEtMessage.getText().length() > 0) {
                    mIvInvite.setVisibility(View.GONE);
                    mIvVoice.setVisibility(View.GONE);
                    mIvHome.setVisibility(View.GONE);
                    mIvSend.setVisibility(View.VISIBLE);
                } else {
                    mIvInvite.setVisibility(View.GONE);
                    SharedPreferences sp = getSharedPreferences(mUser, Context.MODE_PRIVATE);
                    boolean disableVoice = sp.getBoolean(SurespotConstants.PrefNames.VOICE_DISABLED, false);

                    if (disableVoice) {
                        mIvVoice.setVisibility(View.GONE);
                        mIvHome.setVisibility(View.VISIBLE);
                    } else {
                        mIvVoice.setVisibility(View.VISIBLE);
                        mIvHome.setVisibility(View.GONE);
                    }

                    mIvSend.setVisibility(View.GONE);
                }
            }

        }
    }

    private void handleTabChange(Friend friend) {
        SurespotLog
                .v(TAG,
                        "handleTabChange, mFriendHasBeenSet: %b, currentFriend is null: %b",
                        mFriendHasBeenSet, mCurrentFriend == null);

        if (friend == null) {
            mEmojiButton.setVisibility(View.GONE);
            mEtMessage.setVisibility(View.GONE);
            mEtInvite.setVisibility(View.VISIBLE);
            //  mEmojiView.setVisibility(View.GONE);

            mQRButton.setVisibility(View.VISIBLE);
            mEtInvite.requestFocus();


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_drawer);
            } else {

                ViewGroup home = (ViewGroup) findViewById(android.R.id.home).getParent();
                // get the first child (up imageview)
                ((ImageView) home.getChildAt(0))
                        // change the icon according to your needs
                        .setImageResource(R.drawable.ic_drawer);
            }


        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                getActionBar().setHomeAsUpIndicator(R.drawable.ic_ab_back_holo_dark_am);
            } else {
                ViewGroup home = (ViewGroup) findViewById(android.R.id.home).getParent();
                // get the first child (up imageview)
                ((ImageView) home.getChildAt(0))
                        // change the icon according to your needs
                        .setImageResource(R.drawable.ic_ab_back_holo_dark_am);
            }

            if (friend.isDeleted()) {

                mEmojiButton.setVisibility(View.GONE);
                mEtMessage.setVisibility(View.GONE);


            } else {
                mEtMessage.setVisibility(View.VISIBLE);
                mEmojiButton.setVisibility(View.VISIBLE);
            }

            mEtInvite.setVisibility(View.GONE);
            mQRButton.setVisibility(View.GONE);
            mEtMessage.requestFocus();
        }

        // if keyboard is showing and we want to show emoji or vice versa, just toggle emoji
        mCurrentFriend = friend;

        if (friend == null) {
//            if (mActivityLayout.isKeyboardVisible()) {
//                sendBackPressed();
//            }
//            else {
            if (isEmojiVisible()) {
                hideEmojiDrawer(false);
            }
            //  }
        }

        setButtonText();
        mFriendHasBeenSet = true;
    }

    private void setEmojiIcon(final boolean keyboardShowing) {
        boolean black = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.BLACK);
        if (keyboardShowing) {
            mEmojiButton.setImageResource(R.drawable.smiley);
        } else {
            if (black) {
                mEmojiButton.setImageResource(R.drawable.ic_action_keyboard_grey);
            } else {
                mEmojiButton.setImageResource(R.drawable.ic_action_keyboard);
            }
        }
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
        } else {
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
                } else {
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
        } else {
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
        } else {
            Utils.makeToast(MainActivity.this, getString(R.string.could_not_remove_friend_alias));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void bindCacheService() {
        SurespotLog.d(TAG, "binding cache service");
        Intent cacheIntent = new Intent(this, CredentialCachingService.class);
        startService(cacheIntent);
        bindService(cacheIntent, mConnection, Context.BIND_AUTO_CREATE);
    }


    public boolean isEmojiVisible() {
        return mEmojiShowing;
    }

    private void toggleEmojiDrawer() {
        // TODO animate drawer enter & exit

        if (isEmojiVisible()) {
            hideEmojiDrawer();
        } else {
            showEmojiDrawer();
        }
    }

    private void showEmojiDrawer() {
        int keyboardHeight = mActivityLayout.getKeyboardHeight();

        SurespotLog.d(TAG, "showEmojiDrawer height: %d", keyboardHeight);
        mEmojiShowing = true;

        if (mEmojiView == null) {
            mEmojiView = (EmojiconsView) LayoutInflater
                    .from(this).inflate(R.layout.emojicons, null, false);


            //   mEmojiView.setId(R.id.emoji_drawer);
            mEmojiView.setOnEmojiconBackspaceClickedListener(this);
            mEmojiView.setOnEmojiconClickedListener(this);


            mWindowLayoutParams = new WindowManager.LayoutParams();
            mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            mWindowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            mWindowLayoutParams.token = ((Activity) mContext).getWindow().getDecorView().getWindowToken();
            mWindowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }

        mWindowLayoutParams.height = keyboardHeight;
        mWindowLayoutParams.width = UIUtils.getDisplaySize(this).x;

        WindowManager wm = (WindowManager) mContext.getSystemService(Activity.WINDOW_SERVICE);

        try {
            if (mEmojiView.getParent() != null) {
                wm.removeViewImmediate(mEmojiView);
            }
        } catch (Exception e) {
            SurespotLog.e(TAG, e, "error removing emoji view");
        }

        try {
            wm.addView(mEmojiView, mWindowLayoutParams);
        } catch (Exception e) {
            SurespotLog.e(TAG, e, "error adding emoji view");
            return;
        }


        if (!mActivityLayout.isKeyboardVisible()) {
            SurespotLog.d(TAG, "setting padding");
            mActivityLayout.setPadding(0, 0, 0, keyboardHeight);
            // TODO mEmojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
        }

        setEmojiIcon(false);
    }

    private void hideEmojiDrawer() {
        hideEmojiDrawer(true);
    }

    public void hideEmojiDrawer(boolean showKeyboard) {
        if (showKeyboard) {
            InputMethodManager input = (InputMethodManager) mContext
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            input.showSoftInput(mEtMessage, 0);
        }

        if (mEmojiView != null && mEmojiView.getParent() != null) {
            WindowManager wm = (WindowManager) mContext
                    .getSystemService(Context.WINDOW_SERVICE);
            wm.removeViewImmediate(mEmojiView);
        }

        mEmojiButton.setImageResource(R.drawable.smiley);
        mActivityLayout.setPadding(0, 0, 0, 0);
        mEmojiShowing = false;
    }

    @Override
    public void onEmojiconBackspaceClicked(View v) {
        EmojiconsView.backspace(mEtMessage);
    }

    @Override
    public void onEmojiconClicked(Emojicon emojicon) {
        EmojiconsView.input(mEtMessage, emojicon);
    }

    void sendBackPressed() {
        this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
    }
}
