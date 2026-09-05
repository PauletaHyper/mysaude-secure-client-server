Reparei que quando zipo a pasta do projeto a pasta .vscode com o ficheiro settings.json desaparece 
como não encontrei nenhuma solução para este problema deixo aqui o conteudo do mesmo ficheiro:

{
	"folders": [
		{
			"path": "."
		}
	],
	"settings": {
		"java.project.sourcePaths": [
			"."
		]
	}
}

================================================================================
GUIA PASSO A PASSO SEM SCRIPTS - 3 MAQUINAS
================================================================================

NOTAS IMPORTANTES ANTES DE COMECAR:

1. Substituir IP_SERVIDOR pelo IP real do servidor (correr "ipconfig" no servidor).

2. O servidor usa a porta 8080 e fica BLOQUEADO no terminal — abrir um terminal
   separado para os comandos do servidor depois de o arrancar.

3. As flags TLS (-Djavax...) podem partir se copiadas com quebras de linha.
   Usar a variavel de ambiente UMA VEZ no inicio de cada sessao do cliente:
       Linux/Mac: export _JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"
       PowerShell: $env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"
   Depois todos os comandos java ja incluem as flags automaticamente.

4. UNICIDADE: o servidor nao aceita duas versoes do mesmo ficheiro base.
   Se enviares "teste.txt" com -e, nao podes depois enviar "teste.txt.cifrado"
   com -ce. Por isso cada operacao usa um ficheiro diferente neste guia.


================================================================================
TERMINAL 1 — PC SERVIDOR (setup completo)
================================================================================

--- PASSO 1: Compilar ---

javac -encoding UTF-8 server/PasswordManager.java server/MacManager.java server/CriarUser.java server/MySaudeServer.java client/KeyUtils.java client/CryptoUtils.java client/MySaude.java


--- PASSO 2: Criar keystores RSA-2048 para todos os utilizadores ---

keytool -genkeypair -alias afonso    -keyalg RSA -keysize 2048 -storetype JKS -keystore keystore.afonso    -validity 365 -storepass 123456 -keypass 123456 -dname "CN=afonso"
keytool -genkeypair -alias lima      -keyalg RSA -keysize 2048 -storetype JKS -keystore keystore.lima      -validity 365 -storepass 123456 -keypass 123456 -dname "CN=lima"
keytool -genkeypair -alias duarte    -keyalg RSA -keysize 2048 -storetype JKS -keystore keystore.duarte    -validity 365 -storepass 123456 -keypass 123456 -dname "CN=duarte"
keytool -genkeypair -alias alexandre -keyalg RSA -keysize 2048 -storetype JKS -keystore keystore.alexandre -validity 365 -storepass 123456 -keypass 123456 -dname "CN=alexandre"


--- PASSO 3: Exportar certificados ---

keytool -exportcert -alias afonso    -keystore keystore.afonso    -file afonso.cer    -storepass 123456
keytool -exportcert -alias lima      -keystore keystore.lima      -file lima.cer      -storepass 123456
keytool -exportcert -alias duarte    -keystore keystore.duarte    -file duarte.cer    -storepass 123456
keytool -exportcert -alias alexandre -keystore keystore.alexandre -file alexandre.cer -storepass 123456


--- PASSO 4: Importar relacoes de confianca ---

keytool -importcert -alias lima   -file lima.cer   -keystore keystore.afonso    -storepass 123456 -noprompt
keytool -importcert -alias duarte -file duarte.cer -keystore keystore.afonso    -storepass 123456 -noprompt
keytool -importcert -alias afonso -file afonso.cer -keystore keystore.lima      -storepass 123456 -noprompt
keytool -importcert -alias afonso -file afonso.cer -keystore keystore.duarte    -storepass 123456 -noprompt
keytool -importcert -alias afonso -file afonso.cer -keystore keystore.alexandre -storepass 123456 -noprompt

    NOTA: Lima NAO importa o cert de Duarte de proposito.
          O cliente vai busca-lo ao servidor automaticamente quando necessario (Ponto E).


--- PASSO 5: Criar utilizadores ---
    (cada comando pede a password de MAC — escrever: macpassword123)

