package com.twofours.surespot.identity;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.EncryptedBytesAndIv;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.encryption.KeyStoreEncryptionController;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class AndroidMKeystoreController {

    private static final String TAG = "AndroidMKeystoreController";
    private static final int AUTHENTICATION_DURATION_SECONDS = 900;

    // generates a new secret AES/GCM 256-bit key
    public static SecretKey getSecretKey(String userName) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        if (IdentityController.USE_PUBLIC_KEYSTORE_M) {
            // key generation
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(userName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            KeyGenParameterSpec keySpec = builder
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .setUserAuthenticationRequired(false)
                    .build();
            KeyGenerator kg = null;
            kg = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            kg.init(keySpec);

            SecretKey key = kg.generateKey();

            /*
            // Test code to encrypt and decrypt a simple password using the generated AES/GCM key
            try {
                EncryptedBytesAndIv bytesAndIv = KeyStoreEncryptionController.simpleEncrypt(key, "testing");
                byte[] decrypted = KeyStoreEncryptionController.simpleDecrypt(key, bytesAndIv.mEncrypted, bytesAndIv.mIv);
                int position = decrypted.length;
                for (int n = 0; n < decrypted.length; n++) {
                    if (decrypted[n] == 0) {
                        position = n;
                        break;
                    }
                }
                byte[] original = new byte[position];
                System.arraycopy(decrypted, 0, original, 0, position);
                String ss = new String(original, "UTF8");
                if (ss == "testing") {
                    int f = 4;
                }
            } catch (InvalidKeyException | IOException e) {
                e.printStackTrace();
            }
            */

            return key;
        }
        return null;
    }

    // loads the password for the given userName from the keystore
    public static String loadEncryptedPassword(Context context, String userName, boolean createFakeKeyIfNoneExists) throws InvalidKeyException {
        // destroyMKeystore(); - useful if you want to blow away the keystore quickly - uncomment and run
        java.security.KeyStore ks = null;

        // read encrypted password and IV from preferences
        SharedPreferences pm = context.getSharedPreferences(userName, Context.MODE_PRIVATE);
        String encryptedPassword = pm.getString("encrypt_pass", null);
        String iv = pm.getString("encrypt_iv", null);
        if (encryptedPassword == null || iv == null) {
            return null;
        }

        try {
            ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(userName))
            {
                if (createFakeKeyIfNoneExists) {
                    throw new InvalidKeyException();
                }
            } else {
                java.security.KeyStore.SecretKeyEntry entry = (java.security.KeyStore.SecretKeyEntry) ks.getEntry(userName, null);
                SecretKey keyStoreKey = (SecretKey) entry.getSecretKey();

                byte[] ivBytes = android.util.Base64.decode(iv, Base64.NO_WRAP);
                byte[] encryptedBytes = android.util.Base64.decode(encryptedPassword, Base64.NO_WRAP);
                byte[] decryptedBytes = KeyStoreEncryptionController.simpleDecrypt(keyStoreKey, encryptedBytes, ivBytes);
                String password = new String(decryptedBytes, "UTF8");
                return password;
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableEntryException e) {
            SurespotLog.d(TAG, "Error loading encrypted password: " + e.getMessage());
        }
        return null;
    }

    public static void saveEncryptedPassword(Context context, String userName, String password) throws InvalidKeyException {
        java.security.KeyStore ks = null;
        SecretKey key;
        try {
            ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);

            if (!ks.containsAlias(userName)) {
                key = getSecretKey(userName);
            } else {
                java.security.KeyStore.SecretKeyEntry entry = (java.security.KeyStore.SecretKeyEntry) ks.getEntry(userName, null);
                key = (SecretKey) entry.getSecretKey();
            }

            EncryptedBytesAndIv bytesAndIv = KeyStoreEncryptionController.simpleEncrypt(key, password);
            if (bytesAndIv != null) {
                byte[] encrypted = bytesAndIv.mEncrypted;
                String encryptedPassword = android.util.Base64.encodeToString(encrypted, Base64.NO_WRAP);
                String iv = android.util.Base64.encodeToString(bytesAndIv.mIv, Base64.NO_WRAP);
                SharedPreferences pm = context.getSharedPreferences(userName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pm.edit();
                editor.putString("encrypt_pass", encryptedPassword);
                editor.putString("encrypt_iv", iv);
                editor.apply();
            } else {
                SurespotLog.d(TAG, "Unable to save encrypted password to keystore - encryption failed");
            }
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
            SurespotLog.d(TAG, "Error saving encrypted password: " + e.getMessage());
        }
    }

    // Destroys all keys in the Android M(+) keystore
    public static void destroyMKeystore()
    {
        java.security.KeyStore ks = null;

        try {
            ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            List<String> aliases = new ArrayList<String>();
            Enumeration<String> all = ks.aliases();
            while (all.hasMoreElements()){
                String s = all.nextElement();
                if (!aliases.contains(s)) {
                    aliases.add(s);
                }
                else{
                    break;
                }
            }
            for (String s : aliases) {
                ks.deleteEntry(s);
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            SurespotLog.d(TAG, "Error destroying keystore: " + e.getMessage());
        }
    }
}
