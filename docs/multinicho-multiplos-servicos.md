# Agendamento para diferentes segmentos e múltiplos serviços

O painel e as mensagens usam profissional/estabelecimento. O campo opcional **Segmento** aceita até 80 caracteres (ex.: estética, consultoria, barbearia). O catálogo já permite cadastrar, editar, ativar/desativar serviços, preço e duração.

O modelo atende serviços sequenciais, em uma agenda por profissional/estabelecimento, na mesma data. Não representa equipes simultâneas, equipamentos, capacidade coletiva, duração variável ou prontuários. O segmento é informativo; não ativa regras específicas de profissão. Os identificadores Java `Barbeiro`, as tabelas, rotas `/api/admin/barbeiros`, nome do JAR e nomes dos templates Meta foram preservados para compatibilidade.

## Fluxo e regras

1. Agendar → escolher um serviço → resumo com total e duração.
2. Adicionar serviço → catálogo sem os já escolhidos; Voltar ao resumo permite desistir de adicionar.
3. Continuar → data → horário disponível para a **soma** das durações → confirmação.
4. Refazer seleção limpa todos os itens; enviar uma mensagem de texto reinicia a conversa.

Até 8 serviços distintos por reserva, cada um uma vez. O limite mantém os resumos dentro do corpo interativo do WhatsApp. A duração total deve ser menor que 24 horas e caber integralmente em um período de expediente, sem atravessar intervalo. A reserva ocupa um único período contínuo e o cancelamento cancela o conjunto inteiro.

Cada item guarda ID, nome, preço e duração apresentados. A confirmação recarrega todos os serviços sob a trava do profissional e verifica pertencimento, atividade e alterações individuais (inclusive mudanças que se compensam no total). Uma alteração exige nova seleção. O histórico novo guarda os valores contratados; registros anteriores sem itens continuam legíveis pelo campo legado, sem inventar preços históricos. Notificações, agenda, consulta e bloqueios mostram todos os serviços. Serviços em uso, inclusive como segundo item, não podem ser excluídos; podem ser desativados.

## Deploy na VPS após merge e CI aprovado

O HTML é hospedado separadamente: atualizar apenas o Docker **não atualiza o painel na Hostinger**. Sem mudanças de variáveis obrigatórias.

No diretório do projeto, com o `.env` atual preservado:

```bash
cd /opt/barbearia-git
git switch main
git pull --ff-only origin main
# Construa antes da interrupção; não prossiga se falhar.
docker compose -p barbearia build app
# Pare a versão antiga antes da migração e de aceitar seleções com a etapa nova.
docker compose -p barbearia stop app
docker compose -p barbearia exec -T db pg_dump -U barbearia -d barbearia -Fc > backup-pre-multisservicos.dump
# Verifique sucesso e tamanho do backup antes de prosseguir.
test -s backup-pre-multisservicos.dump
docker compose -p barbearia exec -T db psql -v ON_ERROR_STOP=1 -U barbearia -d barbearia < docs/sql/20261008-multiplos-servicos.sql
docker compose -p barbearia up -d app
docker compose -p barbearia logs --tail=100 app
```

Execute cada etapa e só avance se ela concluir sem erro. A migração adiciona `barbeiros.segmento`, `agendamento_itens`, `sessao_servicos` e expande a restrição da etapa de conversa. É idempotente e preserva os dados anteriores. **Não dependa apenas de `ddl-auto=update`: um CHECK antigo de enum pode rejeitar `REVISANDO_SERVICOS`.** Para instalação vazia, o Hibernate cria o schema completo na primeira inicialização; o SQL é para banco existente.

Publique o conteúdo atualizado de `docs/frontend/painel-zalura.html` na mesma página `/painel/` da Hostinger. Confira no painel um segmento diferente, cadastre dois serviços e teste pelo WhatsApp: soma de preço/duração, seleção única, múltipla, horário perto do almoço/fim do expediente, confirmação, notificação e cancelamento. Mensagens externas dependem das credenciais e templates Meta reais; o CI simula o transporte.

Os nomes dos templates aprovados são mantidos; revise o texto cadastrado na Meta antes de vender para outro segmento, pois uma mudança no código não altera o conteúdo aprovado de um template.

## Reversão

A migração é aditiva, porém o código antigo não entende a etapa nova e exibe apenas o primeiro serviço. **Não reverta simplesmente a imagem após aceitar reservas múltiplas.** Se a implantação falhar antes de receber novas reservas, mantenha a aplicação parada, restaure o backup verificado no banco de destino e volte à imagem anterior e ao HTML anterior. Após receber reservas, prefira correção para frente; restaurar o backup perderia as reservas posteriores. Guarde o backup fora do repositório e registre a versão anterior antes do deploy.

## Testes

`./gradlew build` executa testes unitários/de integração H2. Os testes PostgreSQL exigem `TEST_POSTGRES_URL`, `TEST_POSTGRES_USERNAME` e `TEST_POSTGRES_PASSWORD`; o workflow da PR fornece um PostgreSQL de teste. O teste de migração reproduz schema antigo, aplica o SQL duas vezes e verifica dados e sessão após reinicialização. O teste DOM roda com `NODE_PATH=/tmp/zalura-ui-test/node_modules node src/test/frontend/painel.cjs`, após instalar `jsdom@26.1.0` nesse prefixo.