echo macpassword123 | java server.CriarUser afonso medico 123456 -f afonso.cer
echo macpassword123 | java server.CriarUser lima   medico 123456 -f lima.cer
echo macpassword123 | java server.CriarUser duarte medico 123456 -f duarte.cer
echo macpassword123 | java server.CriarUser bob    utente 123456 -f alexandre.cer

    Verificar ficheiro criado (deve mostrar formato username:funcao:salt:digest):
    cat server_storage/users
    (Windows PowerShell: Get-Content server_storage/users)

    Ver ficheiro MAC em base64 (Ponto B — prova de codificacao base64):
    cat server_storage/mySaude.mac
    (Windows PowerShell: Get-Content server_storage/mySaude.mac)

    Verificar certificados na keystore do servidor (Ponto E.1):
    keytool -list -keystore server_storage/keystore.users -storepass mysaude
    (deve listar 4 entradas: afonso, lima, duarte, bob)


--- PASSO 6: Copiar para os PCs cliente (pen drive) ---

    Para PC CLIENTE 1 (Lima):   keystore.afonso  keystore.lima  pasta client/
    Para PC CLIENTE 2 (Duarte): keystore.afonso  keystore.duarte  pasta client/


--- PASSO 7: Arrancar o servidor (fica bloqueado neste terminal) ---
    (pede a password de MAC — escrever: macpassword123)

echo macpassword123 | java server.MySaudeServer 8080 keystore.afonso 123456

    Resultado esperado:
    MAC do ficheiro 'users' verificado com sucesso.
    Servidor MySaude (TLS) a correr no porto 8080


================================================================================
TERMINAL 2 — PC CLIENTE 1 (Lima)
================================================================================

PRE-REQUISITO: ter na pasta keystore.afonso, keystore.lima e pasta client/


--- PASSO 1: Compilar ---

javac -encoding UTF-8 client/KeyUtils.java client/CryptoUtils.java client/MySaude.java


--- PASSO 2: Definir flags TLS (uma unica vez no inicio da sessao) ---

    Linux/Mac:
    export _JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"

    Windows PowerShell:
    $env:_JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"

    Windows CMD:
    set _JAVA_OPTIONS=-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456


--- PASSO 3: Criar ficheiros de teste ---

    Linux/Mac:
    echo "documento simples de lima" > teste_simples.txt
    echo "documento cifrado de lima" > teste_cifrado.txt
    echo "documento assinado de lima" > teste_assinado.txt
    echo "documento envelope de lima" > teste_envelope.txt

    Windows PowerShell:
    "documento simples de lima" | Out-File -Encoding utf8 teste_simples.txt
    "documento cifrado de lima" | Out-File -Encoding utf8 teste_cifrado.txt
    "documento assinado de lima" | Out-File -Encoding utf8 teste_assinado.txt
    "documento envelope de lima" | Out-File -Encoding utf8 teste_envelope.txt

    Windows CMD:
    echo documento simples de lima > teste_simples.txt
    echo documento cifrado de lima > teste_cifrado.txt
    echo documento assinado de lima > teste_assinado.txt
    echo documento envelope de lima > teste_envelope.txt


--- PASSO 4: Operacoes locais (sem servidor) ---

    Assinar (-a):
    java client.MySaude -u lima -p 123456 -a teste_simples.txt
    (cria: teste_simples.txt.assinatura.lima)

    Verificar assinatura propria (-v):
    java client.MySaude -u lima -p 123456 -t lima -v teste_simples.txt
    (esperado: Assinatura valida para teste_simples.txt)

    Cifrar para si propria (-c) e decifrar (-d):
    java client.MySaude -u lima -p 123456 -t lima -c teste_simples.txt
    java client.MySaude -u lima -p 123456 -d teste_simples.txt.cifrado
    (cria: teste_simples.txt.decifrado — conteudo identico ao original)


--- PASSO 5: Enviar ficheiro simples para Duarte (-e) ---

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -e teste_simples.txt
(esperado: UPLOAD teste_simples.txt: OK)


--- PASSO 6: Cifrar e enviar para Duarte (-ce) ---
    Lima nao tem o cert de Duarte — vai busca-lo ao servidor automaticamente (Ponto E)

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -ce teste_cifrado.txt
(esperado: "Certificado de 'duarte' obtido do servidor..." + UPLOAD OK)


