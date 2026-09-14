import { readFile, mkdir, writeFile } from 'node:fs/promises';
const realm = JSON.parse(await readFile(new URL('./realm.json', import.meta.url)));
realm.clients[0].redirectUris = ['http://localhost:5183/*'];
realm.clients[0].webOrigins = ['http://localhost:5183'];
await mkdir(new URL('../secrets/', import.meta.url), { recursive: true });
await writeFile(new URL('../secrets/account-realm.json', import.meta.url), JSON.stringify(realm, null, 2));
