package org.nick.androidkeystore;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.nick.androidkeystore.android.security.KeyStore;
import org.nick.androidkeystore.android.security.KeyStoreJb43;
import org.nick.androidkeystore.android.security.KeyStoreKk;
import org.nick.androidkeystore.android.security.KeyStoreM;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

// TO BE DELETED - however, this provides good examples for use for utilizing the new KeyStoreM
public class KeystoreActivity extends Activity implements OnClickListener {

    private static final String TAG = KeystoreActivity.class.getSimpleName();

    private static final boolean IS_JB43 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    private static final boolean IS_JB = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static final boolean IS_KK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    private static final boolean IS_M = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

    private static final String EXTRA_CIPHERTEXT = "org.nick.androidkeystore.CIPHERTEXT";
    private static final String EXTRA_KEY_NAME = "org.nick.androidkeystore.KEY_NAME";
    private static final String EXTRA_PLAINTEXT = "org.nick.androidkeystore.PLAINTEXT";

    public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";

    public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";
    public static final String RESET_ACTION = "com.android.credentials.RESET";

    private static final String KEY_NAME = "aes_key";
    private static final String RSA_KEY_NAME = "rsa_key";
    private static final String EC_KEY_NAME = "ec_key";
    private static final String PLAIN_TEXT = "Hello, KeyStore!";

    private static int keyNum = 0;

    private TextView encryptedText;
    private TextView decryptedText;

    private Button encryptButton;
    private Button decryptButton;
    private Button signButton;
    private Button verifyButton;
    private Button encryptRsaButton;
    private Button decryptRsaButton;
    private Button signEcButton;
    private Button verifyEcButton;
    private Button listButton;
    private Button resetButton;

    private ListView keyList;

    private KeyStore ks;

    private String encryptionKeyName;
    private String signKeyName;
    private String rsaEncryptKeyName;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);

        PRNGFixes.apply();

        //setContentView(R.layout.main);

        //findViews();

        if (savedInstanceState != null) {
            encryptedText.setText(savedInstanceState
                    .getString(EXTRA_CIPHERTEXT));
            encryptionKeyName = savedInstanceState.getString(EXTRA_KEY_NAME);

            String plaintext = savedInstanceState.getString(EXTRA_PLAINTEXT);
            decryptedText.setText(plaintext);
        }

        if (IS_M) {
            ks = KeyStoreM.getInstance();
        } else if (IS_KK) {
            ks = KeyStoreKk.getInstance();
        } else if (IS_JB43) {
            ks = KeyStoreJb43.getInstance();
        } else {
            ks = KeyStore.getInstance();
        }

        displayKeystoreState();
    }

    abstract class KeystoreTask extends AsyncTask<Void, Void, String[]> {

        Exception error;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
            toggleControls(false);
        }

        @Override
        protected String[] doInBackground(Void... params) {
            try {
                return doWork();
            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Error: " + e.getMessage(), e);

                return null;
            }
        }

        protected abstract String[] doWork() throws Exception;

        @Override
        protected void onPostExecute(String[] result) {
            setProgressBarIndeterminateVisibility(false);
            toggleControls(true);

            if (error != null) {
                Toast.makeText(KeystoreActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_LONG)
                        .show();

                return;
            }

            updateUi(result);
        }

        protected abstract void updateUi(String[] result);
    }

    private void toggleControls(boolean enable) {
        encryptButton.setEnabled(enable);
        decryptButton.setEnabled(enable);
        listButton.setEnabled(enable);
        resetButton.setEnabled(enable);
        if (IS_JB) {
            signButton.setEnabled(enable);
            verifyButton.setEnabled(enable);
            encryptRsaButton.setEnabled(enable);
            decryptRsaButton.setEnabled(enable);
        }
        if (IS_KK) {
            signEcButton.setEnabled(enable);
            verifyEcButton.setEnabled(enable);
        }
    }

    private void displayKeystoreState() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() {
                Log.d(TAG, "Keystore state: " + ks.state());
                String status = String.format("Keystore state:%s", ks.state()
                        .toString());
                String storeType = null;
                
                if (IS_M) {
                    storeType = ((KeyStoreM) ks).isHardwareBacked() ? "HW-backed"
                            : "SW only";
                } else if (IS_KK) {
                    storeType = ((KeyStoreKk) ks).isHardwareBacked() ? "HW-backed"
                            : "SW only";
                } else if (IS_JB43) {
                    storeType = ((KeyStoreJb43) ks).isHardwareBacked() ? "HW-backed"
                            : "SW only";
                }

                return new String[] { status, storeType };
            }

            @Override
            @SuppressLint("NewApi")
            protected void updateUi(String[] result) {
                setTitle(result[0]);
                if (result[1] != null) {
                    if (IS_JB43) {
                        getActionBar().setSubtitle(result[1]);
                    }
                }
            }
        }.execute();
    }
