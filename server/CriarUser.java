package server;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;


public class CriarUser {

    // Keystore do servidor que guarda os certificados de todos os utilizadores
    static final String KEYSTORE_USERS      = "server_storage/keystore.users";
    static final String KEYSTORE_USERS_PASS = "mysaude"; // password fixa da keystore.users

    public static void main(String[] args) throws Exception {

        // -------------------------------------------------------
        //  1) VALIDAR ARGUMENTOS
        // -------------------------------------------------------
        // Formato esperado: <username> <funcao> <password> -f <ficheiro.cert>

        if (args.length != 5 || !args[3].equals("-f")) {
            System.out.println("Uso: java server.CriarUser <username> <funcao> <password> -f <ficheiro.cert>");
            return;
        }

        String username   = args[0];
        String funcao     = args[1];
        String password   = args[2];
        String certFile   = args[4];

        // -------------------------------------------------------
        //  2) VALIDAR FUNÇÃO
        // -------------------------------------------------------

        if (!PasswordManager.funcaoValida(funcao)) {
            System.out.println("Erro: função inválida '" + funcao + "'. Use 'medico' ou 'utente'.");
            return;
        }

        // -------------------------------------------------------
        //  3) PEDIR PASSWORD DE MAC
        // -------------------------------------------------------

        System.out.print("Introduza a password de MAC do servidor: ");
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        String macPassword = consoleReader.readLine();
        if (macPassword == null || macPassword.trim().isEmpty()) {
            System.out.println("Erro: password de MAC não pode ser vazia.");
            return;
        }

        MacManager macManager = new MacManager(macPassword);

        // -------------------------------------------------------
        //  4) GARANTIR QUE server_storage EXISTE
        // -------------------------------------------------------

        File serverStorage = new File("server_storage");
        if (!serverStorage.exists()) serverStorage.mkdirs();

        // -------------------------------------------------------
        //  5) VERIFICAR MAC ANTES DE QUALQUER ACESSO AO FICHEIRO users
        // -------------------------------------------------------

        File usersFile = new File(PasswordManager.USERS_FILE);

        if (!usersFile.exists()) {
            // Servidor novo: criar ficheiro users vazio e MAC inicial
            usersFile.createNewFile();
            macManager.guardarMac(PasswordManager.USERS_FILE);
            System.out.println("Ficheiro 'users' inicializado.");
        } else {
            // Verificar MAC — se inválido, recusa a operação
            try {
                macManager.verificarAcesso(PasswordManager.USERS_FILE);
            } catch (SecurityException e) {
                System.out.println("AVISO DE SEGURANÇA: " + e.getMessage());
                System.out.println("Operação cancelada.");
                return;
            } catch (FileNotFoundException e) {
                System.out.println("AVISO DE SEGURANÇA: Ficheiro de MAC não encontrado.");
                System.out.println("Operação cancelada.");
                return;
            }
        }

        // -------------------------------------------------------
        //  6) VERIFICAR SE O USERNAME JÁ EXISTE
        // -------------------------------------------------------

        if (PasswordManager.userExists(username)) {
            System.out.println("Erro: o utilizador '" + username + "' já existe.");
            return;
        }

        // -------------------------------------------------------
        //  7) VERIFICAR SE O FICHEIRO DE CERTIFICADO EXISTE
        // -------------------------------------------------------

        File certFileObj = new File(certFile);
        if (!certFileObj.exists()) {
            System.out.println("Erro: ficheiro de certificado não encontrado: " + certFile);
            return;
        }

        // -------------------------------------------------------
        //  8) ADICIONAR UTILIZADOR AO FICHEIRO users
        // -------------------------------------------------------

        PasswordManager.adicionarUser(username, funcao, password);
        System.out.println("Utilizador '" + username + "' adicionado ao ficheiro de passwords.");

        // -------------------------------------------------------
        //  9) ATUALIZAR O MAC APÓS ALTERAÇÃO DO FICHEIRO users
        // -------------------------------------------------------

        macManager.guardarMac(PasswordManager.USERS_FILE);
        System.out.println("MAC atualizado.");

        // -------------------------------------------------------
        //  10) CRIAR DIRETORIA DO UTILIZADOR EM server_storage/
        // -------------------------------------------------------

        File userDir = new File("server_storage/" + username);
        if (!userDir.exists()) {
            userDir.mkdirs();
            System.out.println("Diretoria criada: server_storage/" + username);
        }

        // -------------------------------------------------------
        //  11) ADICIONAR CERTIFICADO À keystore.users
        //      alias = username  (simplificação do enunciado)
        // -------------------------------------------------------

        adicionarCertificado(username, certFileObj);
        System.out.println("Certificado de '" + username + "' adicionado à keystore.users.");
        System.out.println("Utilizador '" + username + "' criado com sucesso.");
    }

    // ============================================================
    //  MÉTODO AUXILIAR — Adicionar certificado à keystore.users
    // ============================================================
    
    private static void adicionarCertificado(String username, File certFile) throws Exception {

        // 1) Carregar o certificado do ficheiro .cert
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (FileInputStream fis = new FileInputStream(certFile)) {
            cert = cf.generateCertificate(fis);
        }

        // 2) Carregar (ou criar) a keystore.users
        KeyStore ks = KeyStore.getInstance("JKS");
        File ksFile = new File(KEYSTORE_USERS);

        if (ksFile.exists()) {
            // Keystore já existe — carregar
            try (FileInputStream fis = new FileInputStream(ksFile)) {
                ks.load(fis, KEYSTORE_USERS_PASS.toCharArray());
            }
        } else {
            // Keystore nova — inicializar vazia
            ks.load(null, KEYSTORE_USERS_PASS.toCharArray());
        }

        // 3) Adicionar o certificado com alias = username
        //    (enunciado: "o username e o alias do certificado devem coincidir")
        ks.setCertificateEntry(username, cert);

        // 4) Guardar a keystore atualizada em disco
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, KEYSTORE_USERS_PASS.toCharArray());
        }
    }
}