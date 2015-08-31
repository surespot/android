package com.twofours.surespot.encryption;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Base64;

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



/**
 * Created by owen on 8/24/15.
 */
public class KeyStoreEncryptionController {

    private static SecureRandom mSecureRandom = new SurespotSecureRandom();
    private static byte[] mIv;

    public static byte[] simpleEncrypt(SecretKey key, String input) throws InvalidKeyException {
        final byte[] iv = new byte[12];
        mSecureRandom.nextBytes(iv);
        mIv = iv;
      //  final IvParameterSpec ivParams = new IvParameterSpec(iv);

        try {
            return aesEncrypt(input, key, iv);
        } catch (BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (ShortBufferException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] aesEncrypt(String originalString, SecretKey key, byte[] iv) throws BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, ShortBufferException {
        byte[] original = new byte[0];
        try {
            String s = android.util.Base64.encodeToString(originalString.getBytes("UTF8"), Base64.NO_WRAP);
            original = s.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Cipher cipher = null;
        if(iv != null)
        {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");///CBC/PKCS7Padding");//PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128,iv));
        }
        else  //if(iv == null)
        {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");///CBC/PKCS7Padding");//PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }

        int outputLen=cipher.getOutputSize(original.length);
        int toAdd = 16 - (outputLen % 16);
        byte[] output=new byte[outputLen + toAdd];
        output[0] = (byte)original.length;
        System.arraycopy(original, 0, output, 1, original.length);
        return cipher.doFinal(output);
    }

    public static byte[] aesDecrypt(byte[] encrypted, SecretKey key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, ShortBufferException, UnsupportedEncodingException {

        Cipher cipher = null;
        if(iv != null)
        {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");///CBC/PKCS7Padding");//PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        }
        else  //if(iv == null)
        {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");///CBC/PKCS7Padding");//PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
        }
        byte[] bytes = cipher.doFinal(encrypted);
        int len = bytes[0];
        byte[] finalBytes = new byte[len];
        System.arraycopy(bytes,1,finalBytes,0,len);
        String s = new String(finalBytes, "UTF8");
        byte[] finalFinalBytes = android.util.Base64.decode(s, Base64.NO_WRAP);
        return finalFinalBytes;
    }

    public static byte[] simpleDecrypt(SecretKey key, byte[] input) throws InvalidKeyException {
        final byte[] iv = new byte[16];
        mSecureRandom.nextBytes(iv);
        final IvParameterSpec ivParams = new IvParameterSpec(iv);

        try {
            byte[] bytes = aesDecrypt(input, key, mIv); // iv);
            return bytes;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException  | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (ShortBufferException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }

}
