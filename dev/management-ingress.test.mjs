import {test} from 'node:test';
import assert from 'node:assert/strict';
const origin=process.env.CAMPUS_WEB || 'http://localhost:5173';
test('web ingress rejects management paths instead of serving the application', async()=>{
 for(const path of ['/actuator','/actuator/prometheus','/actuator/env']) {
  const response=await fetch(origin+path);
  assert.equal(response.status,404,path);
 }
});