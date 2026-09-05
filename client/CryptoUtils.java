package client;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import javax.net.ssl.*;

public class CryptoUtils {

    // ============================================================
    //  PONTO E — PEDIR CERTIFICADO AO SERVIDOR E GUARDAR NA KEYSTORE LOCAL
    // ============================================================
    // Chamado automaticamente quando o certificado de 'alias' não existe
    // na keystore local. Contacta o servidor via TLS, pede o certificado,
    // e adiciona-o à keystore do utilizador.
    public static void fetchCertFromServer(String host, int port,
                                           String alias,
                                           String keystorePath,
                                           char[] keystorePassword) throws Exception {

        // 1) Contactar o servidor via TLS
        SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket socket = sf.createSocket(host, port);

        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

        out.writeObject("GET_CERT");
        out.writeObject(alias);
        out.flush();

        String status = (String) in.readObject();
        if (!status.equals("OK")) {
            socket.close();
            throw new RuntimeException("Certificado de '" + alias + "' não encontrado no servidor.");
        }

        byte[] certBytes = (byte[]) in.readObject();
        socket.close();

        // 2) Reconstruir o certificado a partir dos bytes X.509
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));

        // 3) Abrir a keystore local do utilizador e adicionar o certificado
        KeyStore ks = KeyStore.getInstance("JKS");
        File ksFile = new File(keystorePath);
        if (ksFile.exists()) {
            try (FileInputStream fis = new FileInputStream(ksFile)) {
                ks.load(fis, keystorePassword);
            }
        } else {
            ks.load(null, keystorePassword);
        }

        ks.setCertificateEntry(alias, cert);

        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, keystorePassword);
        }

        System.out.println("Certificado de '" + alias + "' obtido do servidor e adicionado à keystore local.");
    }

    // ============================================================
    //  PONTO 3 — CIFRA HIBRIDA (AES/CBC + RSA)
    // ============================================================
    public static void encryptHybrid(String inputPath,
                                     String outputCifradoPath,
                                     String outputChavePath,
                                     String destinatario,
                                     String keystorePath,
                                     char[] keystorePassword) throws Exception {
        // Versão sem servidor — não faz fetch automático
        encryptHybrid(inputPath, outputCifradoPath, outputChavePath,
                      destinatario, keystorePath, keystorePassword, null, -1);
    }

    public static void encryptHybrid(String inputPath,
                                     String outputCifradoPath,
                                     String outputChavePath,
                                     String destinatario,
                                     String keystorePath,
                                     char[] keystorePassword,
                                     String host, int port) throws Exception {

        // 1) Abrir keystore e obter certificado do destinatário
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword);
        }

        Certificate certDest = ks.getCertificate(destinatario);

        // PONTO E — Se o certificado não existir localmente, pedir ao servidor
        if (certDest == null && host != null && port > 0) {
            fetchCertFromServer(host, port, destinatario, keystorePath, keystorePassword);
            // Recarregar a keystore após adicionar o novo certificado
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword);
            }
            certDest = ks.getCertificate(destinatario);
        }

        if (certDest == null)
            throw new RuntimeException("Certificado do destinatário '" + destinatario + "' não encontrado.");

        PublicKey pubDest = certDest.getPublicKey();

        // 2) Gerar chave AES
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey aesKey = kg.generateKey();

        // 3) Cifrar ficheiro com AES/CBC/PKCS5Padding
        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] iv = aesCipher.getIV();

        try (FileInputStream fis = new FileInputStream(inputPath);
             FileOutputStream fos = new FileOutputStream(outputCifradoPath)) {

            // Guardar IV no início do ficheiro
            fos.write(iv);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] out = aesCipher.update(buffer, 0, read);
                if (out != null) fos.write(out);
            }

            byte[] outFinal = aesCipher.doFinal();
            if (outFinal != null) fos.write(outFinal);
        }

        // 4) Cifrar chave AES com RSA
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubDest);
        byte[] chaveCifrada = rsaCipher.doFinal(aesKey.getEncoded());

        // 5) Guardar chave cifrada
        try (FileOutputStream fos = new FileOutputStream(outputChavePath)) {
            fos.write(chaveCifrada);
        }
    }

    // ============================================================
    //  PONTO 3 — DECIFRAR HIBRIDO
    // ============================================================
    public static void decryptHybrid(String inputCifradoPath,
                                     String inputChavePath,
                                     String outputDecifradoPath,
                                     String username,
                                     String keystorePath,
                                     char[] keystorePassword) throws Exception {

        // 1) Abrir keystore e obter chave privada
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword);
        }

        Key key = ks.getKey(username, keystorePassword);
        if (!(key instanceof PrivateKey))
            throw new RuntimeException("Chave privada não encontrada para " + username);

        PrivateKey priv = (PrivateKey) key;

        // 2) Ler chave AES cifrada
        byte[] chaveCifrada = java.nio.file.Files.readAllBytes(new File(inputChavePath).toPath());

        // 3) Decifrar chave AES com RSA
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, priv);
        byte[] chaveAES = rsaCipher.doFinal(chaveCifrada);

        SecretKey aesKey = new SecretKeySpec(chaveAES, "AES");

        // 4) Decifrar ficheiro com AES/CBC/PKCS5Padding
        try (FileInputStream fis = new FileInputStream(inputCifradoPath);
             FileOutputStream fos = new FileOutputStream(outputDecifradoPath)) {

            // Ler IV (primeiros 16 bytes)
            byte[] iv = new byte[16];
            fis.read(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] out = aesCipher.update(buffer, 0, read);
                if (out != null) fos.write(out);
            }

            byte[] outFinal = aesCipher.doFinal();
            if (outFinal != null) fos.write(outFinal);
        }
    }

    // ============================================================
    //  PONTO 5 — ASSINAR
    // ============================================================
    public static void signFileKeystore(String inputPath,
                                        String outputSignaturePath,
                                        String username,
                                        String keystorePath,
                                        char[] password) throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password);
        }

        Key key = ks.getKey(username, password);
        if (!(key instanceof PrivateKey))
            throw new RuntimeException("Chave privada não encontrada para " + username);

        PrivateKey priv = (PrivateKey) key;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);

        try (FileInputStream fis = new FileInputStream(inputPath)) {
            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                sig.update(buffer, 0, read);
            }
        }

        byte[] assinatura = sig.sign();

        try (FileOutputStream fos = new FileOutputStream(outputSignaturePath)) {
            fos.write(assinatura);
        }
    }

    // ============================================================
    //  PONTO 5 — VALIDAR ASSINATURA
    // ============================================================
    public static boolean verifyFileKeystore(String inputPath,
                                             String signaturePath,
                                             String quemAssinou,
                                             String keystorePath,
                                             char[] password) throws Exception {
        // Versão sem servidor — não faz fetch automático
        return verifyFileKeystore(inputPath, signaturePath, quemAssinou,
                                  keystorePath, password, null, -1);
    }

    public static boolean verifyFileKeystore(String inputPath,
                                             String signaturePath,
                                             String quemAssinou,
                                             String keystorePath,
                                             char[] password,
                                             String host, int port) throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password);
        }

        Certificate cert = ks.getCertificate(quemAssinou);

        // PONTO E — Se o certificado não existir localmente, pedir ao servidor
        if (cert == null && host != null && port > 0) {
            fetchCertFromServer(host, port, quemAssinou, keystorePath, password);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, password);
            }
            cert = ks.getCertificate(quemAssinou);
        }

        if (cert == null)
            throw new RuntimeException("Certificado de " + quemAssinou + " não encontrado.");

        PublicKey pub = cert.getPublicKey();

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pub);

        try (FileInputStream fis = new FileInputStream(inputPath)) {
            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                sig.update(buffer, 0, read);
            }
        }

        byte[] assinatura = java.nio.file.Files.readAllBytes(new File(signaturePath).toPath());

        return sig.verify(assinatura);
    }

    // ============================================================
    //  PONTO 7 — ENVELOPE SEGURO (CRIAR)
    // ============================================================
    public static void createEnvelope(String inputPath,
                                      String outputEnvelopePath,
                                      String outputChavePath,
                                      String destinatario,
                                      String keystorePath,
                                      char[] password) throws Exception {
        createEnvelope(inputPath, outputEnvelopePath, outputChavePath,
                       destinatario, keystorePath, password, null, -1);
    }

    public static void createEnvelope(String inputPath,
                                      String outputEnvelopePath,
                                      String outputChavePath,
                                      String destinatario,
                                      String keystorePath,
                                      char[] password,
                                      String host, int port) throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password);
        }

        Certificate certDest = ks.getCertificate(destinatario);

        // PONTO E — fetch automático se necessário
        if (certDest == null && host != null && port > 0) {
            fetchCertFromServer(host, port, destinatario, keystorePath, password);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, password);
            }
            certDest = ks.getCertificate(destinatario);
        }

        if (certDest == null)
            throw new RuntimeException("Certificado do destinatário '" + destinatario + "' não encontrado.");

        PublicKey pubDest = certDest.getPublicKey();

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey aesKey = kg.generateKey();

        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] iv = aesCipher.getIV();

        try (FileInputStream fis = new FileInputStream(inputPath);
             FileOutputStream fos = new FileOutputStream(outputEnvelopePath)) {

            fos.write(iv);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] out = aesCipher.update(buffer, 0, read);
                if (out != null) fos.write(out);
            }

            byte[] outFinal = aesCipher.doFinal();
            if (outFinal != null) fos.write(outFinal);
        }

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubDest);
        byte[] chaveCifrada = rsaCipher.doFinal(aesKey.getEncoded());

        try (FileOutputStream fos = new FileOutputStream(outputChavePath)) {
            fos.write(chaveCifrada);
        }
    }

    // ============================================================
    //  PONTO 7 — ENVELOPE SEGURO (ABRIR)
    // ============================================================
    public static void openEnvelope(String inputEnvelopePath,
                                    String inputChavePath,
                                    String outputDecifradoPath,
                                    String username,
                                    String keystorePath,
                                    char[] password) throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password);
        }

        Key key = ks.getKey(username, password);
        if (!(key instanceof PrivateKey))
            throw new RuntimeException("Chave privada não encontrada para " + username);

        PrivateKey priv = (PrivateKey) key;

        byte[] chaveCifrada = java.nio.file.Files.readAllBytes(new File(inputChavePath).toPath());

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.DECRYPT_MODE, priv);
        byte[] chaveAES = rsaCipher.doFinal(chaveCifrada);

        SecretKey aesKey = new SecretKeySpec(chaveAES, "AES");

        try (FileInputStream fis = new FileInputStream(inputEnvelopePath);
             FileOutputStream fos = new FileOutputStream(outputDecifradoPath)) {

            byte[] iv = new byte[16];
            fis.read(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] out = aesCipher.update(buffer, 0, read);
                if (out != null) fos.write(out);
            }

            byte[] outFinal = aesCipher.doFinal();
            if (outFinal != null) fos.write(outFinal);
        }
    }
}