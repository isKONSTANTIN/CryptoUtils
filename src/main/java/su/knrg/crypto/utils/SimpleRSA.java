package su.knrg.crypto.utils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SimpleRSA {
    public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);

        return generator.generateKeyPair();
    }

    public static String keyToBase64(Key key) {
        return Base64.getEncoder().encodeToString(keyToBytes(key));
    }

    public static byte[] keyToBytes(Key key) {
        return key.getEncoded();
    }

    public static PublicKey base64ToPublicKey(String text) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return getPublicKey(Base64.getDecoder().decode(text));
    }

    public static PrivateKey base64ToPrivateKey(String text) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return getPrivateKey(Base64.getDecoder().decode(text));
    }

    public static PublicKey getPublicKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);

        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);

        return keyFactory.generatePrivate(keySpec);
    }

    public static byte[] encrypt(PublicKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, key);

        return encryptCipher.doFinal(data);
    }

    public static byte[] decrypt(PrivateKey key, byte[] eData) throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, key);

        return decryptCipher.doFinal(eData);
    }
}