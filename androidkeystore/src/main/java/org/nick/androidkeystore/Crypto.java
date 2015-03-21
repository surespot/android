package org.nick.androidkeystore;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.x500.X500Principal;

import org.spongycastle.asn1.ASN1Encoding;
import org.spongycastle.asn1.DERObjectIdentifier;
import org.spongycastle.asn1.x509.AlgorithmIdentifier;
import org.spongycastle.asn1.x509.DigestInfo;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.digests.SHA512Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.signers.PSSSigner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import android.util.Log;

public class Crypto {

    private static final String TAG = Crypto.class.getSimpleName();

    private static String DELIMITER = "]";

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static int KEY_LENGTH = 256;

    private static SecureRandom random = new SecureRandom();

    private Crypto() {
    }

    @SuppressLint("DefaultLocale")
    public static void listAlgorithms(String algFilter) {
        Provider[] providers = Security.getProviders();
        for (Provider p : providers) {
            String providerStr = String.format("%s/%s/%f\n", p.getName(),
                    p.getInfo(), p.getVersion());
            Log.d(TAG, providerStr);
            Set<Service> services = p.getServices();
            List<String> algs = new ArrayList<String>();
            for (Service s : services) {
                boolean match = true;
                if (algFilter != null) {
                    match = s.getAlgorithm().toLowerCase()
                            .contains(algFilter.toLowerCase());
                }

                if (match) {
                    String algStr = String.format("\t%s/%s/%s", s.getType(),
                            s.getAlgorithm(), s.getClassName());
                    algs.add(algStr);
                }
            }

            Collections.sort(algs);
            for (String alg : algs) {
                Log.d(TAG, "\t" + alg);
            }
            Log.d(TAG, "");
        }
    }

    public static SecretKey generateAesKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(KEY_LENGTH);
            SecretKey key = kg.generateKey();

            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    public static byte[] generateIv(int length) {
        byte[] b = new byte[length];
        random.nextBytes(b);

        return b;
    }

