package com.twofours.surespot.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.TextView.OnEditorActionListener;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.backup.ImportIdentityActivity;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.IOException;
import java.security.KeyPair;

import okhttp3.Call;
import okhttp3.Response;

public class SignupActivity extends Activity {
    private static final String TAG = "SignupActivity";
    private Button signupButton;
    private MultiProgressDialog mMpd;
    private MultiProgressDialog mMpdCheck;

    private View mUsernameValid;
    private View mUsernameInvalid;
    private Menu mMenuOverflow;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mSigningUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UIUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.create), false);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        TextView tvSignupHelp = (TextView) findViewById(R.id.tvSignupHelp);
        tvSignupHelp.setMovementMethod(LinkMovementMethod.getInstance());

        Spannable suggestion1 = setRestoreListener(getString(R.string.enter_username_and_password));
        Spannable suggestion2 = new SpannableString(getString(R.string.usernames_case_sensitive));
        suggestion2.setSpan(new ForegroundColorSpan(Color.RED), 0, suggestion2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        Spannable suggestion3 = new SpannableString(getString(R.string.aware_username_password));

        Spannable warning = new SpannableString(getString(R.string.warning_password_reset));

        warning.setSpan(new ForegroundColorSpan(Color.RED), 0, warning.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvSignupHelp.setText(TextUtils.concat(suggestion1, " ", suggestion2, " ", suggestion3, " ", warning), BufferType.SPANNABLE);

        mUsernameValid = findViewById(R.id.ivUsernameValid);
        mUsernameInvalid = findViewById(R.id.ivUsernameInvalid);

        mMpd = new MultiProgressDialog(this, getString(R.string.create_user_progress), 250);
        mMpdCheck = new MultiProgressDialog(this, getString(R.string.user_exists_progress), 500);

        EditText editText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter()});
        editText.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    checkUsername();
                }
            }
        });

        this.signupButton = (Button) this.findViewById(R.id.bSignup);
        this.signupButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                signup();
            }
        });

        final EditText pwText = (EditText) findViewById(R.id.etSignupPassword);
        pwText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_PASSWORD_LENGTH)});

        final EditText pwConfirmText = (EditText) findViewById(R.id.etSignupPasswordConfirm);
        pwConfirmText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConfiguration.MAX_PASSWORD_LENGTH)});
        pwConfirmText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    //
                    signup();
                    handled = true;
                }
                return handled;
            }
        });
    }

    private SpannableStringBuilder setRestoreListener(String str) {

        int idx1 = str.indexOf("[");
        int idx2 = str.indexOf("]");

        if (idx1 < idx2) {

            String preString = str.substring(0, idx1);
            String linkString = str.substring(idx1 + 1, idx2);
            String endString = str.substring(idx2 + 1, str.length());

            SpannableStringBuilder ssb = new SpannableStringBuilder(preString + linkString + endString);

            ssb.setSpan(new ClickableSpan() {

                @Override
                public void onClick(View widget) {
                    launchImport();
                }
            }, idx1, idx2 - 1, 0);

            return ssb;
        }

        return new SpannableStringBuilder(str);

    }

    private void checkUsername() {
        final EditText userText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
        final String username = userText.getText().toString();

        if (TextUtils.isEmpty(username)) {
            return;
        }

        mMpdCheck.incrProgress();

        // see if the user exists
        NetworkManager.getNetworkController(this).userExists(username, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.i(TAG, e, "userExists error");
                mMpdCheck.decrProgress();
                Utils.makeToast(SignupActivity.this, getString(R.string.user_exists_error));
                userText.requestFocus();
            }



            @Override
            public void onResponse(Call call, Response response, String responseString) {
                mMpdCheck.decrProgress();
                if (response.isSuccessful()) {
                    if (responseString.equals("true")) {
                        Utils.makeToast(SignupActivity.this, getString(R.string.username_exists));
                        setUsernameValidity(false);
                        userText.requestFocus();
                    }
                    else {
                        setUsernameValidity(true);
                        EditText pwText = (EditText) findViewById(R.id.etSignupPassword);
                        pwText.requestFocus();
                    }
                }
                else {
                    SurespotLog.i(TAG, "userExists");
                    switch (response.code()) {
                        case 429:
                            Utils.makeToast(SignupActivity.this, getString(R.string.user_exists_throttled));
                            break;
                        default:
                            Utils.makeToast(SignupActivity.this, getString(R.string.user_exists_error));
                    }

                    userText.requestFocus();
                }
            }
        }));
    }

    private void signup() {

        final EditText userText = (EditText) SignupActivity.this.findViewById(R.id.etSignupUsername);
        final String username = userText.getText().toString();

        final EditText pwText = (EditText) SignupActivity.this.findViewById(R.id.etSignupPassword);
        final String password = pwText.getText().toString();

        final EditText confirmPwText = (EditText) SignupActivity.this.findViewById(R.id.etSignupPasswordConfirm);
        String confirmPassword = confirmPwText.getText().toString();

        if (!(username.length() > 0 && password.length() > 0 && confirmPassword.length() > 0)) {
            return;
        }

        if (!confirmPassword.equals(password)) {
            Utils.makeToast(this, getString(R.string.passwords_do_not_match));
            pwText.setText("");
            confirmPwText.setText("");
            pwText.requestFocus();
            return;
        }

        //prevent multiple signup attempts
        if (!mSigningUp) {
            mSigningUp = true;
        }
        else {
            return;
        }
        mMpd.incrProgress();

        // make sure we can create the file
        if (!IdentityController.ensureIdentityFile(SignupActivity.this, username, false)) {
            Utils.makeToast(SignupActivity.this, getString(R.string.username_exists));
            userText.setText("");
            // confirmPwText.setText("");
            // pwText.setText("");
            userText.requestFocus();
            mMpd.decrProgress();
            mSigningUp = false;
            setUsernameValidity(false);
            return;
        }

        byte[][] derived = EncryptionController.derive(password);
        final String salt = new String(ChatUtils.base64EncodeNowrap(derived[0]));
        final String dPassword = new String(ChatUtils.base64EncodeNowrap(derived[1]));
        // generate key pair
        // TODO don't always regenerate if the signup was not
        // successful
        EncryptionController.generateKeyPairs(new IAsyncCallback<KeyPair[]>() {

            @Override
            public void handleResponse(final KeyPair[] keyPair) {
                if (keyPair != null) {
                    new AsyncTask<Void, Void, String[]>() {
                        protected String[] doInBackground(Void... params) {

                            String[] data = new String[4];
                            data[0] = EncryptionController.encodePublicKey(keyPair[0].getPublic());
                            data[1] = EncryptionController.encodePublicKey(keyPair[1].getPublic());

                            //sign the username and password for authentication
                            data[2] = EncryptionController.sign(keyPair[1].getPrivate(), username, dPassword);
                            // sign the public keys, username, and version so clients can validate
                            String dh = new String(ChatUtils.base64EncodeNowrap(keyPair[0].getPublic().getEncoded()));
                            String dsa =  new String(ChatUtils.base64EncodeNowrap(keyPair[1].getPublic().getEncoded()));
                            data[3] = EncryptionController.sign(keyPair[1].getPrivate(), username, 1, dh, dsa);
                            return data;
                        }

                        protected void onPostExecute(String[] result) {
                            String sPublicDH = result[0];
                            String sPublicECDSA = result[1];
                            String authSig = result[2];
                            String clientSig = result[3];

                            String referrers = Utils.getSharedPrefsString(SignupActivity.this, SurespotConstants.PrefNames.REFERRERS);

                            NetworkManager.getNetworkController(SignupActivity.this, username).createUser3(username, dPassword, sPublicDH, sPublicECDSA, authSig, clientSig, referrers, new CookieResponseHandler() {


                                @Override
                                public void onSuccess(int responseCode, String result, final okhttp3.Cookie cookie) {


                                    confirmPwText.setText("");
                                    pwText.setText("");


                                    if (responseCode == 201) {
                                        // save key pair now
                                        // that we've created
                                        // a
                                        // user successfully
                                        new AsyncTask<Void, Void, Void>() {

                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                Utils.putSharedPrefsString(SignupActivity.this, SurespotConstants.PrefNames.REFERRERS, null);
                                                IdentityController
                                                        .createIdentity(SignupActivity.this, username, password, salt, keyPair[0], keyPair[1], cookie);
                                                return null;
                                            }

                                            protected void onPostExecute(Void result) {

                                                // SurespotApplication.getUserData().setUsername(username);
                                                Intent newIntent = new Intent(SignupActivity.this, MainActivity.class);
                                                Intent intent = getIntent();
                                                newIntent.setAction(intent.getAction());
                                                newIntent.setType(intent.getType());
                                                Bundle extras = intent.getExtras();
                                                if (extras != null) {
                                                    newIntent.putExtras(extras);
                                                }
                                                // set a flag showing we just created a user
                                                newIntent.putExtra("userWasCreated", true);
                                                newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                startActivity(newIntent);
                                                Utils.clearIntent(intent);
                                                mMpd.decrProgress();
                                                mSigningUp = false;
                                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                                imm.hideSoftInputFromWindow(pwText.getWindowToken(), 0);

                                                finish();
                                                setUsernameValidity(true);
                                            }

                                        }.execute();
                                    }
                                    else {
                                        SurespotLog.w(TAG, "201 not returned on user create.");
                                        // confirmPwText.setText("");
                                        // pwText.setText("");
                                        mMpd.decrProgress();
                                        mSigningUp = false;
                                        pwText.requestFocus();
                                        // setUsernameValidity(false);
                                    }
                                }

                                @Override
                                public void onFailure(Throwable arg0, int code, String content) {
                                    SurespotLog.i(TAG,  "signup error %s", content);
                                    mMpd.decrProgress();
                                    mSigningUp = false;

                                    switch (code) {
                                        case 403:
                                            Utils.makeToast(SignupActivity.this, getString(R.string.signup_update));
                                            break;
                                        default:
                                            Utils.makeToast(SignupActivity.this, getString(R.string.could_not_create_user));
                                    }
                                }
                            });
                        }
                    }.execute();

                }
                else {
                    mMpd.decrProgress();
                    mSigningUp = false;
                    Utils.makeToast(SignupActivity.this, getString(R.string.could_not_create_user));
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_signup, menu);

        mMenuOverflow = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_import_identities:
                launchImport();
                return true;
            case R.id.menu_about:
                Intent abIntent = new Intent(this, AboutActivity.class);
                abIntent.putExtra("signup", true);
                startActivity(abIntent);
                return true;
            default:
                return false;
        }
    }


    private void launchImport() {
        Intent intent = new Intent(this, ImportIdentityActivity.class);
        intent.putExtra("signup", true);
        startActivity(intent);
    }

    private void setUsernameValidity(boolean isValid) {
        mUsernameValid.setVisibility(isValid ? View.VISIBLE : View.GONE);
        mUsernameInvalid.setVisibility(isValid ? View.GONE : View.VISIBLE);
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
}