/*
    private void findViews() {
        encryptedText = (TextView) findViewById(R.id.encrypted_text);
        decryptedText = (TextView) findViewById(R.id.decrypted_text);

        encryptButton = (Button) findViewById(R.id.encrypt_button);
        encryptButton.setOnClickListener(this);

        decryptButton = (Button) findViewById(R.id.decrypt_button);
        decryptButton.setOnClickListener(this);

        signButton = (Button) findViewById(R.id.sign_rsa_button);
        signButton.setOnClickListener(this);
        signButton.setEnabled(IS_JB);

        verifyButton = (Button) findViewById(R.id.verify_rsa_button);
        verifyButton.setOnClickListener(this);
        verifyButton.setEnabled(IS_JB);

        encryptRsaButton = (Button) findViewById(R.id.encrypt_rsa_button);
        encryptRsaButton.setOnClickListener(this);
        encryptRsaButton.setEnabled(IS_JB);

        decryptRsaButton = (Button) findViewById(R.id.decrypt_rsa_button);
        decryptRsaButton.setOnClickListener(this);
        decryptRsaButton.setEnabled(IS_JB);

        signEcButton = (Button) findViewById(R.id.sign_ec_button);
        signEcButton.setOnClickListener(this);
        signEcButton.setEnabled(IS_KK);

        verifyEcButton = (Button) findViewById(R.id.verify_ec_button);
        verifyEcButton.setOnClickListener(this);
        verifyEcButton.setEnabled(IS_KK);

        listButton = (Button) findViewById(R.id.list_button);
        listButton.setOnClickListener(this);

        resetButton = (Button) findViewById(R.id.reset_button);
        resetButton.setOnClickListener(this);

        keyList = (ListView) findViewById(R.id.key_list);
    }*/

    @TargetApi(18)
    @Override
    protected void onResume() {
        super.onResume();

        displayKeystoreState();

        if (ks.state() == KeyStore.State.UNLOCKED) {
            showKeys();
        }

        if (IS_KK) {
            Log.d(TAG,
                    "RSA supported " + KeyChain.isKeyAlgorithmSupported("RSA"));
            Log.d(TAG, "RSA bound " + KeyChain.isBoundKeyAlgorithm("RSA"));

            Log.d(TAG,
                    "DSA supported " + KeyChain.isKeyAlgorithmSupported("DSA"));
            Log.d(TAG, "DSA bound " + KeyChain.isBoundKeyAlgorithm("DSA"));

            Log.d(TAG, "EC supported " + KeyChain.isKeyAlgorithmSupported("EC"));
            Log.d(TAG, "EC bound " + KeyChain.isBoundKeyAlgorithm("EC"));
        } else if (IS_JB43) {
            Log.d(TAG,
                    "RSA supported " + KeyChain.isKeyAlgorithmSupported("RSA"));
            Log.d(TAG, "RSA bound " + KeyChain.isBoundKeyAlgorithm("RSA"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        String ciphertext = encryptedText.getText().toString();
        String plaintext = decryptedText.getText().toString();
        if (ciphertext != null) {
            outState.putString(EXTRA_CIPHERTEXT, encryptedText.getText()
                    .toString());
            outState.putString(EXTRA_KEY_NAME, encryptionKeyName);
        }
        if (plaintext != null) {
            outState.putString(EXTRA_PLAINTEXT, plaintext);
        }

    }

    @Override
    public void onClick(View v) {
        if (ks.state() != KeyStore.State.UNLOCKED) {
            Toast.makeText(
                    this,
                    "Keystore is locked or not initialized. Retry operation "
                            + "after unlock activity returns.",
                    Toast.LENGTH_LONG).show();
            unlock();

            // unlocking is in a separate activity, stop here
            return;
        }
/*
        try {
            if (v.getId() == R.id.reset_button) {
                deleteAllKeys();
                // resetKeystore();
            } else if (v.getId() == R.id.list_button) {
                showKeys();
                // testKeystore();
            } else if (v.getId() == R.id.encrypt_button) {
                encrypt();
            } else if (v.getId() == R.id.decrypt_button) {
                if (encryptedText.getText() == null
                        || encryptedText.getText().equals("")) {
                    Toast.makeText(this, "No encrypted text found.",
                            Toast.LENGTH_SHORT).show();

                    return;
                }

                decrypt();
            } else if (v.getId() == R.id.sign_rsa_button) {
                signRsaPss();
            } else if (v.getId() == R.id.verify_rsa_button) {
                verifyRsaPss();
            } else if (v.getId() == R.id.encrypt_rsa_button) {
                encryptRsaOaep();
            } else if (v.getId() == R.id.decrypt_rsa_button) {
                decryptRsa();
            } else if (v.getId() == R.id.sign_ec_button) {
                signEcDsa();
            } else if (v.getId() == R.id.verify_ec_button) {
                verifyEcDsa();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
            Toast.makeText(this, "Unexpected error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        */
    }

    private void encryptRsaOaep() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String alias = RSA_KEY_NAME + keyNum;

                KeyPair kp = Crypto.generateRsaPairWithGenerator(
                        KeystoreActivity.this, alias);
                Log.d(TAG, String.format("Genarated %d bit RSA key pair",
                        ((RSAPublicKey) kp.getPublic()).getModulus()
                                .bitLength()));
                rsaEncryptKeyName = alias;
                keyNum++;

                String ciphertext = Crypto.encryptRsaOaep(PLAIN_TEXT,
                        rsaEncryptKeyName);

                return new String[] { ciphertext };
            }

            @Override
            protected void updateUi(String[] result) {
                encryptedText.setText(result[0]);
                decryptedText.setText("");

                showKeys();

            }
        }.execute();
    }

    private void decryptRsa() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String plainStr = Crypto.decryptRsaOaep(encryptedText.getText()
                        .toString(), rsaEncryptKeyName);

                return new String[] { plainStr };
            }

            @Override
            protected void updateUi(String[] result) {
                decryptedText.setText(result[0]);

                showKeys();

            }
        }.execute();
    }

    private void decrypt() {
        final String ciphertext = encryptedText.getText().toString();
        new KeystoreTask() {

            @Override
            protected String[] doWork() {
                byte[] keyBytes = ks.get(encryptionKeyName);
                if (keyBytes == null) {
                    Log.w(TAG, "Encryption key not found in keystore: "
                            + encryptionKeyName);

                    return null;
                }

                SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
                String plaintext = Crypto.decryptAesCbc(ciphertext, key);

                return new String[] { plaintext };
            }

            @Override
            protected void updateUi(String[] result) {
                if (result == null) {
                    Toast.makeText(KeystoreActivity.this,
                            "Encryption key not found in keystore.",
                            Toast.LENGTH_SHORT).show();

                    return;
                }

                decryptedText.setText(result[0]);
            }
        }.execute();
    }

    private void encrypt() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() {
                SecretKey key = Crypto.generateAesKey();
                encryptionKeyName = KEY_NAME + keyNum;
                boolean success = ks.put(encryptionKeyName, key.getEncoded());
                Log.d(TAG, "put key success: " + success);
                checkRc(success);

                keyNum++;
                String ciphertext = Crypto.encryptAesCbc(PLAIN_TEXT, key);

                return new String[] { ciphertext };
            }

            @Override
            protected void updateUi(String[] result) {
                encryptedText.setText(result[0]);
                decryptedText.setText("");

                showKeys();

            }
        }.execute();
    }

    private void signRsaPss() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String alias = RSA_KEY_NAME + keyNum;
                KeyPair kp = Crypto.generateRsaPairWithGenerator(
                        KeystoreActivity.this, alias);
                Log.d(TAG, String.format("Genarated %d bit RSA key pair",
                        ((RSAPublicKey) kp.getPublic()).getModulus()
                                .bitLength()));
                signKeyName = alias;
                keyNum++;

                String signature = Crypto.signRsaPss(signKeyName, PLAIN_TEXT);

                return new String[] { signature };
            }

            @Override
            protected void updateUi(String[] result) {
                encryptedText.setText(result[0]);
                decryptedText.setText("");

                showKeys();

            }
        }.execute();
    }

    private void verifyRsaPss() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String signatureStr = encryptedText.getText().toString();
                boolean verified = Crypto.verifyRsaPss(signatureStr,
                        PLAIN_TEXT, signKeyName);
                Log.d(TAG, "RSA PSS signature verification result: " + verified);

                return new String[] { verified ? "Signature verifies"
                        : "Invalid signature" };
            }

            @Override
            protected void updateUi(String[] result) {
                if (result == null) {
                    Toast.makeText(KeystoreActivity.this,
                            "Signature key key not found in keystore.",
                            Toast.LENGTH_SHORT).show();

                    return;
                }

                decryptedText.setText(result[0]);
            }
        }.execute();
    }

    private void signEcDsa() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String alias = EC_KEY_NAME + keyNum;
                KeyPair kp = Crypto.generateEcPairWithGenerator(
                        KeystoreActivity.this, alias);
                Log.d(TAG, String.format("Genarated %d bit EC key pair",
                        ((ECPublicKey) kp.getPublic()).getParams().getCurve()
                                .getField().getFieldSize()));
                signKeyName = alias;
                keyNum++;

                String signature = Crypto.signEc(signKeyName, PLAIN_TEXT);

                return new String[] { signature };
            }

            @Override
            protected void updateUi(String[] result) {
                encryptedText.setText(result[0]);
                decryptedText.setText("");

                showKeys();

            }
        }.execute();
    }

    private void verifyEcDsa() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String signatureStr = encryptedText.getText().toString();
                boolean verified = Crypto.verifyEc(signatureStr, PLAIN_TEXT,
                        signKeyName);
                Log.d(TAG, "RSA PSS signature verification result: " + verified);

                return new String[] { verified ? "Signature verifies"
                        : "Invalid signature" };
            }

            @Override
            protected void updateUi(String[] result) {
                if (result == null) {
                    Toast.makeText(KeystoreActivity.this,
                            "Signature key key not found in keystore.",
                            Toast.LENGTH_SHORT).show();

                    return;
                }

                decryptedText.setText(result[0]);
            }
        }.execute();
    }

    private void deleteAllKeys() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() throws Exception {
                String[] keys = ks.saw("");
                for (String key : keys) {
                    boolean success = ks.delete(key);
                    Log.d(TAG, String.format("delete key '%s' success: %s",
                            key, success));
                    if (!success && IS_JB) {
                        success = ks.delKey(key);
                        Log.d(TAG, String.format("delKey '%s' success: %s",
                                key, success));
                    }
                    // delete_keypair() is optional, don't fail
                    // checkRc(success);
                }

                return null;
            }

            @Override
            protected void updateUi(String[] result) {
                encryptionKeyName = null;
                signKeyName = null;

                encryptedText.setText("");
                decryptedText.setText("");

                showKeys();
            }
        }.execute();
    }

    private void showKeys() {
        new KeystoreTask() {

            @Override
            protected String[] doWork() {
                String[] keyNames = ks.saw("");

                return keyNames;
            }

            @Override
            protected void updateUi(String[] keyNames) {
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        KeystoreActivity.this,
                        android.R.layout.simple_list_item_1, keyNames);
                keyList.setAdapter(adapter);

                Log.d(TAG, "Keys: ");
                for (String keyName : keyNames) {
                    byte[] keyBytes = ks.get(keyName);

                    if (keyBytes != null) {
                        Log.d(TAG, String.format("\t%s: %s", keyName,
                                new BigInteger(keyBytes).toString()));
                    } else {
                        Log.d(TAG, String.format("\t%s: %s", keyName,
                                "RSA unexportable"));
                    }
                }
            }
        }.execute();
    }

    private void unlock() {
        if (ks.state() == KeyStore.State.UNLOCKED) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                startActivity(new Intent(OLD_UNLOCK_ACTION));
            } else {
                startActivity(new Intent(UNLOCK_ACTION));
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No UNLOCK activity: " + e.getMessage(), e);
            Toast.makeText(this, "No keystore unlock activity found.",
                    Toast.LENGTH_SHORT).show();

            return;
        }
    }

    @SuppressWarnings("unused")
    private void resetKeystore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Toast.makeText(this, "Reset not supported on pre-ICS devices",
                    Toast.LENGTH_SHORT).show();
        } else {
            startActivity(new Intent(RESET_ACTION));
        }
    }

    @SuppressWarnings("unused")
    private void testKeystore() {
        boolean success = ks.lock();
        if (!success) {
            Log.d(TAG, "lock() last error = " + rcToStr(ks.getLastError()));
        }
        success = ks.unlock("foo");
        if (!success) {
            Log.d(TAG, "unlock() last error = " + rcToStr(ks.getLastError()));
        }
        success = ks.password("foobar");
        if (!success) {
            Log.d(TAG, "password() last error = " + rcToStr(ks.getLastError()));
        }
        success = ks.reset();
        if (!success) {
            Log.d(TAG, "reset() last error = " + rcToStr(ks.getLastError()));
        }
        success = ks.isEmpty();
        if (!success) {
            Log.d(TAG, "isEmpty() last error = " + rcToStr(ks.getLastError()));
        }
    }

    private void checkRc(boolean success) {
        if (!success) {
            String errorStr = rcToStr(ks.getLastError());
            Log.d(TAG, "last error = " + errorStr);

            throw new RuntimeException("Keystore error: " + errorStr);
        }
    }

    private static final String rcToStr(int rc) {
        switch (rc) {
        case KeyStore.NO_ERROR:
            return "NO_ERROR";
        case KeyStore.LOCKED:
            return "LOCKED";
        case KeyStore.UNINITIALIZED:
            return "UNINITIALIZED";
        case KeyStore.SYSTEM_ERROR:
            return "SYSTEM_ERROR";
        case KeyStore.PROTOCOL_ERROR:
            return "PROTOCOL_ERROR";
        case KeyStore.PERMISSION_DENIED:
            return "PERMISSION_DENIED";
        case KeyStore.KEY_NOT_FOUND:
            return "KEY_NOT_FOUND";
        case KeyStore.VALUE_CORRUPTED:
            return "VALUE_CORRUPTED";
        case KeyStore.UNDEFINED_ACTION:
            return "UNDEFINED_ACTION";
        case KeyStore.WRONG_PASSWORD:
            return "WRONG_PASSWORD";
        default:
            return "Unknown RC";
        }
    }
}
