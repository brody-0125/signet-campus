import { readFile, mkdir, writeFile } from 'node:fs/promises';
const realm = JSON.parse(await readFile(new URL('./realm.json', import.meta.url)));
realm.clients[0].redirectUris = ['http://localhost:5183/*'];
realm.clients[0].webOrigins = ['http://localhost:5183'];
// Synthetic institution: browser URLs use the public port; backchannel calls stay inside Keycloak.
const institution = 'http://localhost:8084/realms/institution';
const backchannel = 'http://localhost:8080/realms/institution/protocol/openid-connect';
realm.identityProviders = [{
  alias: 'institution', displayName: 'Test institution', providerId: 'oidc', enabled: true,
  trustEmail: false, firstBrokerLoginFlowAlias: 'first broker login',
  config: {
    clientId: 'campus-broker', clientSecret: 'local-broker-only',
    authorizationUrl: `${institution}/protocol/openid-connect/auth`,
    tokenUrl: `${backchannel}/token`, jwksUrl: `${backchannel}/certs`,
    userInfoUrl: `${backchannel}/userinfo`, issuer: institution,
    defaultScope: 'openid profile email', validateSignature: 'true', useJwksUrl: 'true',
    syncMode: 'IMPORT',
  },
}];
await mkdir(new URL('../secrets/', import.meta.url), { recursive: true });
await writeFile(new URL('../secrets/account-realm.json', import.meta.url), JSON.stringify(realm, null, 2));
await writeFile(new URL('../secrets/institution-realm.json', import.meta.url), JSON.stringify({
  realm: 'institution', enabled: true,
  clients: [{
    clientId: 'campus-broker', enabled: true, protocol: 'openid-connect',
    publicClient: false, secret: 'local-broker-only', standardFlowEnabled: true,
    redirectUris: ['http://localhost:8084/realms/signet-campus/broker/institution/endpoint'],
  }],
  users: [{
    username: 'student', enabled: true, email: 'student@institution.example.test', emailVerified: true,
    firstName: 'Test', lastName: 'Student',
    credentials: [{ type: 'password', value: 'local-student-only', temporary: false }],
  }],
}, null, 2));
