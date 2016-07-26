package com.ethercamp.harmony.keystore;

import org.codehaus.jackson.annotate.JsonSetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.ethereum.crypto.ECKey;
import org.spongycastle.crypto.generators.SCrypt;
import org.spongycastle.jcajce.provider.digest.Keccak;
import org.spongycastle.util.encoders.Hex;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.security.*;
import java.util.Arrays;

public class Keystore {

    private KeystoreCrypto crypto;
    private String id;
    private Integer version;
    private String address;

    public static void toKeystore(final ECKey key, final String password) {
        ObjectMapper mapper = new ObjectMapper();

//        try {
////            Keystore ksObj = mapper.wr(keystore, Keystore.class);
//            final Keystore keystore = new Keystore();
//            final byte[] cipherKey = checkMacScrypt(ksObj, password);
//
//            final byte[] secret = decryptAes(Hex.decode(ksObj.getCrypto().getCipherparams().getIv()), cipherKey, Hex.decode(ksObj.getCrypto().getCiphertext()));
//
//            final String fileName = "UTC--" + "--" + key.getAddress();
//            mapper.writeValue(new File(fileName), keystore);
////            return ECKey.fromPrivate(secret);
//        } catch (Exception e) {
//            throw new RuntimeException("Problem storing key. Message: " + e.getMessage(), e);
//        }
    }


    public static ECKey fromKeystore(final File keystore, final String password) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            Keystore ksObj = mapper.readValue(keystore, Keystore.class);
            byte[] cipherKey;
            switch (ksObj.getCrypto().getKdf()) {
                case "pbkdf2":
                    cipherKey = checkMacSha3(ksObj, password);
                    break;
                case "scrypt":
                    cipherKey = checkMacScrypt(ksObj, password);

                    break;
                default:
                    throw new RuntimeException("non valid algorithm " + ksObj.getCrypto().getCipher());
            }

            byte[] secret = decryptAes(Hex.decode(ksObj.getCrypto().getCipherparams().getIv()), cipherKey, Hex.decode(ksObj.getCrypto().getCiphertext()));

            return ECKey.fromPrivate(secret);
        } catch (Exception e) {
            throw new RuntimeException("Problem loading key. Message: " + e.getMessage(), e);
        }
    }

    private static byte[] decryptAes(byte[] iv, byte[] keyBytes, byte[] cipherText) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        //Initialisation
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        //Mode
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        return cipher.doFinal(cipherText);
    }

    private static byte[] checkMacSha3(Keystore keystore, String password) throws Exception {
        byte[] salt = Hex.decode(keystore.getCrypto().getKdfparams().getSalt());
        int iterations = keystore.getCrypto().getKdfparams().getC();
        byte[] part = new byte[16];
        byte[] h = hash(password, salt, iterations);
        byte[] cipherText = Hex.decode(keystore.getCrypto().getCiphertext());
        System.arraycopy(h, 16, part, 0, 16);

        byte[] actual = sha3(concat(part, cipherText));

        if (Arrays.equals(actual, Hex.decode(keystore.getCrypto().getMac()))) {
            System.arraycopy(h, 0, part, 0, 16);
            return part;
        }

        throw new RuntimeException("error while loading the private key from the keystore. Most probably a wrong passphrase");
    }

    private static byte[] checkMacScrypt(Keystore keystore, String password) throws Exception {
        byte[] part = new byte[16];
        KdfParams params = keystore.getCrypto().getKdfparams();
        byte[] h = scrypt(password.getBytes(), Hex.decode(params.getSalt()), params.getN(), params.getR(), params.getP(), params.getDklen());
        byte[] cipherText = Hex.decode(keystore.getCrypto().getCiphertext());
        System.arraycopy(h, 16, part, 0, 16);

        byte[] actual = sha3(concat(part, cipherText));

        if (Arrays.equals(actual, Hex.decode(keystore.getCrypto().getMac()))) {
            System.arraycopy(h, 0, part, 0, 16);
            return part;
        }

        throw new RuntimeException("error while loading the private key from the keystore. Most probably a wrong passphrase");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    private static byte[] scrypt(byte[] pass, byte[] salt, int n, int r, int p, int dkLen) throws GeneralSecurityException {
        return SCrypt.generate(pass, salt, n, r, p, dkLen);
    }

    private static byte[] hash(String encryptedData, byte[] salt, int iterations) throws Exception {
        char[] chars = encryptedData.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

    private static byte[] sha3(byte[] h) throws NoSuchAlgorithmException {
        MessageDigest KECCAK = new Keccak.Digest256();
        KECCAK.reset();
        KECCAK.update(h);
        return KECCAK.digest();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public KeystoreCrypto getCrypto() {
        return crypto;
    }

    @JsonSetter("crypto")
    public void setCrypto(KeystoreCrypto crypto) {
        this.crypto = crypto;
    }

    @JsonSetter("Crypto")
    public void setCryptoOld(KeystoreCrypto crypto) {
        this.crypto = crypto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }


}
