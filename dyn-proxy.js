const http = require('http');
const httpProxy = require('http-proxy');
const fs = require('fs');
const execSync = require('child_process').execSync;
let chrdbgport = null;

function ec(cmd) {
    execSync(cmd, {stdio:[0,1,2]});
}

var proxy = httpProxy.createProxyServer({});
var server = http.createServer(function(req, res) {
	if(chrdbgport==null) {
		let file = execSync('find /tmp -name "DevToolsActivePort"|grep "DevToolsActivePort"').toString();
		let port = execSync('head -n 1 '+file).toString();
		chrdbgport = port*1;
		console.log("Forwarding to port ..." + chrdbgport);
		proxy.web(req, res, { target: 'http://127.0.0.1:'+chrdbgport });
	} else {
		console.log("Forwarding to port ..." + chrdbgport);
		proxy.web(req, res, { target: 'http://127.0.0.1:' + chrdbgport });
	} 
});
server.on('upgrade', function (req, socket, head) {
	proxy.ws(req, socket, head, { target: 'http://127.0.0.1:' + chrdbgport });
});

console.log("listening on port 5050");
server.listen(8001);


function onRequest(client_req, client_res) {
	console.log('serve: ' + client_req.url);

	/*
	DVT_FILE=`find /tmp -name "DevToolsActivePort"|grep "DevToolsActivePort"`
	DVT_PORT=`head -n 1 ${DVT_FILE}`
	*/
	
	if(chrdbgport==null) {
		let file = execSync('find /tmp -name "DevToolsActivePort"|grep "DevToolsActivePort"').toString();
		let port = execSync('head -n 1 '+file).toString();
		chrdbgport = port*1;
		console.log("Forwarding to port ..." + chrdbgport);
		
		var options = {
			hostname: 'localhost',
			port: chrdbgport,
			path: client_req.url,
			method: client_req.method,
			headers: client_req.headers
		};
	
		var proxy = http.request(options, function(res) {
			client_res.writeHead(res.statusCode, res.headers)
			res.pipe(client_res, {
				end: true
			});
		});
	
		client_req.pipe(proxy, {
			end: true
		});
	} else {
		console.log("Forwarding to port ..." + chrdbgport);
		var options = {
			hostname: 'localhost',
			port: chrdbgport,
			path: client_req.url,
			method: client_req.method,
			headers: client_req.headers
		};
	
		var proxy = http.request(options, function(res) {
			client_res.writeHead(res.statusCode, res.headers)
			res.pipe(client_res, {
				end: true
			});
		});
	
		client_req.pipe(proxy, {
			end: true
		});
	}
}