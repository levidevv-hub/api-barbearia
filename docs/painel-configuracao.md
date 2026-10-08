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
- Bloqueios fecham o dia inteiro e continuam valendo após editar a semana. O painel consulta a prévia e pede confirmação antes de cancelar as reservas futuras do dia e registrar seus avisos. A mensagem aos clientes é opcional e fica visível a quem tentar agendar nessa data. Veja `bloqueio-mensagem-clientes.md` para templates e deploy.
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
| POST | `/bloqueios` | Recebe `{data,motivo,idsConfirmados}`; use `/bloqueios/previa` para consultar os afetados |
| POST | `/bloqueios/liberar` | Recebe `{data}` |
| POST | `/remover` | Recebe `{nomeConfirmacao}`, retorna 204 após excluir |

`diaSemana` usa MONDAY a SUNDAY; horários HH:mm; datas ISO yyyy-MM-dd. Nenhuma resposta serializa credenciais da Meta.

Não há alteração de tabelas nem novas dependências.

## Verificação

Testes incluídos para validação, persistência da semana e coordenadas, atualização sem colisão da chave única, isolamento de bloqueios por barbeiro, exclusão com dependências, preservação de histórico e proteção ADMIN/CSRF. A CI executa `./gradlew build`.

A sintaxe JavaScript foi verificada localmente. O navegador automatizado não foi executado neste ambiente (Chromium indisponível); o fluxo visual/produção deve ser conferido após a instalação na Hostinger.


## Gestão completa pelo painel

O HTML `docs/frontend/painel-zalura.html` inclui agora:

- Cadastro de barbeiros e edição de nome, WhatsApp administrador e WhatsApp de notificações.
- Expediente para os sete dias, dia fechado, sem intervalo, com intervalo de almoço ou múltiplos períodos.
- Cadastro/edição de cortes, barba e combos com nome, preço, duração em minutos e situação ativo/inativo.
- Exclusão de serviço sem vínculos; desativação/reativação de serviços vinculados a conversas ou reservas.
- Edição de endereço e coordenadas, bloqueio/edição/liberação de datas e remoção de barbeiros sem histórico.

As seções Dados, Horários e Serviços têm gravação própria. As alterações vão para a API e o banco; não ficam apenas no navegador. Ao cadastrar um barbeiro, a configuração abre automaticamente. Complete a semana e adicione pelo menos um serviço ativo antes de disponibilizar o bot.

### Intervalo

Sem intervalo: entrada 08:00, saída 18:00 gera um único período 08:00–18:00.
Com intervalo: entrada 08:00, almoço 12:00–13:30, saída 18:00 gera os períodos 08:00–12:00 e 13:30–18:00. As pontas do intervalo precisam ficar dentro do expediente. Horários que atravessariam a pausa não são oferecidos pelo bot.
A escolha é persistida pelos períodos já existentes no banco, sem uma coluna redundante. Configurações com mais de dois períodos são abertas no modo múltiplos períodos, sem perda de dados. Fechar todos os dias interrompe a oferta de novos horários após salvar; reservas existentes continuam registradas.

### Novos contratos REST

Todas as rotas exigem sessão ADMIN; todos os POSTs exigem CSRF. São usados DTOs sem tokens da Meta.

| Método | Rota após `/api/admin/barbeiros/{id}` | Corpo/retorno |
|---|---|---|
| GET | `/dados` | Resumo do barbeiro sem credenciais |
| POST | `/dados` | `nome`, `numeroWhatsAppAdministrador`, `numeroWhatsAppNotificacao` |
| GET | `/servicos` | Lista de serviços ativos e inativos deste barbeiro |
| POST | `/servicos` | `nome`, `preco`, `duracaoMinutos`, `ativo`; retorna 201 |
| POST | `/servicos/{servicoId}` | Os mesmos quatro campos; retorna serviço atualizado |
| POST | `/servicos/{servicoId}/status` | `ativo` booleano |
| POST | `/servicos/{servicoId}/remover` | Sem corpo; retorna 204 ou 409 se houver vínculos |
| POST | `/bloqueios/editar` | `dataOriginal`, `data`, `motivo` |

O preço é não negativo, com até duas casas decimais; duração entre 1 e 1439 minutos. Os serviços são sempre consultados pelo par barbeiro/serviço. Escritas compartilham a trava do barbeiro usada no agendamento. Alterar a duração de um serviço não reescreve a duração das reservas existentes.

Não se apaga histórico automaticamente. Serviços com reservas (inclusive canceladas) ou referências em conversas devem ser desativados. Barbeiros com histórico não podem ser excluídos; é possível fechar a semana pelo painel. Uma mudança de expediente também não cancela reservas automaticamente. A grade atual do bot mantém passos de 30 minutos entre os horários iniciais, embora a duração de cada serviço seja configurável em minutos.

### Instalação

Esta branch reúne as mudanças das PRs #19 e #20 e a gestão completa. Atualize a API com esta versão e depois publique `docs/frontend/painel-zalura.html` como `painel/index.html` na Hostinger. O frontend depende das novas rotas. Não é necessário criar novas tabelas nem configurar novas credenciais.

Teste após a publicação: criar barbeiro, salvar semana com almoço, cadastrar serviço, recarregar o painel e conferir persistência; editar preço/duração e desativar/reativar; conferir as opções no WhatsApp. Confirme que o almoço não aparece e que nenhum corte cruza a pausa. Repita escolhendo sem intervalo.


### Validação desta entrega

- `node --check`: sintaxe do JavaScript do HTML.
- `src/test/frontend/painel.cjs`: integração do DOM com API simulada, usando jsdom 26.1.0. Cobre almoço/sem intervalo, intervalo inválido, múltiplos períodos, dias fechados, dados, serviços, falha de gravação, rascunhos, bloqueios, recarga e CSRF.
- `PainelGestaoTest`: testes JUnit de persistência, isolamento por barbeiro, segurança/validação HTTP, proteção de histórico e disponibilidade durante o almoço.
- Os testes Java precisam ser executados com `./gradlew test` antes do merge/deploy. Neste ambiente o download do Gradle foi bloqueado pela rede. Testes de DOM não substituem a conferência visual em navegador nem um teste integrado após o deploy.

Para executar o teste do painel sem adicionar dependências à aplicação Java:

```bash
npm install --prefix /tmp/zalura-ui-test jsdom@26.1.0 --no-audit --no-fund
NODE_PATH=/tmp/zalura-ui-test/node_modules node src/test/frontend/painel.cjs
```
