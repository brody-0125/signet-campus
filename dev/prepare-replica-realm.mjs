import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
const realm=JSON.parse(readFileSync('dev/realm.json','utf8'));
const client=realm.clients.find(c=>c.clientId==='campus-dev');
client.redirectUris=['http://localhost:5186/*'];
client.webOrigins=['http://localhost:5186'];
mkdirSync('secrets',{recursive:true});
writeFileSync('secrets/replica-realm.json',JSON.stringify(realm,null,2)+'\n');
console.log('Disposable replica realm prepared.');