    public static String encryptAesCbc(String plaintext, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

            byte[] iv = generateIv(cipher.getBlockSize());
            Log.d(TAG, "IV: " + toHex(iv));
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
            Log.d(TAG, "Cipher IV: "
                    + (cipher.getIV() == null ? null : toHex(cipher.getIV())));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return String.format("%s%s%s", toBase64(iv), DELIMITER,
                    toBase64(cipherText));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encryptRsaOaep(String plaintext, String keyAlias) {
        try {
            AndroidRsaEngine rsa = new AndroidRsaEngine(keyAlias, false);

            Digest digest = new SHA512Digest();
            Digest mgf1digest = new SHA512Digest();
            OAEPEncoding oaep = new OAEPEncoding(rsa, digest, mgf1digest, null);
            oaep.init(true, null);
            byte[] plainBytes = plaintext.getBytes("UTF-8");
            byte[] cipherText = oaep.processBlock(plainBytes, 0,
                    plainBytes.length);

            return toBase64(cipherText);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decryptRsaOaep(String ciphertext, String keyAlias) {
        try {
            AndroidRsaEngine rsa = new AndroidRsaEngine(keyAlias, false);

            Digest digest = new SHA512Digest();
            Digest mgf1digest = new SHA512Digest();
            OAEPEncoding oaep = new OAEPEncoding(rsa, digest, mgf1digest, null);
            oaep.init(false, null);

            byte[] ciphertextBytes = fromBase64(ciphertext);
            byte[] plain = oaep.processBlock(ciphertextBytes, 0,
                    ciphertextBytes.length);

            return new String(plain, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (byte b : bytes) {
            buff.append(String.format("%02X", b));
        }

        return buff.toString();
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    public static String decryptAesCbc(String ciphertext, SecretKey key) {
        try {
            String[] fields = ciphertext.split(DELIMITER);
            if (fields.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid encypted text format");
            }

            byte[] iv = fromBase64(fields[0]);
            byte[] cipherBytes = fromBase64(fields[1]);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
            Log.d(TAG, "Cipher IV: " + toHex(cipher.getIV()));
            byte[] plaintext = cipher.doFinal(cipherBytes);
            String plainrStr = new String(plaintext, "UTF-8");

            return plainrStr;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static RSAPublicKey createPublicKey(byte[] pubKeyBytes)
            throws GeneralSecurityException {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
        RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpec);

        return pubKey;
    }

    @SuppressWarnings("deprecation")
    public static byte[] createSha512EncryptionBlock(byte[] digest, int keySize)
            throws IOException, InvalidCipherTextException {
        // SHA512
        DERObjectIdentifier oid = new DERObjectIdentifier(
                "2.16.840.1.101.3.4.2.3");
        AlgorithmIdentifier sha512Aid = new AlgorithmIdentifier(oid);
        DigestInfo di = new DigestInfo(sha512Aid, digest);
        byte[] diDer = di.getEncoded(ASN1Encoding.DER);

        return padPkcs1(diDer, keySize);
    }

    // PKCS#1 padding
    public static byte[] padPkcs1(byte[] in, int keySize) {
        if (in.length > keySize) {
            throw new IllegalArgumentException("Data too long");
        }
        byte[] result = new byte[keySize / 8];

        result[0] = 0x0;
        result[1] = 0x01; // BT 1

        // PS
        for (int i = 2; i != result.length - in.length - 1; i++) {
            result[i] = (byte) 0xff;
        }

        // end of padding
        result[result.length - in.length - 1] = 0x00;
        // D
        System.arraycopy(in, 0, result, result.length - in.length, in.length);

        return result;
    }

    @SuppressLint("NewApi")
    public static KeyPair generateRsaPairWithGenerator(Context ctx, String alais)
            throws Exception {
        Calendar notBefore = Calendar.getInstance();
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(1, Calendar.YEAR);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(ctx)
                .setAlias(alais)
                .setSubject(
                        new X500Principal(String.format("CN=%s, OU=%s", alais,
                                ctx.getPackageName())))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(notBefore.getTime())
                .setEndDate(notAfter.getTime()).build();

        KeyPairGenerator kpGenerator = KeyPairGenerator.getInstance("RSA",
                "AndroidKeyStore");
        kpGenerator.initialize(spec);
        KeyPair kp = kpGenerator.generateKeyPair();

        return kp;
    }

    @SuppressLint("NewApi")
    public static KeyPair generateEcPairWithGenerator(Context ctx, String alais)
            throws Exception {
        Calendar notBefore = Calendar.getInstance();
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(1, Calendar.YEAR);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(ctx)
                .setAlias(alais)
                .setKeyType("EC")
                // curve
                .setKeySize(256)
                .setSubject(
                        new X500Principal(String.format("CN=%s, OU=%s", alais,
                                ctx.getPackageName())))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(notBefore.getTime())
                .setEndDate(notAfter.getTime()).build();

        KeyPairGenerator kpGenerator = KeyPairGenerator.getInstance("RSA",
                "AndroidKeyStore");
        kpGenerator.initialize(spec);
        KeyPair kp = kpGenerator.generateKeyPair();

        return kp;
    }

    public static String signRsaPss(String keyAlias, String toSign) {
        try {
            RSAPublicKey pubKey = loadRsaPublicKey(keyAlias);

            AndroidRsaEngine rsa = new AndroidRsaEngine(keyAlias, true);

            Digest digest = new SHA512Digest();
            Digest mgf1digest = new SHA512Digest();
            PSSSigner signer = new PSSSigner(rsa, digest, mgf1digest, 512 / 8);
            RSAKeyParameters params = new RSAKeyParameters(false,
                    pubKey.getModulus(), pubKey.getPublicExponent());
            signer.init(true, params);

            byte[] signedData = toSign.getBytes("UTF-8");
            signer.update(signedData, 0, signedData.length);
            byte[] signature = signer.generateSignature();

            return toBase64(signature);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DataLengthException e) {
            throw new RuntimeException(e);
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    public static RSAPublicKey loadRsaPublicKey(String keyAlias)
            throws GeneralSecurityException, IOException {
        return (RSAPublicKey) loadPublicKey(keyAlias);
    }

    public static PublicKey loadPublicKey(String keyAlias)
            throws GeneralSecurityException, IOException {
        java.security.KeyStore ks = java.security.KeyStore
                .getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.KeyStore.Entry keyEntry = ks.getEntry(keyAlias, null);

        return ((java.security.KeyStore.PrivateKeyEntry) keyEntry)
                .getCertificate().getPublicKey();
    }

    public static PrivateKey loadPrivateKey(String keyAlias)
            throws GeneralSecurityException, IOException {
        java.security.KeyStore ks = java.security.KeyStore
                .getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.KeyStore.Entry keyEntry = ks.getEntry(keyAlias, null);

        return ((java.security.KeyStore.PrivateKeyEntry) keyEntry)
                .getPrivateKey();
    }

    public static boolean verifyRsaPss(String signatureStr, String signedStr,
            String keyAlias) {
        try {
            RSAPublicKey pubKey = loadRsaPublicKey(keyAlias);

            AndroidRsaEngine rsa = new AndroidRsaEngine(keyAlias, true);

            Digest digest = new SHA512Digest();
            Digest mgf1digest = new SHA512Digest();
            PSSSigner signer = new PSSSigner(rsa, digest, mgf1digest, 512 / 8);
            RSAKeyParameters params = new RSAKeyParameters(false,
                    pubKey.getModulus(), pubKey.getPublicExponent());
            signer.init(false, params);

            byte[] signedData = signedStr.getBytes("UTF-8");
            signer.update(signedData, 0, signedData.length);
            byte[] signature = fromBase64(signatureStr);
            boolean result = signer.verifySignature(signature);

            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String signEc(String keyAlias, String toSign) {
        try {
            PrivateKey privKey = loadPrivateKey(keyAlias);

            Signature sig = Signature.getInstance("SHA512WITHECDSA");
            sig.initSign(privKey);
            byte[] signedData = toSign.getBytes("UTF-8");
            sig.update(signedData);
            byte[] signature = sig.sign();

            return toBase64(signature);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DataLengthException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verifyEc(String signatureStr, String signedStr,
            String keyAlias) {
        try {
            PublicKey pubKey = loadPublicKey(keyAlias);

            Signature sig = Signature.getInstance("SHA512WITHECDSA");
            sig.initVerify(pubKey);

            byte[] signedData = signedStr.getBytes("UTF-8");
            byte[] signature = fromBase64(signatureStr);
            sig.update(signedData);
            boolean result = sig.verify(signature);

            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
