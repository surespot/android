package org.nick.androidkeystore;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;

import android.util.Log;

public class AndroidRsaEngine implements AsymmetricBlockCipher {

    private static final String TAG = AndroidRsaEngine.class.getSimpleName();
    private static final boolean DEBUG = false;

    private String keyAlias;
    private boolean isSigner;

    private Cipher cipher;
    private KeyStore keyStore;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    private boolean forEncryption;
    private CipherParameters params;

    public AndroidRsaEngine(String keyAlias, boolean isSigner) {
        this.keyAlias = keyAlias;
        this.isSigner = isSigner;
        try {
            this.cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            this.keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            java.security.KeyStore.Entry keyEntry = keyStore.getEntry(
                    this.keyAlias, null);
            publicKey = (RSAPublicKey) ((java.security.KeyStore.PrivateKeyEntry) keyEntry)
                    .getCertificate().getPublicKey();
            privateKey = (RSAPrivateKey) ((java.security.KeyStore.PrivateKeyEntry) keyEntry)
                    .getPrivateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableEntryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getInputBlockSize() {
        int bitSize = publicKey.getModulus().bitLength();

        if (forEncryption) {
            return (bitSize + 7) / 8 - 1;
        } else {
            return (bitSize + 7) / 8;
        }
    }

    @Override
    public int getOutputBlockSize() {
        int bitSize = publicKey.getModulus().bitLength();

        if (forEncryption) {
            return (bitSize + 7) / 8;
        } else {
            return (bitSize + 7) / 8 - 1;
        }
    }

    @Override
    public void init(boolean forEncryption, CipherParameters param) {
        this.forEncryption = forEncryption;
        if (DEBUG) {
            Log.d(TAG, "forEncryption: " + forEncryption);
        }
        this.params = param;
        if (DEBUG) {
            Log.d(TAG, "CipherParameters: " + param);
        }

        try {
            if (forEncryption) {
                cipher.init(Cipher.ENCRYPT_MODE, isSigner ? privateKey
                        : publicKey);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, isSigner ? publicKey
                        : privateKey);
            }
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] processBlock(byte[] in, int inOff, int inLen)
            throws InvalidCipherTextException {
        try {
            String inputStr = Crypto.toHex(in);
            if (DEBUG) {
                Log.d(TAG, "processBlock() INPUT: " + inputStr);
            }
            byte[] result = cipher.doFinal(in, inOff, inLen);
            String outputStr = Crypto.toHex(result);
            if (DEBUG) {
                Log.d(TAG, "processBlock() OUTPUT: " + outputStr);
            }
            byte[] converted = convertOutput(result);
            String convertedStr = Crypto.toHex(converted);
            if (DEBUG) {
                Log.d(TAG, "processBlock() CONVERTED: " + convertedStr);
            }

            return converted;
        } catch (IllegalBlockSizeException e) {
            throw new InvalidCipherTextException("Illegal block size: "
                    + e.getMessage());
        } catch (BadPaddingException e) {
            throw new InvalidCipherTextException("Bad padding: "
                    + e.getMessage());
        }
    }

    // from BC's RSACoreEngine
    public byte[] convertOutput(byte[] output) {
        if (forEncryption) {
            if (output[0] == 0 && output.length > getOutputBlockSize()) // have ended up with an extra zero byte, copy down.
            {
                byte[] tmp = new byte[output.length - 1];

                System.arraycopy(output, 1, tmp, 0, tmp.length);

                return tmp;
            }

            if (output.length < getOutputBlockSize()) // have ended up with less bytes than normal, lengthen
            {
                byte[] tmp = new byte[getOutputBlockSize()];

                System.arraycopy(output, 0, tmp, tmp.length - output.length,
                        output.length);

                return tmp;
            }
        } else {
            if (output[0] == 0) // have ended up with an extra zero byte, copy down.
            {
                byte[] tmp = new byte[output.length - 1];

                System.arraycopy(output, 1, tmp, 0, tmp.length);

                return tmp;
            }
        }

        return output;
    }


}