--- PASSO 7: Assinar, cifrar e enviar para Duarte (-ae) ---

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -ae teste_assinado.txt
(esperado: 3 uploads OK — .cifrado, .chave.duarte, .assinatura.lima)


--- PASSO 8: Envelope seguro para Duarte (-ace) ---

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -ace teste_envelope.txt
(esperado: 3 uploads OK — .envelope, .chave.duarte, .assinatura.lima)


--- PASSO 9: Receber ficheiro simples enviado por Duarte (-r) ---
    (ESPERAR que Duarte execute o Passo 5 do Terminal 3 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -r teste_simples.txt
(cria: recebido_teste_simples.txt)


--- PASSO 10: Receber e decifrar o que Duarte enviou (-rd) ---
    (ESPERAR que Duarte execute o Passo 6 do Terminal 3 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -rd teste_cifrado.txt
(cria: teste_cifrado.txt.decifrado)


--- PASSO 11: Receber, decifrar e verificar assinatura de Duarte (-rv) ---
    (ESPERAR que Duarte execute o Passo 7 do Terminal 3 primeiro)
    Lima nao tem o cert de Duarte — vai busca-lo ao servidor automaticamente (Ponto E)

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -rv teste_assinado.txt
(esperado: "Certificado de 'duarte' obtido do servidor..." + "Assinatura valida (rv)")


--- PASSO 12: Abrir envelope seguro de Duarte (-rdv) ---
    (ESPERAR que Duarte execute o Passo 8 do Terminal 3 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p 123456 -t duarte -rdv teste_envelope.txt
(esperado: Envelope seguro valido (rdv))


--- PASSO 13: Demonstrar controlo de acesso (bob e utente, nao pode enviar) ---

java client.MySaude -s IP_SERVIDOR:8080 -u bob -p 123456 -t duarte -e teste_simples.txt
(esperado: ERRO — Acesso negado. Apenas utilizadores com funcao 'medico' podem enviar)


--- PASSO 14: Demonstrar autenticacao com password errada ---

java client.MySaude -s IP_SERVIDOR:8080 -u lima -p ERRADA -t duarte -e teste_simples.txt
(esperado: ERRO — Autenticacao falhou para o utilizador 'lima')


================================================================================
TERMINAL 3 — PC CLIENTE 2 (Duarte)
================================================================================

PRE-REQUISITO: ter na pasta keystore.afonso, keystore.duarte e pasta client/


--- PASSO 1: Compilar ---

javac -encoding UTF-8 client/KeyUtils.java client/CryptoUtils.java client/MySaude.java


--- PASSO 2: Definir flags TLS (uma unica vez no inicio da sessao) ---

    Linux/Mac:
    export _JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"

    Windows PowerShell:
    $env:_JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456"

    Windows CMD:
    set _JAVA_OPTIONS=-Djavax.net.ssl.trustStore=keystore.afonso -Djavax.net.ssl.trustStorePassword=123456


--- PASSO 3: Criar ficheiros de teste ---

    Linux/Mac:
    echo "documento simples de duarte" > teste_simples.txt
    echo "documento cifrado de duarte" > teste_cifrado.txt
    echo "documento assinado de duarte" > teste_assinado.txt
    echo "documento envelope de duarte" > teste_envelope.txt

    Windows PowerShell:
    "documento simples de duarte" | Out-File -Encoding utf8 teste_simples.txt
    "documento cifrado de duarte" | Out-File -Encoding utf8 teste_cifrado.txt
    "documento assinado de duarte" | Out-File -Encoding utf8 teste_assinado.txt
    "documento envelope de duarte" | Out-File -Encoding utf8 teste_envelope.txt

    Windows CMD:
    echo documento simples de duarte > teste_simples.txt
    echo documento cifrado de duarte > teste_cifrado.txt
    echo documento assinado de duarte > teste_assinado.txt
    echo documento envelope de duarte > teste_envelope.txt


--- PASSO 4: Receber ficheiro simples enviado por Lima (-r) ---
    (ESPERAR que Lima execute o Passo 5 do Terminal 2 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -r teste_simples.txt
(cria: recebido_teste_simples.txt)


--- PASSO 5: Enviar ficheiro simples para Lima (-e) ---

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -e teste_simples.txt
(esperado: UPLOAD teste_simples.txt: OK)


--- PASSO 6: Receber e decifrar o que Lima enviou (-rd) ---
    (ESPERAR que Lima execute o Passo 6 do Terminal 2 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -rd teste_cifrado.txt
(cria: teste_cifrado.txt.decifrado)


--- PASSO 7: Receber, decifrar e verificar assinatura de Lima (-rv) ---
    (ESPERAR que Lima execute o Passo 7 do Terminal 2 primeiro)
    Duarte nao tem o cert de Lima — vai busca-lo ao servidor automaticamente (Ponto E)

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -rv teste_assinado.txt
(esperado: "Certificado de 'lima' obtido do servidor..." + "Assinatura valida (rv)")


--- PASSO 8: Abrir envelope seguro de Lima (-rdv) ---
    (ESPERAR que Lima execute o Passo 8 do Terminal 2 primeiro)

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -rdv teste_envelope.txt
(esperado: Envelope seguro valido (rdv))


--- PASSO 9: Cifrar e enviar para Lima (-ce) ---
    Duarte nao tem o cert de Lima — vai busca-lo ao servidor automaticamente (Ponto E)

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -ce teste_cifrado.txt
(esperado: "Certificado de 'lima' obtido do servidor..." + UPLOAD OK)


--- PASSO 10: Assinar, cifrar e enviar para Lima (-ae) ---

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -ae teste_assinado.txt
(esperado: 3 uploads OK)


--- PASSO 11: Envelope seguro para Lima (-ace) ---

java client.MySaude -s IP_SERVIDOR:8080 -u duarte -p 123456 -t lima -ace teste_envelope.txt
(esperado: 3 uploads OK)


--- PASSO 12: Operacoes locais (sem servidor) ---

    Assinar (-a):
    java client.MySaude -u duarte -p 123456 -a teste_simples.txt

    Cifrar para si proprio (-c) e decifrar (-d):
    java client.MySaude -u duarte -p 123456 -t duarte -c teste_simples.txt
    java client.MySaude -u duarte -p 123456 -d teste_simples.txt.cifrado


================================================================================
DEMONSTRACAO DOS PONTOS A/B/C (correr no PC SERVIDOR, servidor ja a correr)
================================================================================

--- Ponto A: Ver ficheiro de passwords ---

cat server_storage/users
(Windows PowerShell: Get-Content server_storage/users)
(deve mostrar: username:funcao:salt:sintese)


--- Ponto B: MAC errado no arranque (servidor deve terminar imediatamente) ---
(porta 9999 apenas para demonstracao, nao afeta o servidor real na 8080)

echo MAC_ERRADA | java server.MySaudeServer 9999 keystore.afonso 123456
(esperado: AVISO DE SEGURANCA: O MAC do ficheiro de passwords esta INCORRETO!
           O ficheiro 'users' pode ter sido adulterado. A terminar o servidor.)


--- Ponto B: MAC inexistente no arranque (servidor deve terminar imediatamente) ---
(renomear o ficheiro MAC temporariamente para simular ausencia)

    Linux/Mac:
    mv server_storage/mySaude.mac server_storage/mySaude.mac.bak
    echo MAC_QUALQUER | java server.MySaudeServer 9999 keystore.afonso 123456
    mv server_storage/mySaude.mac.bak server_storage/mySaude.mac

    Windows PowerShell:
    Rename-Item server_storage\mySaude.mac server_storage\mySaude.mac.bak
    echo MAC_QUALQUER | java server.MySaudeServer 9999 keystore.afonso 123456
    Rename-Item server_storage\mySaude.mac.bak server_storage\mySaude.mac

(esperado: AVISO DE SEGURANCA: Ficheiro de MAC (mySaude.mac) nao encontrado!
           A terminar o servidor por razoes de seguranca.)


--- Ponto C: Username ja existente (deve dar erro) ---

echo macpassword123 | java server.CriarUser afonso medico 123456 -f afonso.cer
(esperado: Erro: o utilizador 'afonso' ja existe.)


--- Ponto C: Funcao invalida (deve dar erro) ---

java server.CriarUser novo enfermeiro 123456 -f afonso.cer
(esperado: Erro: funcao invalida 'enfermeiro'. Use 'medico' ou 'utente'.)
