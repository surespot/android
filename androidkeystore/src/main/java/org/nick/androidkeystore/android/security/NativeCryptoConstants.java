package org.nick.androidkeystore.android.security;

public class NativeCryptoConstants {

    public static final int EVP_PKEY_RSA = 6; // NID_rsaEcnryption
    public static final int EVP_PKEY_DSA = 116; // NID_dsa
    public static final int EVP_PKEY_DH = 28; // NID_dhKeyAgreement
    public static final int EVP_PKEY_EC = 408; // NID_X9_62_id_ecPublicKey
    public static final int EVP_PKEY_HMAC = 855; // NID_hmac
    public static final int EVP_PKEY_CMAC = 894; // NID_cmac


    private NativeCryptoConstants() {
    }
}
