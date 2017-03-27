package com.twofours.surespot.identity;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.twofours.surespot.R;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.utils.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.utils.UIUtils;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

public class DeleteIdentityActivity extends Activity {
    private static final String TAG = null;
    private List<String> mIdentityNames;
    private Spinner mSpinner;
    private MultiProgressDialog mMpd;
    private AlertDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UIUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_identity);
        Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.delete), true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        mMpd = new MultiProgressDialog(this, getString(R.string.delete_identity_progress), 250);

        Button deleteIdentityButton = (Button) findViewById(R.id.bDeleteIdentity);
        mSpinner = (Spinner) findViewById(R.id.identitySpinner);
        refreshSpinner();

        deleteIdentityButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final String user = (String) mSpinner.getSelectedItem();
                mDialog = UIUtils.passwordDialog(DeleteIdentityActivity.this, getString(R.string.delete_identity_user, user),
                        getString(R.string.enter_password_for, user), new IAsyncCallback<String>() {
                            @Override
                            public void handleResponse(String result) {
                                if (!TextUtils.isEmpty(result)) {
                                    deleteIdentity(user, result);
                                }
                                else {
                                    Utils.makeToast(DeleteIdentityActivity.this, getString(R.string.no_identity_deleted));
                                }
                            }
                        });

            }
        });
    }

    private void refreshSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mIdentityNames = IdentityController.getIdentityNames(this);

        for (String name : mIdentityNames) {
            adapter.add(name);
        }

        mSpinner.setAdapter(adapter);
        String loggedInUser = IdentityController.getLoggedInUser();
        if (loggedInUser != null) {
            mSpinner.setSelection(adapter.getPosition(loggedInUser));
        }

    }

    private void deleteIdentity(final String username, final String password) {

        mMpd.incrProgress();
        SurespotIdentity identity = IdentityController.getIdentity(this, username, password);

        if (identity == null) {
            mMpd.decrProgress();
            Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
            return;
        }

        final String version = identity.getLatestVersion();
        final PrivateKey pk = identity.getKeyPairDSA().getPrivate();

        // create auth sig
        byte[] saltBytes = ChatUtils.base64DecodeNowrap(identity.getSalt());
        final String dPassword = new String(ChatUtils.base64EncodeNowrap(EncryptionController.derive(password, saltBytes)));
        final String authSignature = EncryptionController.sign(pk, username, dPassword);
        SurespotLog.v(TAG, "generatedAuthSig: " + authSignature);

        // get a key update token from the server
        NetworkManager.getNetworkController(DeleteIdentityActivity.this, username).getDeleteToken(username, dPassword, authSignature, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mMpd.decrProgress();
                Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    final String deleteToken = responseString;
                    new AsyncTask<Void, Void, DeleteIdentityWrapper>() {
                        @Override
                        protected DeleteIdentityWrapper doInBackground(Void... params) {
                            SurespotLog.v(TAG, "received delete token: " + deleteToken);

                            // create token sig
                            final String tokenSignature = EncryptionController.sign(pk, ChatUtils.base64DecodeNowrap(deleteToken),
                                    dPassword.getBytes());

                            SurespotLog.v(TAG, "generatedTokenSig: " + tokenSignature);

                            return new DeleteIdentityWrapper(tokenSignature, authSignature, version);
                        }

                        protected void onPostExecute(final DeleteIdentityWrapper result) {
                            if (result != null) {
                                // upload all this crap to the server
                                NetworkManager.getNetworkController(DeleteIdentityActivity.this, username).deleteUser(username, dPassword, result.authSig, result.tokenSig,
                                        result.keyVersion, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback()
                                {
                                            @Override
                                            public void onFailure(Call call, IOException e) {
                                                SurespotLog.i(TAG, e, "deleteIdentity");
                                                mMpd.decrProgress();
                                                Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
                                            }

                                            @Override
                                            public void onResponse(Call call, Response response, String responseString) throws IOException {
                                                if (response.isSuccessful()) {
                                                    // delete the identity stuff locally
                                                    IdentityController.deleteIdentity(DeleteIdentityActivity.this, username, false);
                                                    refreshSpinner();
                                                    mMpd.decrProgress();
                                                    Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.identity_deleted));
                                                }
                                                else {
                                                    SurespotLog.i(TAG, "deleteIdentity error");
                                                    mMpd.decrProgress();
                                                    Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
                                                }


                                            }
                                        }));
                            }
                            else {
                                mMpd.decrProgress();
                                Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
                            }

                        }
                    }.execute();
                }
                else {
                    mMpd.decrProgress();
                    Utils.makeLongToast(DeleteIdentityActivity.this, getString(R.string.could_not_delete_identity));
                }
            }

        }));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private class DeleteIdentityWrapper {

        public String tokenSig;
        public String authSig;
        public String keyVersion;

        public DeleteIdentityWrapper(String tokenSig, String authSig, String keyVersion) {
            super();
            this.tokenSig = tokenSig;
            this.authSig = authSig;
            this.keyVersion = keyVersion;
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

}
