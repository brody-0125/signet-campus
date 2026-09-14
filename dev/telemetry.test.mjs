import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes,randomUUID} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
const origin=process.env.CAMPUS_WEB || 'http://localhost:5173';
const zipkin=process.env.CAMPUS_ZIPKIN || 'http://localhost:9411';
const prometheus=process.env.CAMPUS_PROMETHEUS || 'http://localhost:9090';
async function eventually(read,accept) {
 for(let attempt=0;attempt<40;attempt++) {
  try {const value=await read();if(accept(value)) return value;} catch {}
  await delay(1000);
 }
 throw new Error('Telemetry was not collected before the deadline');
}
test('real traces and scraped metrics preserve route templates and omit request secrets', async()=>{
 const login=await fetch(`${process.env.CAMPUS_ISSUER || 'http://localhost:8081/realms/signet-campus'}/protocol/openid-connect/token`, {method:'POST',body:new URLSearchParams({grant_type:'password',client_id:'campus-dev',username:'learner',password:'local-learner-only'})});
 assert.equal(login.status,200);
 const token=(await login.json()).access_token;
 const subject=JSON.parse(Buffer.from(token.split('.')[1],'base64url')).sub;
 const canary=`private-${randomUUID()}`;
 const requests=[`/api/achievements?evidence=${canary}`,`/api/achievements/${randomUUID()}?email=${canary}`, '/api/submissions', '/api/submissions'];
 const traces=await Promise.all(requests.map(async(path,index)=>{
  const traceId=randomBytes(16).toString('hex');
  const response=await fetch(origin+path,{headers:{traceparent:`00-${traceId}-${randomBytes(8).toString('hex')}-01`,'X-Private-Canary':canary,...(index===3?{Authorization: 'Bearer '+token}:{})}});
  assert.equal(response.status,[200,404,401,200][index]);
  const spans=await eventually(async()=>{const r=await fetch(`${zipkin}/api/v2/trace/${traceId}`);return r.ok?r.json():[];},spans=>spans.some(s=>s.kind==='SERVER'));
  assert.ok(spans.every(s=>s.traceId===traceId));
  assert.equal(JSON.stringify(spans).includes(canary),false);
  assert.equal(JSON.stringify(spans).includes(subject),false);
  assert.equal(JSON.stringify(spans).includes(token),false);
  assert.equal(JSON.stringify(spans).includes('http.url'),false);
  assert.ok(spans.some(s=>s.localEndpoint?.serviceName==='signet-campus'));
  return traceId;
 }));
 assert.equal(new Set(traces).size,requests.length);
 const query='http_server_requests_seconds_count{job="signet-campus",uri="/api/achievements",status="200"}';
 const series=await eventually(async()=> (await (await fetch(`${prometheus}/api/v1/query?query=${encodeURIComponent(query)}`)).json()).data?.result || [],data=>data.some(s=>Number(s.value[1])>0));
 assert.ok(series.length>0);
 const metrics=await (await fetch(`${prometheus}/api/v1/series?match[]=${encodeURIComponent('{job="signet-campus"}')}`)).text();
 assert.equal(metrics.includes(canary),false);
 console.log(`Collected isolated request traces: ${traces.join(', ')}`);
});