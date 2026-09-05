# MySaude — Cliente/Servidor Seguro

Sistema cliente/servidor em Java para troca segura de ficheiros clínicos, desenvolvido como projeto de Segurança Informática (FCUL). Implementa autenticação, controlo de acesso por perfil, e um conjunto completo de operações criptográficas ponto-a-ponto — assinatura digital, cifra, e "envelope digital" (cifra + assinatura + troca de chave).

## Funcionalidades de segurança

- **Comunicação sobre TLS**, com keystores/certificados RSA-2048 por utilizador
- **Autenticação** de utilizadores com password
- **Controlo de acesso por função** (ex: apenas utilizadores com perfil "médico" podem enviar ficheiros)
- **Integridade do ficheiro de passwords**, verificada por **MAC** no arranque do servidor — o servidor recusa arrancar se o MAC estiver incorreto ou em falta
- **Assinatura digital** de ficheiros e verificação de assinatura
- **Cifra simétrica** de ficheiros para um destinatário específico
- **Envelope digital**: assinatura + cifra + troca segura da chave simétrica (cifrada com a chave pública do destinatário)
- **Distribuição automática de certificados**: se o cliente não tem o certificado de um destinatário, é obtido automaticamente do servidor

## Componentes

```
server/
├── MySaudeServer.java    → Servidor TLS, gestão de sessões e ficheiros
├── CriarUser.java         → Utilitário de criação de utilizadores
├── PasswordManager.java   → Autenticação e hashing de passwords
└── MacManager.java        → Verificação de integridade (MAC) do ficheiro de utilizadores

client/
├── MySaude.java     → CLI do cliente (assinar, cifrar, enviar, receber, verificar)
├── CryptoUtils.java → Operações criptográficas (RSA, AES, assinatura)
└── KeyUtils.java    → Gestão de keystores e chaves
```

## Como correr

Guia completo de setup e demonstração passo-a-passo (3 máquinas: servidor + 2 clientes) em [`Relatorio.txt`](./Relatorio.txt) e [`Readme.txt`](./Readme.txt), incluindo:
- geração de keystores RSA e certificados
- arranque do servidor e criação de utilizadores
- todos os fluxos: assinar, cifrar, enviar, receber, verificar, envelope digital
- demonstração de controlo de acesso e deteção de adulteração do ficheiro de passwords

## Stack

Java · TLS/SSL · JCA (Java Cryptography Architecture) · RSA · AES · Assinaturas digitais
