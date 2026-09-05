package client;

import java.io.*;
import java.security.*;

public class KeyUtils {

    // Gera um par de chaves RSA 2048 bits
    public static void generateKeys(String username) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        File dir = new File("keys");
        if (!dir.exists()) dir.mkdir();

        try (FileOutputStream fos = new FileOutputStream("keys/" + username + "_private.key")) {
            fos.write(kp.getPrivate().getEncoded());
        }

        try (FileOutputStream fos = new FileOutputStream("keys/" + username + "_public.key")) {
            fos.write(kp.getPublic().getEncoded());
        }

        System.out.println("Chaves RSA criadas para " + username);
    }

    // Carrega chave privada
    public static PrivateKey loadPrivateKey(String username) throws Exception {
        byte[] bytes = readAllBytes("keys/" + username + "_private.key");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(bytes));
    }

    // Carrega chave pública
    public static PublicKey loadPublicKey(String username) throws Exception {
        byte[] bytes = readAllBytes("keys/" + username + "_public.key");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new java.security.spec.X509EncodedKeySpec(bytes));
    }

    private static byte[] readAllBytes(String path) throws Exception {
        return java.nio.file.Files.readAllBytes(new File(path).toPath());
    }
}
