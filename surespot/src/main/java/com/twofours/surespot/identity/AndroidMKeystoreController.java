package com.twofours.surespot.identity;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

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

/**
 * Created by owen on 8/24/15.
 */
public class AndroidMKeystoreController {

    private static final int AUTHENTICATION_DURATION_SECONDS = 900;

    public static SecretKey getSecretKey(String userName) {
        if (IdentityController.USE_PUBLIC_KEYSTORE_M) {
            // key generation
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(userName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            KeyGenParameterSpec keySpec = builder
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    //.setBlockModes("CBC")
                    .setRandomizedEncryptionRequired(false)
                    .setUserAuthenticationRequired(false)
                    //.setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                    .build();
            KeyGenerator kg = null;
            try {
                kg = KeyGenerator.getInstance("AES", "AndroidKeyStore");
                kg.init(keySpec);
            } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }

            SecretKey key = kg.generateKey();


            try {
                // key retrieval
                java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);

                //java.security.KeyStore.SecretKeyEntry entry = (java.security.KeyStore.SecretKeyEntry)ks.getEntry(userName, null);
                //key = entry.getSecretKey();
                byte[] encrypted = KeyStoreEncryptionController.simpleEncrypt(key, "testing");
                byte[] decrypted = KeyStoreEncryptionController.simpleDecrypt(key, encrypted);
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
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return key;
        }
        return null;
    }

    public static String loadEncryptedPassword(Context context, String userName, boolean createFakeKeyIfNoneExists) throws InvalidKeyException {
        destroyMKeystore();
        java.security.KeyStore ks = null;
        SecretKey key;
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
                String fileName = userName + "_keystore";
                FileInputStream fis;
                try {
                    fis = context.openFileInput(fileName);
                    byte[] encryptedBytes = new byte[fis.available()];
                    int read = fis.read(encryptedBytes);
                    fis.close();
                    if (encryptedBytes.length > 0) {
                        byte[] decryptedBytes = KeyStoreEncryptionController.simpleDecrypt(keyStoreKey, encryptedBytes);
                        String ss = new String(decryptedBytes, "UTF8");
                        return ss;
                    }
                    return null;
                } catch (FileNotFoundException e) {
                    return null;
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableEntryException e) {
            e.printStackTrace();
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
                //SecretKey key = (SecretKey) ks.getKey(userName, null);
            }

            byte[] encrypted = KeyStoreEncryptionController.simpleEncrypt(key, password);
            String fileName = userName + "_keystore";
            FileOutputStream outputStream;

            try {
                outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                outputStream.write(encrypted);
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
			/*
 			Causes: java.lang.IllegalArgumentException: Unsupported secret key algorithm: PBEWithSHA256And128BitAES-CBC-BC
			java.security.KeyStore.SecretKeyEntry entryOut = (java.security.KeyStore.SecretKeyEntry) ks.getEntry("aliasKey",new android.security.keystore.KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.build());

			SecretKey sk = entryOut.getSecretKey();
			*/
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException e) {
            e.printStackTrace();
        }
    }

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
            e.printStackTrace();
        }
    }
}
