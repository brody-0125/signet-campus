import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
const project='signet-campus-replicas';
const ports=[8086,8087];
const names=ports.map((_,i)=>`${project}-${i?'replica':'server'}-1`);
const compose=['compose','-p',project,'-f','compose.yml','-f','compose.replicas.yml'];
const docker=(...args)=>execFileSync('docker',args,{encoding:'utf8',timeout:30000});
const sql=query=>docker(...compose,'exec','-T','db','psql','-U','campus','-d','campus','-At','-c',query).trim();
async function request(port,path,token,body,status=200) {
 const r=await fetch(`http://localhost:${port}/api${path}`,{signal:AbortSignal.timeout(10000),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body===undefined?{}:{method:'POST',body:JSON.stringify(body)})});
 assert.equal(r.status,status,path);return status===204?null:r.json();
}
async function login(username){const r=await fetch('http://localhost:8088/realms/signet-campus/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({grant_type:'password',client_id:'campus-dev',username,password:`local-${username}-only`})});assert.equal(r.status,200);return (await r.json()).access_token;}
async function eventually(read,accept,timeout=30000){const until=Date.now()+timeout;do{try{const value=await read();if(accept(value))return value;}catch{}await delay(100);}while(Date.now()<until);throw new Error('Recovery condition not reached');}
async function control(path,body){const r=await fetch(`http://localhost:8099${path}`,{...(body===undefined?{}:{method:'POST',body:JSON.stringify(body)})});assert.equal(r.status,200);return r.json();}

test('two actual replicas preserve issuance and recover a delivered-but-uncommitted notification',async()=>{
 const containers=JSON.parse(docker('inspect',...names));
 for(const c of containers) assert.equal(c.Config.Labels['com.docker.compose.project'],project);
 assert.notEqual(containers[0].Id,containers[1].Id);
 assert.equal(containers[0].Mounts.find(m=>m.Destination==='/run/secrets').Source,containers[1].Mounts.find(m=>m.Destination==='/run/secrets').Source);
 await eventually(async()=>{
  const replies=await Promise.all(['http://localhost:8086/api/achievements','http://localhost:8087/api/achievements','http://localhost:8088/realms/signet-campus/.well-known/openid-configuration'].map(url=>fetch(url,{signal:AbortSignal.timeout(2000)})));
  return replies.every(r=>r.ok);
 },Boolean,120000);
 const [reviewer,learner]=await Promise.all([login('reviewer'),login('learner')]);
 const draft=await request(ports[0],'/achievements',reviewer,{name:`Replica audit ${randomUUID()}`,criteria:'Assess cross-instance behavior'},201);
 const achievement=await request(ports[1],`/achievements/${draft.id}/publish`,reviewer,{expectedVersion:draft.version});
 const submit=()=>request(ports[0],'/submissions',learner,{achievementId:achievement.id,evidence:'Synthetic replica evidence'},201);
 const record=await submit();
 const reviews=await Promise.all(ports.map(port=>fetch(`http://localhost:${port}/api/submissions/${record.submission.id}/approve`,{method:'POST',headers:{Authorization:`Bearer ${reviewer}`,'Content-Type':'application/json'},body:JSON.stringify({expectedVersion:record.version})})));
 assert.deepEqual(reviews.map(r=>r.status).sort(),[200,409]);
 const awards=await Promise.all(Array.from({length:12},(_,i)=>request(ports[i%2],`/submissions/${record.submission.id}/credential`,learner,{})));
 awards.forEach(a=>assert.deepEqual(a,awards[0]));
 assert.equal(sql(`SELECT count(*) FROM credentials WHERE submission_id='${record.submission.id}'`),'1');
 const id=awards[0].id.split('/').pop();
 for(const port of ports){assert.deepEqual(await request(port,`/credentials/${id}`,learner),awards[0]);assert.equal((await request(port,`/credentials/${id}/verify`,null,awards[0])).valid,true);}
 const racing=await submit();
 const paused=await submit();
 for(const s of [racing,paused]) await request(ports[0],`/submissions/${s.submission.id}/approve`,reviewer,{expectedVersion:s.version});
 const [archived,raced]=await Promise.all([
  request(ports[0],`/achievements/${achievement.id}/archive`,reviewer,{expectedVersion:achievement.version,archived:true}),
  fetch(`http://localhost:${ports[1]}/api/submissions/${racing.submission.id}/credential`,{method:'POST',headers:{Authorization:`Bearer ${learner}`,'Content-Type':'application/json'},body:'{}'}),
 ]);
 assert.ok([200,423].includes(raced.status));
 assert.equal(sql(`SELECT count(*) FROM credentials WHERE submission_id='${racing.submission.id}'`),raced.status===200?'1':'0');
 for(const port of ports){
  await request(port,`/submissions/${paused.submission.id}/credential`,learner,{},423);
  assert.deepEqual(await request(port,`/submissions/${record.submission.id}/credential`,learner,{}),awards[0]);
 }
 await request(ports[1],`/achievements/${achievement.id}/archive`,reviewer,{expectedVersion:archived.version,archived:false});
 const path=await request(ports[0],'/pathways',reviewer,{name:`Crash delivery ${randomUUID()}`,description:'Retain committed awards',achievementIds:[achievement.id]},201);
 await control('/arm',{});
 await request(ports[1],`/pathways/${path.id}/enrollment`,learner,{});
 const held=await eventually(()=>control('/state'),s=>s.held);
 const event=sql(`SELECT id FROM notification_outbox WHERE pathway_id='${path.id}'`);
 assert.equal(held.messageId,`${event}@signet-campus.invalid`);
 assert.equal(sql(`SELECT sent_at IS NULL FROM notification_outbox WHERE id='${event}'`),'t');
 const worker=containers.find(c=>Object.values(c.NetworkSettings.Networks).some(n=>n.IPAddress===held.peer));
 assert.ok(worker,'Held SMTP connection belongs to a disposable worker');
 const killed=worker.Name.slice(1);assert.ok(names.includes(killed));
 let fresh;
 try {
  docker('kill',killed);
  await eventually(()=>sql(`SELECT sent_at IS NOT NULL FROM notification_outbox WHERE id='${event}'`),s=>s==='t');
  assert.equal(sql(`SELECT attempts FROM notification_outbox WHERE id='${event}'`),'1');
  const messages=await (await fetch('http://localhost:8027/api/v1/messages')).json();
  const delivered=messages.messages.filter(m=>m.Snippet.includes(path.name));
  assert.equal(delivered.length,2,'Acknowledgment-loss window can duplicate SMTP delivery');
  for(const m of delivered){const detail=await (await fetch(`http://localhost:8027/api/v1/message/${m.ID}`)).json();assert.equal(detail.MessageID,held.messageId);}
  const survivor=ports[names.indexOf(killed)===0?1:0];
  const next=await request(survivor,'/submissions',learner,{achievementId:achievement.id,evidence:'Survivor work'},201);
  await request(survivor,`/submissions/${next.submission.id}/approve`,reviewer,{expectedVersion:next.version});
  fresh=await request(survivor,`/submissions/${next.submission.id}/credential`,learner,{});
  assert.equal((await request(survivor,`/credentials/${fresh.id.split('/').pop()}/verify`,null,fresh)).valid,true);
 } finally {docker('start',killed);}
 await eventually(async()=>{try{return (await fetch(`http://localhost:${ports[names.indexOf(killed)]}/api/achievements`)).status}catch{return 0}},s=>s===200);
 const completed=await Promise.all(ports.map(port=>request(port,`/pathways/${path.id}/credential`,learner,{})));
 assert.deepEqual(completed[0],completed[1]);
 assert.equal(sql(`SELECT count(*) FROM credentials WHERE pathway_id='${path.id}'`),'1');
 for(const port of ports) {
  assert.deepEqual(await request(port,`/credentials/${id}`,learner),awards[0]);
  for(const award of [awards[0],fresh]) assert.equal((await request(port,`/credentials/${award.id.split('/').pop()}/verify`,null,award)).valid,true);
 }
 console.log(JSON.stringify({submission:record.submission.id,credential:id,event,killed,deliveries:2,recovered:true}));
});
