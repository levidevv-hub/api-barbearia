// DOM integration test. Run with jsdom@26.1.0 available in NODE_PATH.
const {JSDOM}=require('jsdom');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const html=fs.readFileSync(path.resolve(__dirname,'../../../docs/frontend/painel-zalura.html'),'utf8');
const days=['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];
const barbers=[1,2].map(id=>({id,nome:'Barbeiro '+id,numeroWhatsAppAdministrador:'5588999999999',numeroWhatsAppNotificacao:'5588999999999',inicioExpediente:'08:00',fimExpediente:'18:00',endereco:'Rua Teste',vinculoRegistrado:true}));
const configs=new Map(barbers.map(b=>[b.id,{semana:days.map(diaSemana=>({diaSemana,aberto:diaSemana!=='SUNDAY',periodos:diaSemana==='SUNDAY'?[]:[{inicio:'08:00',fim:'12:00'},{inicio:'13:30',fim:'18:00'}]})),endereco:'Rua Teste',latitude:-4.94,longitude:-37.97}]));
const services=new Map([[1,[]],[2,[]]]),blocks=new Map([[1,[]],[2,[]]]);
const requests=[],errors=[];
let idCounter=1,acceptConfirm=true,failNextService=false;
const dom=new JSDOM(html,{url:'https://zaluratech.com.br/painel/',runScripts:'dangerously',pretendToBeVisual:true,beforeParse(w){
  w.HTMLElement.prototype.scrollIntoView=function(){};
  w.confirm=()=>acceptConfirm;w.prompt=()=>null;
  w.fetch=async(url,options={})=>{
    const route=new URL(url).pathname,method=options.method||'GET';
    const body=options.body?JSON.parse(options.body):null;requests.push({route,method,body,options});
    if(method==='POST')assert.equal(options.headers['X-CSRF-TOKEN'],'csrf-test');
    assert.equal(options.credentials,'include');
    const ok=(value,status=200)=>new Response(status===204?null:JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});
    if(route.endsWith('/csrf'))return ok({token:'csrf-test',headerName:'X-CSRF-TOKEN'});
    if(route.endsWith('/session'))return ok({username:'admin'});
    if(route==='/api/admin/barbeiros')return ok(barbers);
    const match=route.match(/^\/api\/admin\/barbeiros\/(\d+)\/(.*)$/);
    assert.ok(match,'Unexpected route '+route);const id=Number(match[1]),resource=match[2];
    if(resource==='dados'){
      const b=barbers.find(b=>b.id===id);if(method==='POST')Object.assign(b,body);return ok(b);
    }
    if(resource==='configuracao'){
      if(method==='POST')configs.set(id,body);return ok(configs.get(id));
    }
    if(resource==='bloqueios'){
      if(method==='POST')blocks.get(id).push(body);return ok(method==='GET'?blocks.get(id):{mensagem:'OK'});
    }
    if(resource==='bloqueios/editar'){
      const b=blocks.get(id).find(b=>b.data===body.dataOriginal);Object.assign(b,{data:body.data,motivo:body.motivo});return ok({mensagem:'OK'});
    }
    if(resource==='bloqueios/liberar'){
      blocks.set(id,blocks.get(id).filter(b=>b.data!==body.data));return ok({mensagem:'OK'});
    }
    if(resource==='servicos'){
      if(method==='POST'){
        if(failNextService){failNextService=false;return ok({erro:'Falha de gravação de teste'},500)}
        const value={id:idCounter++,...body};services.get(id).push(value);return ok(value,201);
      }
      return ok(services.get(id));
    }
    if(resource.startsWith('servicos/')){
      const [,serviceId,action]=resource.split('/');const service=services.get(id).find(s=>s.id===Number(serviceId));assert.ok(service);
      if(action==='remover'){services.set(id,services.get(id).filter(s=>s!==service));return ok(null,204)}
      Object.assign(service,body);return ok(service);
    }
    throw new Error('Unexpected route '+route);
  };
  w.addEventListener('error',e=>errors.push(e.error));
}});
const w=dom.window,d=w.document,$=id=>d.getElementById(id);
async function until(predicate){for(let i=0;i<100;i++){if(predicate())return;await new Promise(r=>setTimeout(r,10))}throw new Error('Timed out: '+predicate)}
const click=e=>e.click();
function fill(input,value){input.value=value;input.dispatchEvent(new w.Event('input',{bubbles:true}))}
function change(input,value){input.value=value;input.dispatchEvent(new w.Event('change',{bubbles:true}))}
function submit(form){assert.equal(form.checkValidity(),true,'Invalid form '+form.id);form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}))}
function button(root,text){return [...root.querySelectorAll('button')].find(b=>b.textContent===text)}
(async()=>{
  await until(()=>$('rows').children.length===2);
  click(button($('rows'),'Configurar'));await until(()=>!$('profile-section').hidden&&$('config-loading').hidden);
  await until(()=>$('services-status').textContent.includes('Nenhum'));
  assert.equal($('config-days').children.length,7);
  const monday=$('config-days').children[0];assert.equal(monday.querySelector('select').value,'break');
  assert.equal(monday.querySelector('[data-field="break-start"]').value,'12:00');
  change(monday.querySelector('select'),'none');assert.equal(monday.querySelector('[data-field="break-start"]').disabled,true);
  submit($('config-form'));await until(()=>configs.get(1).semana[0].periodos.length===1);await until(()=>$('config-fields').disabled===false);
  assert.deepEqual(configs.get(1).semana[0].periodos,[{inicio:'08:00',fim:'18:00'}]);
  change(monday.querySelector('select'),'break');fill(monday.querySelector('[data-field="break-start"]'),'12:00');fill(monday.querySelector('[data-field="break-end"]'),'11:00');
  const posts=requests.filter(r=>r.method==='POST').length;submit($('config-form'));await until(()=>!$('config-error').hidden);
  assert.equal(requests.filter(r=>r.method==='POST').length,posts,'Invalid break must not be saved');
  fill(monday.querySelector('[data-field="break-end"]'),'13:30');submit($('config-form'));await until(()=>configs.get(1).semana[0].periodos.length===2);await until(()=>!$('config-fields').disabled);
  fill($('profile-form').elements.nome,'Barbearia Central');submit($('profile-form'));await until(()=>$('profile-status').textContent==='Dados salvos.');await until(()=>$('rows').textContent.includes('Barbearia Central'));
  click($('service-new'));const f=$('service-form');fill(f.elements.nome,'Corte degradê');fill(f.elements.preco,'35.50');fill(f.elements.duracaoMinutos,'45');submit(f);
  await until(()=>$('services-list').children.length===1);assert.equal(services.get(1)[0].duracaoMinutos,45);assert.equal(services.get(2).length,0);
  click(button($('services-list'),'Editar'));fill(f.elements.preco,'42.90');fill(f.elements.duracaoMinutos,'60');submit(f);await until(()=>services.get(1)[0].duracaoMinutos===60);await until(()=>!$('service-fields').disabled);
  assert.equal(services.get(1)[0].preco,42.9);
  click(button($('services-list'),'Desativar'));await until(()=>button($('services-list'),'Reativar'));click(button($('services-list'),'Reativar'));await until(()=>button($('services-list'),'Desativar'));
  click($('service-new'));fill(f.elements.nome,'Rascunho');fill(f.elements.preco,'20');fill(f.elements.duracaoMinutos,'20');failNextService=true;submit(f);await until(()=>$('config-error').textContent==='Falha de gravação de teste');
  assert.equal(f.hidden,false);assert.equal(f.elements.nome.value,'Rascunho');assert.equal(services.get(1).length,1);
  acceptConfirm=false;click($('config-close'));assert.equal($('config-box').hidden,false,'Unsaved service must block navigation');acceptConfirm=true;click($('service-cancel'));
  click(button($('services-list'),'Excluir'));await until(()=>services.get(1).length===0);await until(()=>!$('service-fields').disabled);
  fill($('block-form').elements.data,'2027-01-10');fill($('block-form').elements.motivo,'Folga');submit($('block-form'));await until(()=>$('blocks-list').textContent.includes('Folga'));await until(()=>!$('block-fields').disabled);
  click(button($('blocks-list'),'Editar'));fill($('block-form').elements.data,'2027-01-11');fill($('block-form').elements.motivo,'Viagem');submit($('block-form'));await until(()=>$('blocks-list').textContent.includes('11/01/2027 · Viagem'));await until(()=>!$('block-fields').disabled);
  click(button($('blocks-list'),'Liberar'));await until(()=>blocks.get(1).length===0);await until(()=>!$('block-fields').disabled);
  click($('config-close'));assert.equal($('config-box').hidden,true);
  click(button($('rows'),'Configurar'));await until(()=>$('profile-form').elements.nome.value==='Barbearia Central'&&$('config-loading').hidden);
  assert.equal($('config-days').children[0].querySelector('select').value,'break');
  click($('config-close'));
  configs.get(1).semana[0].periodos=[{inicio:'08:00',fim:'10:00'},{inicio:'10:30',fim:'12:00'},{inicio:'14:00',fim:'18:00'}];
  click(button($('rows'),'Configurar'));await until(()=>$('config-loading').hidden);
  assert.equal($('config-days').children[0].querySelector('select').value,'custom');
  submit($('config-form'));await until(()=>!$('config-fields').disabled);
  assert.equal(configs.get(1).semana[0].periodos.length,3,'Existing multiple breaks must be preserved');
  assert.deepEqual(configs.get(1).semana[6].periodos,[],'Closed days have no availability');
  assert.deepEqual(errors,[]);
  console.log('PASS: load, interval modes, invalid break, profile edit, service CRUD/status, failed save draft, unsaved navigation, block CRUD, reload and CSRF.');
  dom.window.close();
})().catch(e=>{console.error(e);dom.window.close();process.exitCode=1});
