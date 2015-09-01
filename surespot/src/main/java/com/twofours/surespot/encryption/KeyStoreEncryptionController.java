package com.twofours.surespot.encryption;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Base64;

import com.twofours.surespot.common.SurespotLog;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyStoreEncryptionController {
    private static final String TAG = "KeyStoreEncryptionController";
    private static SecureRandom mSecureRandom = new SurespotSecureRandom();

    public static EncryptedBytesAndIv simpleEncrypt(SecretKey key, String input) throws InvalidKeyException {
        final byte[] iv = new byte[12];
        mSecureRandom.nextBytes(iv);
        EncryptedBytesAndIv result = new EncryptedBytesAndIv();
        result.mIv = iv;

        try {
            result.mEncrypted = aesEncrypt(input, key, iv);
            return result;
        } catch (BadPaddingException | IllegalBlockSizeException | ShortBufferException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            SurespotLog.d(TAG, "Error decrypting encrypted password using Keystore key: " + e.getMessage());
        }

        return null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static byte[] aesEncrypt(String originalString, SecretKey key, byte[] iv) throws BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, ShortBufferException {
        byte[] original = new byte[0];
        try {
            String s = android.util.Base64.encodeToString(originalString.getBytes("UTF8"), Base64.NO_WRAP);
            original = s.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            SurespotLog.d(TAG, "Error converting to Base64 (?): " + e.getMessage());
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(original);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static byte[] aesDecrypt(byte[] encrypted, SecretKey key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, ShortBufferException, UnsupportedEncodingException {

        Cipher cipher = null;

        cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

        byte[] bytes = cipher.doFinal(encrypted);
        String s = new String(bytes, "UTF8");
        byte[] finalFinalBytes = android.util.Base64.decode(s, Base64.NO_WRAP);
        return finalFinalBytes;
    }

    public static byte[] simpleDecrypt(SecretKey key, byte[] input, byte[] iv) throws InvalidKeyException {

        try {
            byte[] bytes = aesDecrypt(input, key, iv);
            return bytes;
        } catch (ShortBufferException | UnsupportedEncodingException | NoSuchAlgorithmException | NoSuchPaddingException  | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException e) {
            SurespotLog.d(TAG, "Error decrypting encrypted password: " + e.getMessage());
        }

        return null;
    }
}
