package com.twofours.surespot.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.backup.ExportIdentityActivity;
import com.twofours.surespot.backup.ImportIdentityActivity;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.identity.RemoveIdentityFromDeviceActivity;
import com.twofours.surespot.identity.SurespotIdentity;
import com.twofours.surespot.identity.SurespotKeystoreActivity;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.security.InvalidKeyException;
import java.util.List;

public class LoginActivity extends Activity {

    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;
    private Button loginButton;
    private static final String TAG = "LoginActivity";
    MultiProgressDialog mMpd;
    private List<String> mIdentityNames;
    private Menu mMenuOverflow;
    private EditText mEtPassword;
    private CheckBox mCbSavePassword;
    private boolean mKeystoreNeededUnlocking;
    private String mUnlockingUser;
    private boolean mUserChallenge;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UIUtils.setTheme(this);
        super.onCreate(savedInstanceState);

        SurespotLog.d(TAG, "onCreate");

        boolean keystoreEnabled = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED);
        if (keystoreEnabled) {
            IdentityController.initKeystore();
        }

        setContentView(R.layout.activity_login);
        Utils.configureActionBar(this, "", getString(R.string.surespot), false);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        Utils.logIntent(TAG, getIntent());



        mMpd = new MultiProgressDialog(this, getString(R.string.login_progress), 750);

        this.loginButton = (Button) this.findViewById(R.id.bLogin);
        this.loginButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                login();

            }
        });

        mEtPassword = (EditText) findViewById(R.id.etPassword);
        mEtPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_PASSWORD_LENGTH)});
        mEtPassword.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    //
                    login();
                    handled = true;
                }
                return handled;
            }

        });

        mCbSavePassword = (CheckBox) findViewById(R.id.cbSavePassword);
        mCbSavePassword.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                // make sure keystore inited
                if (mCbSavePassword.isChecked()) {
                    SurespotLog.d(TAG, "initing keystore");
                    mUnlockingUser = getSelectedUsername();
                    try {
                        mKeystoreNeededUnlocking = !IdentityController.storePasswordForIdentity(LoginActivity.this, mUnlockingUser, mEtPassword.getText().toString());
                    }
                    catch (InvalidKeyException e) {
                        LaunchKeystoreActivity();
                    }
                }
                else {
                    IdentityController.clearStoredPasswordForIdentity(LoginActivity.this, getSelectedUsername());
                }

            }
        });
    }

    private void LaunchKeystoreActivity() {
        if (mUserChallenge == false) {
            mUserChallenge = true;
            Intent intent = new Intent(LoginActivity.this, SurespotKeystoreActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            LoginActivity.this.startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            // Challenge completed, proceed with using cipher
            if (resultCode == RESULT_OK) {
                Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, true);
            }
            else {
                // The user canceled or didn’t complete the lock screen operation. Go to error/cancellation flow.
                Utils.makeLongToast(this, this.getString(R.string.keystore_not_unlocked));
                Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
                finish();
            }

            mUserChallenge = false;
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        // set the identities

        Spinner spinner = (Spinner) findViewById(R.id.spinnerUsername);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mIdentityNames = IdentityController.getIdentityNames(this);

        if (mIdentityNames == null || mIdentityNames.size() == 0) {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        for (String name : mIdentityNames) {
            adapter.add(name);

        }
        spinner.setAdapter(adapter);

        // select last user if there was one
        String to = getIntent().getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
        if (to == null) {
            to = IdentityController.getLastLoggedInUser(this);
        }

        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePassword();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        if (to != null && mIdentityNames.contains(to)) {
            spinner.setSelection(adapter.getPosition(to));
        }
    }



    private class IdSig {
        public SurespotIdentity identity;
        public String signature;
        protected String derivedPassword;
    }

    private void login() {


        final String username = getSelectedUsername();
        final EditText pwText = (EditText) LoginActivity.this.findViewById(R.id.etPassword);

        final String password = pwText.getText().toString();

        if (username != null && username.length() > 0 && password != null && password.length() > 0) {
            mMpd.incrProgress();

            new AsyncTask<Void, Void, IdSig>() {

                @Override
                protected IdSig doInBackground(Void... params) {

                    SurespotIdentity identity = IdentityController.getIdentity(LoginActivity.this, username, password);
                    if (identity != null) {
                        byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
                        final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));
                        IdSig idSig = new IdSig();
                        idSig.identity = identity;
                        idSig.signature = EncryptionController.sign(identity.getKeyPairDSA().getPrivate(), username, dPassword);
                        idSig.derivedPassword = dPassword;
                        return idSig;
                    }
                    return null;
                }

                protected void onPostExecute(final IdSig idSig) {
                    if (idSig != null) {

                        NetworkController networkController = NetworkManager.getNetworkController(LoginActivity.this, username);
                        networkController.set401Handler(new IAsyncCallback<Object>() {
                            @Override
                            public void handleResponse(Object unused) {

                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        //   mMpd.decrProgress();
                                        Utils.makeToast(LoginActivity.this, getString(R.string.login_check_password));
                                    }
                                };
                                LoginActivity.this.runOnUiThread(runnable);
                            }
                        });

                        networkController.login(username, idSig.derivedPassword, idSig.signature, new CookieResponseHandler() {
                            @Override
                            public void onSuccess(int responseCode, String result, okhttp3.Cookie cookie) {
                                mMpd.decrProgress();
                                IdentityController.userLoggedIn(LoginActivity.this, idSig.identity, cookie, password);
                                boolean enableKeystore = Utils.getSharedPrefsBoolean(LoginActivity.this, SurespotConstants.PrefNames.KEYSTORE_ENABLED);

                                if (enableKeystore) {

                                    // if we're saving the password in the key store then do it
                                    boolean keysaveChecked = mCbSavePassword.isChecked();

                                    if (keysaveChecked) {
                                        try {
                                            IdentityController.storePasswordForIdentity(LoginActivity.this, username, password);
                                        }
                                        catch (InvalidKeyException e) {
                                            LaunchKeystoreActivity();
                                        }
                                    }

                                }

                                Intent intent = getIntent();
                                Intent newIntent = new Intent(LoginActivity.this, MainActivity.class);
                                newIntent.setAction(intent.getAction());
                                newIntent.setType(intent.getType());
                                Bundle extras = intent.getExtras();
                                if (extras != null) {
                                    newIntent.putExtras(extras);
                                }

                                // if we logged in as someone else, remove the notification intent extras as we are no longer special
                                // we are just an ordinary login now with no magical powers
                                String notificationType = intent.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);
                                if (notificationType != null) {
                                    String messageTo = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
                                    if (!messageTo.equals(username)) {
                                        SurespotLog.v(TAG,
                                                "user has elected to login as a different user than the notification, removing relevant intent extras");
                                        newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_TO);
                                        newIntent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
                                        newIntent.removeExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE);

                                        Utils.putUserSharedPrefsString(LoginActivity.this, username, SurespotConstants.PrefNames.LAST_CHAT, null);
                                    }
                                }

                                Utils.logIntent(TAG, newIntent);
                                Utils.clearIntent(intent);

                                startActivity(newIntent);
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(pwText.getWindowToken(), 0);

                                finish();

                            }


                            @Override
                            public void onFailure(Throwable arg0, int code, String message) {
                                mMpd.decrProgress();
                                SurespotLog.i(TAG, arg0, message);

                                switch (code) {
                                    case 401:
                                        //     Utils.makeToast(LoginActivity.this, getString(R.string.login_check_password));
                                        break;
                                    case 403:
                                        Utils.makeToast(LoginActivity.this, getString(R.string.login_update));
                                        break;
                                    default:
                                        Utils.makeToast(LoginActivity.this, getString(R.string.login_try_again_later));
                                }

                                pwText.setText("");
                            }

                        });
                    }
                    else {
                        mMpd.decrProgress();
                        Utils.makeToast(LoginActivity.this, getString(R.string.login_check_password));
                        pwText.setText("");
                    }

                }

                ;
            }.execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_login, menu);

        mMenuOverflow = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_import_identities_bar:
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        Intent intent = new Intent(LoginActivity.this, ImportIdentityActivity.class);
                        startActivity(intent);
                        return null;
                    }
                }.execute();
                return true;
            case R.id.menu_backup_identities_bar:
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        Intent intent = new Intent(LoginActivity.this, ExportIdentityActivity.class);
                        intent.putExtra("backupUsername", getSelectedUsername());
                        startActivity(intent);
                        return null;
                    }
                }.execute();
                return true;
            case R.id.menu_remove_identity_bar:
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        Intent intent = new Intent(LoginActivity.this, RemoveIdentityFromDeviceActivity.class);
                        intent.putExtra("selectedUsername", getSelectedUsername());
                        startActivity(intent);
                        return null;
                    }
                }.execute();
                return true;
            case R.id.menu_create_identity_bar:
                if (IdentityController.getIdentityCount(this) < SurespotConstants.MAX_IDENTITIES) {
                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... params) {
                            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                            startActivity(intent);
                            return null;

                        }

                    }.execute();
                }
                else {
                    Utils.makeLongToast(this, getString(R.string.login_max_identities_reached, SurespotConstants.MAX_IDENTITIES));
                }
                return true;

            case R.id.clear_local_cache_bar:
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        StateController.clearCache(LoginActivity.this, new IAsyncCallback<Void>() {

                            @Override
                            public void handleResponse(Void result) {
                                LoginActivity.this.runOnUiThread(new Runnable() {
                                    public void run() {
                                        Utils.makeToast(LoginActivity.this, getString(R.string.local_cache_cleared));
                                    }

                                    ;
                                });

                            }
                        });
                        return null;
                    }
                }.execute();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

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


    private void updatePassword() {
        String username = getSelectedUsername();

        boolean enableKeystore = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED);
        SurespotLog.d(TAG, "updatePassword, username: %s, keystore enabled: %b", username, enableKeystore);
        String password = null;
        if (enableKeystore) {
            password = IdentityController.getKeyStorePasswordForIdentity(LoginActivity.this, username);

            if (password != null) {
                mEtPassword.setText(password);
                mEtPassword.setSelection(password.length());
                mCbSavePassword.setChecked(true);
            }
            else {
                // if we needed to unlock the keystore don't change the password and check status, and store password in keychain now
                if (mKeystoreNeededUnlocking && username.equals(mUnlockingUser)) {

                    boolean unlocked = IdentityController.isKeystoreUnlocked(this, username);

                    if (unlocked) {
                        SurespotLog.d(TAG, "keystore now unlocked, saving password for %s", username);
                        password = mEtPassword.getText().toString();
                        if (!TextUtils.isEmpty(password)) {
                            try {
                                IdentityController.storePasswordForIdentity(this, username, password);
                            }
                            catch (InvalidKeyException e) {
                                LaunchKeystoreActivity();
                            }
                        }
                    }
                    //if it's not unlocked uncheck save password
                    else {
                        mCbSavePassword.setChecked(false);
                        Utils.putSharedPrefsBoolean(this, SurespotConstants.PrefNames.KEYSTORE_ENABLED, false);
                    }

                    mKeystoreNeededUnlocking = false;
                    mUnlockingUser = null;
                }
                else {
                    mEtPassword.setText(null);
                    mCbSavePassword.setChecked(false);
                }

            }
        }
        else {
            mCbSavePassword.setChecked(false);
        }
    }

    private String getSelectedUsername() {
        return mIdentityNames.get(((Spinner) LoginActivity.this.findViewById(R.id.spinnerUsername)).getSelectedItemPosition());
    }
}
