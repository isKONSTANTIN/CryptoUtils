package su.knrg.crypto.utils;

import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

public class SimpleECDHE {
    static byte[] iv = new SecureRandom().generateSeed(16);
    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static String keyToBase64(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey base64ToPublicKey(String text) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        KeyFactory keyFactory = KeyFactory.getInstance("ECDH", "BC");
        EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(text));

        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey base64ToPrivateKey(String text) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        KeyFactory keyFactory = KeyFactory.getInstance("ECDH", "BC");
        EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(text));

        return keyFactory.generatePrivate(keySpec);
    }

    public static KeyPair generateECKeys() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("brainpoolp256r1");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDH", "BC");
        keyPairGenerator.initialize(parameterSpec);

        return keyPairGenerator.generateKeyPair();
    }

    public static SecretKey generateSharedSecret(PrivateKey privateKey, PublicKey publicKey) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", "BC");
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(publicKey, true);

        return keyAgreement.generateSecret("AES");
    }

    public static byte[] encrypt(SecretKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        int ivOffset = iv.length + 1; // using only byte for array size

        byte[] cipherData = cipher.doFinal(data);
        byte[] cipherDataWithIv = new byte[cipherData.length + ivOffset];

        System.arraycopy(cipherData, 0, cipherDataWithIv, ivOffset, cipherData.length);
        cipherDataWithIv[0] = (byte)iv.length;
        System.arraycopy(iv, 0, cipherDataWithIv, 1, iv.length);

        return cipherDataWithIv;
    }

    public static byte[] decrypt(SecretKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        Key decryptionKey = new SecretKeySpec(key.getEncoded(), key.getAlgorithm());
        byte[] iv = new byte[data[0]];
        byte[] cipherData = new byte[data.length - data[0] - 1];

        System.arraycopy(data, 1, iv, 0, data[0]);
        System.arraycopy(data, data[0] + 1, cipherData, 0, cipherData.length);

        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");

        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, ivSpec);

        return cipher.doFinal(cipherData);
    }
}