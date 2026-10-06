# Painel administrativo privado

Acesse `https://api.zaluratech.com.br/admin`. Apenas o usuário configurado no servidor pode entrar. Não há cadastro público de usuários.

## Configuração

Configure no `.env` do Docker Compose (não versionar):

```
ADMIN_USERNAME=guilherme
ADMIN_PASSWORD_HASH='HASH_BCRYPT_AQUI'
```

Gere o hash BCrypt interativamente em uma máquina confiável, por exemplo com `htpasswd -nBC 12 admin` (pacote apache2-utils). Copie somente o trecho após `admin:`. Use aspas simples no `.env` para preservar os caracteres `$`. A senha não deve entrar no Git nem ser enviada ao chat.

Recrie o container após configurar as variáveis. A configuração de produção exige HTTPS; o cookie de sessão é Secure e HttpOnly, com expiração por inatividade de 30 minutos. Para desenvolvimento local HTTP apenas, use `ADMIN_COOKIE_SECURE=false`.

Sem nome e hash BCrypt válidos, nenhum usuário é criado e o painel fica fechado. O Compose exige ambas as variáveis antes de iniciar.

## Uso

1. Entrar e clicar em Cadastrar barbeiro.
2. Informar nome, telefones com país e DDD, horário básico e endereço opcional.
3. Gerar e copiar o link da linha do barbeiro. Ele expira em 30 minutos.

O cadastro inicial não configura serviços, pausas nem expediente semanal. Essas configurações continuam no fluxo existente. “Vínculo registrado” indica presença do identificador do WhatsApp, não um teste de saúde da integração.

O antigo POST `/api/meta/whatsapp/link/{barbeiroId}` agora exige sessão de administrador e CSRF. O curl antigo sem autenticação deixa de funcionar. O webhook continua público e validando assinatura; `/connect` e `/validar-token/{token}` mantêm a validação do token de conexão existente.

Antes de implantar: conferir CI, configurar credenciais, testar login, cadastro, geração do link e uma mensagem real do WhatsApp. Nenhuma alteração de produção é feita por este PR.
