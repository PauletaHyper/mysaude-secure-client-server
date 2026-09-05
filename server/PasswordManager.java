package server;

import java.io.*;
import java.security.*;
import java.util.*;


public class PasswordManager {

    // Ficheiro onde são guardados os utilizadores
    static final String USERS_FILE = "server_storage/users";

    // Funções válidas do sistema
    private static final Set<String> FUNCOES_VALIDAS = new HashSet<>(Arrays.asList("medico", "utente"));

    // ============================================================
    //  GERAR HASH COM SALT  (SHA-256)
    // ============================================================
    // Recebe a password em texto limpo e devolve um array de 2 posições:
    //   [0] -> salt em Base64
    //   [1] -> síntese(salt || password) em Base64

    public static String[] hashPassword(String password) throws Exception {
        // 1) Gerar salt aleatório de 16 bytes
        SecureRandom sr = new SecureRandom();
        byte[] saltBytes = new byte[16];
        sr.nextBytes(saltBytes);

        // 2) Calcular síntese: SHA-256(salt || password)
        byte[] digest = computeDigest(saltBytes, password);

        // 3) Codificar em Base64
        String saltB64   = Base64.getEncoder().encodeToString(saltBytes);
        String digestB64 = Base64.getEncoder().encodeToString(digest);

        return new String[]{saltB64, digestB64};
    }

    // ============================================================
    //  VERIFICAR PASSWORD
    // ============================================================
    // Recebe a password em texto limpo, o salt em Base64 e a síntese em Base64.
    // Devolve true se a password está correta.

    public static boolean verifyPassword(String password, String saltB64, String digestB64) throws Exception {
        byte[] saltBytes    = Base64.getDecoder().decode(saltB64);
        byte[] digestEsperado = Base64.getDecoder().decode(digestB64);
        byte[] digestCalculado = computeDigest(saltBytes, password);

        return MessageDigest.isEqual(digestCalculado, digestEsperado);
    }

    // ============================================================
    //  MÉTODO INTERNO: calcular SHA-256(salt || password)
    // ============================================================

    private static byte[] computeDigest(byte[] saltBytes, String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(saltBytes);                          // primeiro o salt
        md.update(password.getBytes("UTF-8"));         // depois a password
        return md.digest();
    }

    // ============================================================
    //  VERIFICAR SE UM UTILIZADOR EXISTE
    // ============================================================

    public static boolean userExists(String username) throws Exception {
        File f = new File(USERS_FILE);
        if (!f.exists()) return false;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(":");
                if (parts.length >= 1 && parts[0].equals(username)) return true;
            }
        }
        return false;
    }

    // ============================================================
    //  OBTER A FUNÇÃO DO UTILIZADOR
    // ============================================================
    // Devolve "medico", "utente" ou null se o utilizador não existir.

    public static String getFuncao(String username) throws Exception {
        File f = new File(USERS_FILE);
        if (!f.exists()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(":");
                if (parts.length >= 2 && parts[0].equals(username)) return parts[1];
            }
        }
        return null;
    }

    // ============================================================
    //  AUTENTICAR UTILIZADOR
    // ============================================================
    // Devolve true se o username existe e a password é correta.
    // Devolve false em qualquer outro caso (username não existe, password errada).

    public static boolean autenticar(String username, String password) throws Exception {
        File f = new File(USERS_FILE);
        if (!f.exists()) return false;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                // Formato: username:funcao:salt:digest
                String[] parts = line.split(":");
                if (parts.length < 4) continue;

                if (parts[0].equals(username)) {
                    String saltB64   = parts[2];
                    String digestB64 = parts[3];
                    return verifyPassword(password, saltB64, digestB64);
                }
            }
        }
        return false; // username não encontrado
    }

    // ============================================================
    //  ADICIONAR UTILIZADOR AO FICHEIRO
    // ============================================================
    // Escreve uma nova linha no ficheiro "users".
    // ATENÇÃO: não verifica MAC aqui — essa responsabilidade é do MacManager (Ponto B).
    // ATENÇÃO: não verifica duplicados aqui — deve ser verificado antes de chamar.

    public static void adicionarUser(String username, String funcao, String password) throws Exception {
        // Validar função
        if (!FUNCOES_VALIDAS.contains(funcao)) {
            throw new IllegalArgumentException("Função inválida: " + funcao + ". Use 'medico' ou 'utente'.");
        }

        // Gerar hash + salt
        String[] hashInfo = hashPassword(password);
        String saltB64   = hashInfo[0];
        String digestB64 = hashInfo[1];

        // Garantir que a pasta server_storage existe
        File dir = new File("server_storage");
        if (!dir.exists()) dir.mkdirs();

        // Escrever linha no ficheiro (append)
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(USERS_FILE, true))) {
            bw.write(username + ":" + funcao + ":" + saltB64 + ":" + digestB64);
            bw.newLine();
        }
    }

    // ============================================================
    //  VALIDAR FUNÇÃO
    // ============================================================
    
    public static boolean funcaoValida(String funcao) {
        return FUNCOES_VALIDAS.contains(funcao);
    }
}