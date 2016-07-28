package com.ethercamp.harmony.keystore;

import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.annotate.JsonSetter;
import org.codehaus.jackson.map.ObjectMapper;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
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
import java.util.Random;
import java.util.UUID;

@Slf4j(topic = "keystore")
public class Keystore {

    private KeystoreCrypto crypto;
    private String id;
    private Integer version;
    private String address;

    public static void toKeystore(File file, final ECKey key, String password) {
        try {
            // n,r,p = 2^18, 8, 1 uses 256MB memory and approx 1s CPU time on a modern CPU.
//            final int ScryptN = ((Double) Math.pow(10.0, 18.0)).intValue();
            final int ScryptN = 262144;
            final int ScryptR = 8;
            final int ScryptP = 1;
            final int ScryptDklen = 32;
            // salt
            final byte[] salt = generateRandomBytes(32);


            final byte[] derivedKey = scrypt(password.getBytes(), salt, ScryptN, ScryptR, ScryptP, ScryptDklen);

            // 128-bit initialisation vector for the cipher (16 bytes)
            final byte[] iv = generateRandomBytes(16);
            final byte[] privateKey = key.getPrivKeyBytes();
            final byte[] encryptKey = Arrays.copyOfRange(derivedKey, 0, 16);
            final byte[] cipherText = encryptAes(iv, encryptKey, privateKey);
            final byte[] mac = HashUtil.sha3(concat(Arrays.copyOfRange(derivedKey, 16, 32), cipherText));


            final Keystore keystore = new Keystore();
            keystore.address = Hex.toHexString(key.getAddress());
            keystore.id = UUID.randomUUID().toString();
            keystore.version = 3;
            keystore.crypto = new KeystoreCrypto();
            keystore.crypto.setKdf("scrypt");
            keystore.crypto.setMac(Hex.toHexString(mac));
            keystore.crypto.setCipher("aes-128-ctr");
            keystore.crypto.setCiphertext(Hex.toHexString(cipherText));
            keystore.crypto.setCipherparams(new CipherParams());
            keystore.crypto.getCipherparams().setIv(Hex.toHexString(iv));
            keystore.crypto.setKdfparams(new KdfParams());
            keystore.crypto.getKdfparams().setN(ScryptN);
            keystore.crypto.getKdfparams().setR(ScryptR);
            keystore.crypto.getKdfparams().setP(ScryptP);
            keystore.crypto.getKdfparams().setDklen(ScryptDklen);
            keystore.crypto.getKdfparams().setSalt(Hex.toHexString(salt));

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(file, keystore);
        } catch (Exception e) {
            log.error("Problem storing key", e);
            throw new RuntimeException("Problem storing key. Message: " + e.getMessage(), e);
        }
    }

    private static byte[] generateRandomBytes(int size) {
        final byte[] bytes = new byte[size];
        new Random().nextBytes(bytes);
        return bytes;
    }


    public static ECKey fromKeystore(final File file, final String password) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            final Keystore keystore = mapper.readValue(file, Keystore.class);
            final byte[] cipherKey;

            switch (keystore.getCrypto().getKdf()) {
                case "pbkdf2":
                    cipherKey = checkMacSha3(keystore, password);
                    break;
                case "scrypt":
                    cipherKey = checkMacScrypt(keystore, password);
                    break;
                default:
                    throw new RuntimeException("non valid algorithm " + keystore.getCrypto().getCipher());
            }

            byte[] privateKey = decryptAes(
                    Hex.decode(keystore.getCrypto().getCipherparams().getIv()),
                    cipherKey,
                    Hex.decode(keystore.getCrypto().getCiphertext())
            );

            return ECKey.fromPrivate(privateKey);
        } catch (Exception e) {
            throw new RuntimeException("Problem loading key. Message: " + e.getMessage(), e);
        }
    }

    private static byte[] decryptAes(byte[] iv, byte[] keyBytes, byte[] cipherText) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        return processAes(iv, keyBytes, cipherText, Cipher.DECRYPT_MODE);
    }

    private static byte[] encryptAes(byte[] iv, byte[] keyBytes, byte[] cipherText) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        return processAes(iv, keyBytes, cipherText, Cipher.ENCRYPT_MODE);
    }

    private static byte[] processAes(byte[] iv, byte[] keyBytes, byte[] cipherText, int encryptMode) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Mode
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

        cipher.init(encryptMode, key, ivSpec);
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
