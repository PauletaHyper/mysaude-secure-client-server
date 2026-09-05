package client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

public class MySaude {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Map<String, List<String>> options = parseArgs(args);

        if (options.containsKey("-e")) {
            if (!options.containsKey("-s") || !options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -s <host:port>, -u <username>, -p <password> ou -t <destinatario>");
                return;
            }

            String serverInfo = options.get("-s").get(0);
            String[] parts = serverInfo.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String destinatario = options.get("-t").get(0);

            for (String ficheiro : options.get("-e")) {
                // PONTO F — passa password para autenticação no servidor
                uploadFile(host, port, username, password, destinatario, ficheiro);
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 2 — RECEBER FICHEIROS SIMPLES (-r)
        // ---------------------------------------------------------
        if (options.containsKey("-r")) {
            if (!options.containsKey("-s") || !options.containsKey("-u") || !options.containsKey("-p")) {
                System.out.println("Erro: falta -s <host:port>, -u <username> ou -p <password>");
                return;
            }

            String serverInfo = options.get("-s").get(0);
            String[] parts = serverInfo.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);

            for (String ficheiro : options.get("-r")) {
                // PONTO F — passa password para autenticação no servidor
                downloadFile(host, port, username, password, ficheiro);
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 3 — CIFRAR -c LOCAL
        // ---------------------------------------------------------
        if (options.containsKey("-c")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <destinatario>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String destinatario = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-c")) {

                String outCifrado = ficheiro + ".cifrado";
                String outChave = ficheiro + ".chave." + destinatario;

                try {
                    CryptoUtils.encryptHybrid(ficheiro, outCifrado, outChave,
                                              destinatario, keystorePath, ksPass);

                    System.out.println("Cifrado: " + ficheiro + " -> " + outCifrado +
                                       " + " + outChave);

                } catch (Exception e) {
                    System.out.println("Erro a cifrar " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 3 — DECIFRAR -d LOCAL
        // ---------------------------------------------------------
        if (options.containsKey("-d")) {

            if (!options.containsKey("-u") || !options.containsKey("-p")) {
                System.out.println("Erro: falta -u <username> ou -p <password>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiroCifrado : options.get("-d")) {

                String base = ficheiroCifrado.replaceFirst("\\.cifrado$", "");
                String ficheiroChave = base + ".chave." + username;
                String outDecifrado = base + ".decifrado";

                try {
                    CryptoUtils.decryptHybrid(ficheiroCifrado, ficheiroChave, outDecifrado,
                                              username, keystorePath, ksPass);

                    System.out.println("Decifrado: " + ficheiroCifrado + " -> " + outDecifrado);

                } catch (Exception e) {
                    System.out.println("Erro a decifrar " + ficheiroCifrado + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 5 — ASSINAR -a LOCAL
        // ---------------------------------------------------------
        if (options.containsKey("-a")) {

            if (!options.containsKey("-u") || !options.containsKey("-p")) {
                System.out.println("Erro: falta -u <username> ou -p <password>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-a")) {

                String assinatura = ficheiro + ".assinatura." + username;

                try {
                    CryptoUtils.signFileKeystore(ficheiro, assinatura,
                                                 username, keystorePath, ksPass);

                    System.out.println("Assinado: " + ficheiro + " -> " + assinatura);

                } catch (Exception e) {
                    System.out.println("Erro a assinar " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 5 — VALIDAR ASSINATURA -v LOCAL
        // ---------------------------------------------------------
        if (options.containsKey("-v")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <quem_assinou>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String quemAssinou = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-v")) {

                String assinatura = ficheiro + ".assinatura." + quemAssinou;

                try {
                    boolean ok = CryptoUtils.verifyFileKeystore(ficheiro, assinatura,
                                                                 quemAssinou, keystorePath, ksPass);

                    if (ok)
                        System.out.println("Assinatura válida para " + ficheiro);
                    else
                        System.out.println("Assinatura INVALIDA para " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro a validar assinatura de " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  A PARTIR DAQUI, TODAS AS OPERAÇOES USAM SERVIDOR
        // ---------------------------------------------------------
        if (!options.containsKey("-s")) {
            System.out.println("Erro: falta -s <endereco:porto>");
            return;
        }

        String serverInfo = options.get("-s").get(0);
        String[] parts = serverInfo.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        // ---------------------------------------------------------
        //  PONTO 4 — CIFRAR + ENVIAR -ce
        // ---------------------------------------------------------
        if (options.containsKey("-ce")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <destinatario>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String destinatario = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-ce")) {

                String cifrado = ficheiro + ".cifrado";
                String chave = ficheiro + ".chave." + destinatario;

                try {
                    // PONTO E — passa host e port para fetch automático de certificado
                    CryptoUtils.encryptHybrid(ficheiro, cifrado, chave,
                                              destinatario, keystorePath, ksPass,
                                              host, port);

                    uploadFile(host, port, username, password, destinatario, cifrado);
                    uploadFile(host, port, username, password, destinatario, chave);

                    System.out.println("Enviado (ce): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -ce para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 4 — RECEBER + DECIFRAR -rd
        // ---------------------------------------------------------
        if (options.containsKey("-rd")) {

            if (!options.containsKey("-u") || !options.containsKey("-p")) {
                System.out.println("Erro: falta -u <username> ou -p <password>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-rd")) {

                String cifrado = ficheiro + ".cifrado";
                String chave = ficheiro + ".chave." + username;
                String decifrado = ficheiro + ".decifrado";

                try {
                    if (!downloadFile(host, port, username, password, cifrado)) continue;
                    if (!downloadFile(host, port, username, password, chave)) continue;

                    CryptoUtils.decryptHybrid("recebido_" + cifrado,
                                              "recebido_" + chave,
                                              decifrado,
                                              username, keystorePath, ksPass);

                    System.out.println("Recebido e decifrado (rd): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -rd para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 6 — ASSINAR + CIFRAR + ENVIAR  -ae
        // ---------------------------------------------------------
        if (options.containsKey("-ae")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <destinatario>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String destinatario = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-ae")) {

                String assinatura = ficheiro + ".assinatura." + username;
                String cifrado = ficheiro + ".cifrado";
                String chave = ficheiro + ".chave." + destinatario;

                try {
                    // 1. Assinar o ficheiro original
                    CryptoUtils.signFileKeystore(
                        ficheiro,
                        assinatura,
                        username,
                        keystorePath,
                        ksPass
                    );

                    // 2. Cifrar o ficheiro (igual ao -ce) — PONTO E: fetch automático
                    CryptoUtils.encryptHybrid(
                        ficheiro,
                        cifrado,
                        chave,
                        destinatario,
                        keystorePath,
                        ksPass,
                        host, port
                    );

                    // 3. Enviar os 3 ficheiros
                    uploadFile(host, port, username, password, destinatario, cifrado);
                    uploadFile(host, port, username, password, destinatario, chave);
                    uploadFile(host, port, username, password, destinatario, assinatura);

                    System.out.println("Enviado (ae): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -ae para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 6 — RECEBER + DECIFRAR + VALIDAR  -rv
        // ---------------------------------------------------------
        if (options.containsKey("-rv")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <quem_assinou>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String quemAssinou = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-rv")) {

                String cifrado = ficheiro + ".cifrado";
                String chave = ficheiro + ".chave." + username;
                String assinatura = ficheiro + ".assinatura." + quemAssinou;

                try {
                    // 1. Fazer download dos 3 ficheiros
                    if (!downloadFile(host, port, username, password, cifrado)) continue;
                    if (!downloadFile(host, port, username, password, chave)) continue;
                    if (!downloadFile(host, port, username, password, assinatura)) continue;

                    // 2. Decifrar o ficheiro
                    String decifrado = ficheiro + ".decifrado";
                    CryptoUtils.decryptHybrid(
                        "recebido_" + cifrado,
                        "recebido_" + chave,
                        decifrado,
                        username,
                        keystorePath,
                        ksPass
                    );

                    // 3. Validar a assinatura — PONTO E: fetch automático do certificado
                    boolean ok = CryptoUtils.verifyFileKeystore(
                        decifrado,
                        "recebido_" + assinatura,
                        quemAssinou,
                        keystorePath,
                        ksPass,
                        host, port
                    );

                    if (ok)
                        System.out.println("Assinatura válida (rv): " + ficheiro);
                    else
                        System.out.println("Assinatura INVALIDA (rv): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -rv para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }


        // ---------------------------------------------------------
        //  PONTO 7 — ENVELOPE SEGURO: ASSINAR + CIFRAR + ENVIAR -ace
        // ---------------------------------------------------------
        if (options.containsKey("-ace")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <destinatario>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String destinatario = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-ace")) {

                String envelope = ficheiro + ".envelope";
                String chave = ficheiro + ".chave." + destinatario;
                String assinatura = ficheiro + ".assinatura." + username;

                try {
                    CryptoUtils.signFileKeystore(ficheiro, assinatura,
                                                 username, keystorePath, ksPass);

                    // PONTO E — fetch automático do certificado do destinatário
                    CryptoUtils.createEnvelope(ficheiro, envelope, chave,
                                               destinatario, keystorePath, ksPass,
                                               host, port);

                    if (!uploadFile(host, port, username, password, destinatario, envelope)) return;
                    if (!uploadFile(host, port, username, password, destinatario, chave)) return;
                    if (!uploadFile(host, port, username, password, destinatario, assinatura)) return;

                    System.out.println("Enviado (ace): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -ace para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        // ---------------------------------------------------------
        //  PONTO 7 — ENVELOPE SEGURO: RECEBER + DECIFRAR + VALIDAR -rdv
        // ---------------------------------------------------------
        if (options.containsKey("-rdv")) {

            if (!options.containsKey("-u") || !options.containsKey("-p") || !options.containsKey("-t")) {
                System.out.println("Erro: falta -u <username>, -p <password> ou -t <quem_assinou>");
                return;
            }

            String username = options.get("-u").get(0);
            String password = options.get("-p").get(0);
            String quemAssinou = options.get("-t").get(0);

            String keystorePath = "keystore." + username;
            char[] ksPass = password.toCharArray();

            for (String ficheiro : options.get("-rdv")) {

                String envelope = ficheiro + ".envelope";
                String chave = ficheiro + ".chave." + username;
                String assinatura = ficheiro + ".assinatura." + quemAssinou;
                String decifrado = ficheiro + ".decifrado";

                try {
                    if (!downloadFile(host, port, username, password, envelope)) continue;
                    if (!downloadFile(host, port, username, password, chave)) continue;
                    if (!downloadFile(host, port, username, password, assinatura)) continue;

                    CryptoUtils.openEnvelope("recebido_" + envelope,
                                             "recebido_" + chave,
                                             decifrado,
                                             username, keystorePath, ksPass);

                    boolean ok = CryptoUtils.verifyFileKeystore(
                        decifrado,
                        "recebido_" + assinatura,
                        quemAssinou,
                        keystorePath,
                        ksPass,
                        host, port
                    );

                    if (ok)
                        System.out.println("Envelope seguro válido (rdv): " + ficheiro);
                    else
                        System.out.println("Envelope seguro INVALIDO (rdv): " + ficheiro);

                } catch (Exception e) {
                    System.out.println("Erro em -rdv para " + ficheiro + ": " + e.getMessage());
                }
            }
            return;
        }

        System.out.println("Nenhuma operação válida foi especificada.");
    }

    // ============================================================
    //  FUNÇAO AUXILIAR — COPIAR FICHEIRO
    // ============================================================
    private static void copyFile(String src, String dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // ============================================================
    //  UPLOAD
    // ============================================================
    private static boolean uploadFile(String host, int port, String username, String password, String destinatario, String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("Erro: ficheiro " + filename + " não existe no cliente.");
                return false;
            }

            // PONTO D — Canal seguro TLS
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            Socket socket = sf.createSocket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("UPLOAD");
            out.writeObject(username);
            out.writeObject(password); // PONTO F — enviar password para autenticação no servidor
            out.writeObject(destinatario);
            out.writeObject(file.getName());
            out.writeObject(file.length());

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            fis.close();

            String resposta = (String) in.readObject();
            System.out.println("UPLOAD " + filename + ": " + resposta);

            socket.close();

            // RETORNA TRUE APENAS SE FOR "OK"
            return resposta.equals("OK");

        } catch (Exception e) {
            System.out.println("Erro no upload de " + filename + ": " + e.getMessage());
            return false;
        }
    }

    // ============================================================
    //  DOWNLOAD
    // ============================================================
    private static boolean downloadFile(String host, int port, String username, String password, String filename) {
        try {
            // PONTO D — Canal seguro TLS
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            Socket socket = sf.createSocket(host, port);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("DOWNLOAD");
            out.writeObject(username);
            out.writeObject(password); // PONTO F — enviar password para autenticação no servidor
            out.writeObject(filename);
            out.flush();

            String status = (String) in.readObject();

            if (status.equals("ERRO_AUTH")) {
                System.out.println("DOWNLOAD " + filename + ": Erro: autenticação falhou.");
                socket.close();
                return false;
            }

            if (status.equals("NOT_FOUND")) {
                System.out.println("DOWNLOAD " + filename + ": ficheiro não encontrado no servidor.");
                socket.close();
                return false;
            }

            String nomeReal = (String) in.readObject();
            long size = (long) in.readObject();

            File destino = new File("recebido_" + nomeReal);
            if (destino.exists()) {
                System.out.println("DOWNLOAD " + nomeReal + ": Erro: o ficheiro já existe localmente (recebido_" + nomeReal + ").");
                socket.close();
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(destino)) {
                byte[] buffer = new byte[4096];
                long remaining = size;

                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        throw new IOException("Ficheiro recebido incompleto (read = -1).");
                    }
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            socket.close();

            System.out.println("DOWNLOAD " + nomeReal + ": OK");
            return true;

        } catch (Exception e) {
            System.out.println("Erro no download de " + filename + ": " + e.getMessage());
            return false;
        }
    }



    // ============================================================
    //  PARSER DE ARGUMENTOS
    // ============================================================
    private static Map<String, List<String>> parseArgs(String[] args) {
        Map<String, List<String>> map = new HashMap<>();
        String current = null;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                current = arg;
                map.putIfAbsent(current, new ArrayList<>());
            } else if (current != null) {
                map.get(current).add(arg);
            }
        }
        return map;
    }
}