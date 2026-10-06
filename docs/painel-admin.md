# Painel administrativo privado

O front é um HTML independente hospedado em `https://zaluratech.com.br/painel.html`. Este repositório contém somente o backend. Apenas o usuário configurado no servidor pode entrar. Não há cadastro público de usuários.

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

## Contrato REST para o front

Todas as chamadas usam `credentials: include`. Buscar GET `/api/admin/csrf` antes do login e novamente depois dele. Enviar o token retornado no cabeçalho `X-CSRF-TOKEN` de cada POST.

- POST `/api/admin/login`: formulário URL encoded, `username` e `password`; retorna 204 ou 401.
- GET `/api/admin/session`: usuário autenticado ou 401.
- GET `/api/admin/barbeiros`: lista de DTOs, sem credenciais da Meta.
- POST `/api/admin/barbeiros`: JSON dos campos do cadastro; retorna 201 ou 400.
- POST `/api/admin/barbeiros/{id}/link`: JSON com `link`.
- POST `/api/admin/logout`: encerra sessão, retorna 204.

CORS permite apenas https://zaluratech.com.br e https://www.zaluratech.com.br, com credenciais. Hostinger e API devem estar em HTTPS nestes subdomínios do mesmo domínio; não usar domínio temporário para autenticação. A página não guarda senha nem token de sessão no localStorage.
