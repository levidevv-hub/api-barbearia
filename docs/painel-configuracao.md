# Horários, localização e exclusão no painel

O HTML de referência fica em `docs/frontend/painel-zalura.html`. Ele usa a API de produção `https://api.zaluratech.com.br` com sessão administrativa e CSRF. O arquivo é instalado separadamente na Hostinger, em `/painel/index.html`; não é servido automaticamente pelo Spring.

## Instalação

1. Execute `./gradlew test` e publique a API com os novos controllers/services.
2. Guarde uma cópia do HTML atual e substitua o arquivo servido em `/painel/` pelo HTML desta PR.
3. Entre no painel e clique em **Configurar** no barbeiro.
4. Defina os dias abertos e períodos. Exemplo de almoço: 08:00–12:00 e 13:30–19:00.
5. Informe endereço, latitude e longitude. Salve e reabra a configuração para conferir. O botão de localização do dispositivo só deve ser usado estando no estabelecimento.
6. Faça um agendamento de teste pelo WhatsApp para verificar disponibilidade e localização.

Os campos de início/fim gerais do cadastro são um resumo legado. Um barbeiro recém-cadastrado precisa da configuração semanal para disponibilizar horários. O botão **Fechar todos os dias** preenche a semana como fechada; ainda é necessário salvar.

## Regras

- Sete dias obrigatórios, sem repetição. Dias fechados não recebem períodos.
- Até 12 períodos por dia, sem sobreposição ou virada de dia. Intervalos entre períodos representam almoço/pausas.
- Endereço é texto livre; não calcula coordenadas. Latitude de -90 a 90; longitude de -180 a 180. Informe ou remova as duas juntas.
- A atualização de agenda e localização é transacional e usa a mesma trava por barbeiro do agendamento.
- Reservas já confirmadas não são canceladas nem remarcadas quando o expediente muda.
- Bloqueios fecham o dia inteiro e continuam valendo após editar a semana. Datas com reservas futuras não são bloqueadas por esta tela; não há cancelamento automático nem envio de mensagens.
- A exclusão exige digitar o nome exato. Recusa qualquer histórico de agendamentos, inclusive cancelados/passados (HTTP 409). Para interromper novas reservas preservando histórico, feche todos os dias.
- Sem histórico, a exclusão remove o cadastro e seus períodos, expediente, bloqueios, serviços, sessões, prévias e links pendentes. Clientes globais e outros barbeiros são preservados. Vínculos adicionais que impeçam excluir provocam rollback. A conta/número na Meta não é apagada ou desconectada remotamente.
- O fuso, a duração dos serviços e o passo atual de 30 minutos na oferta de horários continuam definidos pelo backend existente.

## Rotas

Base `/api/admin/barbeiros/{id}`; todas exigem ADMIN; mutações exigem CSRF. GET e POST reutilizam o CORS atual.

| Método | Caminho | Conteúdo |
| --- | --- | --- |
| GET | `/configuracao` | Retorna semana, endereço, latitude e longitude |
| POST | `/configuracao` | Salva `{semana:[{diaSemana,aberto,periodos:[{inicio,fim}]}],endereco,latitude,longitude}` |
| GET | `/bloqueios` | Retorna datas bloqueadas a partir de hoje |
| POST | `/bloqueios` | Recebe `{data,motivo}` |
| POST | `/bloqueios/liberar` | Recebe `{data}` |
| POST | `/remover` | Recebe `{nomeConfirmacao}`, retorna 204 após excluir |

`diaSemana` usa MONDAY a SUNDAY; horários HH:mm; datas ISO yyyy-MM-dd. Nenhuma resposta serializa credenciais da Meta.

Não há alteração de tabelas nem novas dependências.

## Verificação

Testes incluídos para validação, persistência da semana e coordenadas, atualização sem colisão da chave única, isolamento de bloqueios por barbeiro, exclusão com dependências, preservação de histórico e proteção ADMIN/CSRF. A CI executa `./gradlew build`.

A sintaxe JavaScript foi verificada localmente. O navegador automatizado não foi executado neste ambiente (Chromium indisponível); o fluxo visual/produção deve ser conferido após a instalação na Hostinger.
