# Encaminhamento de status para o disparo-service

O callback Meta permanece `https://api.zaluratech.com.br/webhook/whatsapp`.
Não substitua esse endereço nem remova a assinatura de mensagens do bot.
O recurso é desabilitado por padrão e não envia mensagens WhatsApp.

Após validar a assinatura Meta, o bot grava lotes com status da WABA e do remetente configurados em uma fila PostgreSQL, antes de responder 200. Um worker separado encaminha os bytes e o cabeçalho `X-Hub-Signature-256` originais. O processamento local de conversas/status continua. Se a gravação falhar, o callback retorna 503 para permitir nova entrega da Meta.

## Implantação, em etapas

1. Disponibilize o receiver do disparo-service por HTTPS, no caminho `/webhooks/whatsapp/status`. Um endereço localhost do computador de desenvolvimento não é acessível pela VPS; use uma implantação ou túnel HTTPS. Não altere o callback cadastrado na Meta.
2. No receiver configure `META_WEBHOOK_ENABLED=true`, `META_APP_SECRET` com o segredo do MESMO aplicativo Meta do bot, `META_WEBHOOK_VERIFY_TOKEN`, `META_COMPANY_ID`, `META_WABA_ID` e `META_PHONE_NUMBER_ID`. O app secret não é o access token. Guarde os segredos no ambiente, nunca no Git ou em mensagens. O envio (`META_SEND_ENABLED`) é independente e pode permanecer desligado.
3. Antes de habilitar o encaminhamento, execute a migração aditiva no banco do bot, com backup e credenciais habituais. Na VPS, após atualizar o código revisado:

   ```bash
   cd /opt/barbearia-git
   docker compose -p barbearia exec -T db psql -v ON_ERROR_STOP=1 -U barbearia -d barbearia < docs/sql/20261014-status-forwarding.sql
   ```

4. Configure no ambiente da api-barbearia:

   ```dotenv
   STATUS_FORWARDING_ENABLED=true
   STATUS_FORWARDING_URL=https://SEU-DESTINO/webhooks/whatsapp/status
   STATUS_FORWARDING_WABA_ID=1689716415460996
   STATUS_FORWARDING_PHONE_NUMBER_ID=1329007640298316
   ```

   O receiver deve usar os mesmos IDs e a empresa correta (no teste atual, `11111111-1111-1111-1111-111111111111`). Recrie o container da aplicação pelo procedimento habitual de deploy para carregar as variáveis. Não apague volumes. Essa implantação afeta o bot em produção e deve ocorrer em uma janela acompanhada.
5. Confira que o bot ainda recebe mensagens e que novos status chegam ao receiver. Para testar o fluxo completo, use uma nova campanha de teste autorizada; status anteriores à ativação não são importados automaticamente. Registros antigos de disparo sem remetente associado não devem receber um ID presumido.

## Operação e recuperação

Somente HTTP 200 confirma o encaminhamento. Todos os outros resultados, inclusive 403/404/5xx, conexão interrompida ou timeout, mantêm o lote pendente. Retry de 5 segundos até 5 minutos, sem limite de tentativas. HTTP tem timeout de 5 segundos; a reserva expira em 60 segundos após queda do processo. O receiver deve deduplicar entregas repetidas. Redirecionamentos não são seguidos. 403 costuma exigir verificar o app secret; 404, URL/ativação do receiver. Corrija a causa, sem apagar a fila.

A chamada HTTP ocorre fora da transação e em scheduler separado das tarefas do bot. A reserva é protegida contra workers concorrentes e conclusões de reservas antigas. A fila mantém somente um registro por hash do corpo original.

```sql
SELECT state, count(*) FROM status_forwarding_events
WHERE waba_id = '1689716415460996' AND phone_number_id = '1329007640298316'
GROUP BY state;

SELECT fingerprint, attempts, next_attempt_at, last_http_status, last_error
FROM status_forwarding_events
WHERE state = 'PENDING'
ORDER BY received_at LIMIT 20;
```

Enquanto pendente, o lote completo assinado fica no banco: pode conter mensagens e dados pessoais além dos status, pois alterar o JSON invalidaria a assinatura. Restrinja acesso ao banco/backups. Após 200, corpo e assinatura são apagados, mantendo o hash para deduplicação. Nenhum corpo, segredo ou resposta HTTP é registrado nos logs deste componente. Limite por lote selecionado: 1 MiB; configure também limites adequados no proxy.

Desabilitar a flag preserva pendências, mas deixa de capturar novos status. Reabilitar retoma pendências do escopo configurado. Trocar o destino encaminha pendências ao novo endereço: confirme o receiver e a empresa antes. Registros já encaminhados não são reenviados automaticamente. Falha após o receiver aceitar e antes de concluir a gravação pode provocar reentrega, resolvida pela deduplicação do receiver.
