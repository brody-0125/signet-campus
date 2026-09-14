// Test-only transparent SMTP relay. Hold one final DATA acknowledgment after Mailpit accepts it.
import net from 'node:net';
import http from 'node:http';
let armed=false, held=null;
net.createServer(client=>{
  const upstream=net.connect(1025,'mailpit');
  let sent='', replies='', dataComplete=false;
  client.on('data',chunk=>{
    sent=(sent+chunk.toString()).slice(-65536);
    if(sent.includes('\r\n.\r\n')) dataComplete=true;
    upstream.write(chunk);
  });
  upstream.on('data',chunk=>{
    replies+=chunk.toString();
    let end;
    while((end=replies.indexOf('\r\n'))!==-1){
      const line=replies.slice(0,end+2);replies=replies.slice(end+2);
      if(dataComplete && line.startsWith('250 ') && armed){
        armed=false;
        held={held:true,peer:client.remoteAddress.replace('::ffff:',''),messageId:sent.match(/Message-ID:\s*<([^>]+)>/i)?.[1]};
        console.log('Held final SMTP acknowledgment for crash rehearsal.');
      } else client.write(line);
      if(dataComplete && line.startsWith('250 ')){sent='';dataComplete=false;}
    }
  });
  client.on('close',()=>upstream.destroy());
  upstream.on('close',()=>client.destroy());
  client.on('error',()=>upstream.destroy());
  upstream.on('error',()=>client.destroy());
}).listen(1025,'0.0.0.0');
http.createServer((request,response)=>{
  if(request.method==='POST' && request.url==='/arm'){armed=true;held=null;}
  else if(request.method!=='GET' || request.url!=='/state'){response.writeHead(404);response.end();return;}
  response.writeHead(200,{'Content-Type':'application/json'});
  response.end(JSON.stringify(held || {held:false,armed}));
}).listen(8080,'0.0.0.0');
