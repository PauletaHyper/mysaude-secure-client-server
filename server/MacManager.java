package server;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.*;


public class MacManager {

    static final String MAC_FILE = "server_storage/mySaude.mac";

    // Chave derivada da password de MAC — guardada em memória durante a execução
    private SecretKey macKey;

    // ============================================================
    //  CONSTRUTOR — deriva a chave a partir da password de MAC
    // ============================================================

    public MacManager(String macPassword) {
        // Exatamente como indicado no enunciado e no FAQ:
        //   byte[] pass = "maria12".getBytes();
        //   SecretKey key = new SecretKeySpec(pass, "HmacSHA256");
        byte[] pass = macPassword.getBytes();
        this.macKey = new SecretKeySpec(pass, "HmacSHA256");
    }

    // ============================================================
    //  CALCULAR O MAC DO FICHEIRO
    // ============================================================
    // Lê o conteúdo do ficheiro "users" byte a byte e calcula o HMAC-SHA256.
    // Devolve o MAC em Base64.

    public String calcularMac(String usersFilePath) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(macKey);

        try (FileInputStream fis = new FileInputStream(usersFilePath)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                mac.update(buffer, 0, read);
            }
        }

        byte[] macBytes = mac.doFinal();
        return Base64.getEncoder().encodeToString(macBytes);
    }

    // ============================================================
    //  GUARDAR O MAC NO FICHEIRO mySaude.mac
    // ============================================================

    public void guardarMac(String usersFilePath) throws Exception {
        String macB64 = calcularMac(usersFilePath);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(MAC_FILE, false))) {
            bw.write(macB64);
        }
    }

    // ============================================================
    //  VERIFICAR O MAC
    // ============================================================
    // Lê o MAC guardado em mySaude.mac e compara com o MAC calculado do ficheiro "users".
    // Devolve true se os MACs coincidem, false caso contrário.
    // Lança exceção específica se o ficheiro MAC não existir.

    public boolean verificarMac(String usersFilePath) throws Exception {
        File macFile = new File(MAC_FILE);

        if (!macFile.exists()) {
            throw new FileNotFoundException("Ficheiro de MAC não encontrado: " + MAC_FILE);
        }

        // Ler o MAC guardado
        String macGuardadoB64;
        try (BufferedReader br = new BufferedReader(new FileReader(macFile))) {
            macGuardadoB64 = br.readLine();
        }

        if (macGuardadoB64 == null || macGuardadoB64.trim().isEmpty()) {
            return false;
        }

        // Calcular o MAC atual do ficheiro users
        String macAtualB64 = calcularMac(usersFilePath);

        // Comparar (MessageDigest.isEqual é resistente a timing attacks)
        byte[] macGuardado = Base64.getDecoder().decode(macGuardadoB64.trim());
        byte[] macAtual    = Base64.getDecoder().decode(macAtualB64);

        return java.security.MessageDigest.isEqual(macGuardado, macAtual);
    }

    // ============================================================
    //  VERIFICAR NO ARRANQUE (com terminação se MAC inválido)
    // ============================================================
    // Chamado uma vez no arranque do servidor.
    // Se o ficheiro users não existir ainda (servidor novo), inicializa o MAC.
    // Se o MAC estiver errado, imprime aviso e termina o processo.

    public void verificarArranque(String usersFilePath) {
        File usersFile = new File(usersFilePath);

        // Servidor novo: ainda não há ficheiro users nem MAC -> criar ficheiro vazio e MAC inicial
        if (!usersFile.exists()) {
            try {
                File dir = new File("server_storage");
                if (!dir.exists()) dir.mkdirs();
                usersFile.createNewFile();
                guardarMac(usersFilePath);
                System.out.println("Servidor novo: ficheiro 'users' e MAC inicializados.");
            } catch (Exception e) {
                System.out.println("Erro ao inicializar ficheiro users: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        // Servidor existente: verificar MAC
        try {
            boolean valido = verificarMac(usersFilePath);
            if (!valido) {
                System.out.println("AVISO DE SEGURANÇA: O MAC do ficheiro de passwords está INCORRETO!");
                System.out.println("O ficheiro 'users' pode ter sido adulterado. A terminar o servidor.");
                System.exit(1);
            }
            System.out.println("MAC do ficheiro 'users' verificado com sucesso.");
        } catch (FileNotFoundException e) {
            System.out.println("AVISO DE SEGURANÇA: Ficheiro de MAC (mySaude.mac) não encontrado!");
            System.out.println("A terminar o servidor por razões de segurança.");
            System.exit(1);
        } catch (Exception e) {
            System.out.println("Erro ao verificar MAC no arranque: " + e.getMessage());
            System.exit(1);
        }
    }

    // ============================================================
    //  VERIFICAR EM ACESSOS INTERMÉDIOS (leitura/escrita)
    // ============================================================
    // Chamado antes de cada acesso ao ficheiro users durante a execução.
    // Se o MAC estiver errado, lança exceção (o chamador deve tratar e recusar a operação).
    
    public void verificarAcesso(String usersFilePath) throws Exception {
        boolean valido = verificarMac(usersFilePath);
        if (!valido) {
            throw new SecurityException("MAC inválido: ficheiro 'users' pode ter sido adulterado!");
        }
    }
}