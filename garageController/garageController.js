var Ssdp = require('upnp-ssdp');
var express = require('express');
var app = express();
var http = require('http');
var https = require('https');
var pfio = require('piface-node');
var express = require('express'),
    app = express(),
    fs = require('fs');

pfio.init();

var server = new Ssdp();

server.announce([{name:'urn:schemas-upnp-org:device:GarageController:1',port:8080}, {name:'urn:schemas-upnp-org:device:GarageController:1',port:8081}]);

http.createServer(app).listen(8080, "192.168.0.174");
http.createServer(app).listen(8081, "192.168.0.174");

var status;

var installationId= "8b2a297e-003c-435e-b001-708659128429";

// get the installation id from https://graph.api.smartthings.com/api/smartapps/endpoints
// set headers to Authorizations:Bearer 5453aadc-ed57-4607-93a8-585d8771fcbf

(function checkSensors()
{
    setTimeout( function()
    {
        var bits=  pfio.digital_read(0) + (pfio.digital_read(1)<<1);

        if( status !== bits)
        {
            notify("graph.api.smartthings.com", 443, "/api/smartapps/installations/"+installationId+"/event/update", function(){ console.log("Status Change")})
        }

        status= bits;

        checkSensors();

    }, 1000);
})();


function pressDoor(idx)
{
    pfio.digital_write(idx,1);

    console.log("Relay",idx,"on");

    setTimeout( function()
    {
        console.log("Relay",idx,"off");

        pfio.digital_write(idx,0);
    }, 500);
}

app.get('/GarageController/1/door/close', function( req, res, next) {

    console.log("Close door");

//    if( pfio.digital_read(ci(req)) === 0) // open
    //{
        pressDoor(ci(req));

  //      res.json({ msg: "state", name : "door", state : "closed" });
    //}
    //else
   // {
        res.json({ msg: "state", name : "door", state : "closed" });
    //}
});

app.get('/GarageController/1/door/open', function( req, res, next) {

    console.log("Open door");

    //if( pfio.digital_read(ci(req)) === 1) // closed
    //{
        pressDoor(ci(req));

      //  res.json({ msg: "state", name : "door", state : "opened" });
    //}
    //else
    //{
        res.json({ msg: "state", name : "contact", state : "opened" });
    //}
});

app.get('/GarageController/1/status', function( req, res, next) {

    var result= ({ msg: "status", door: (pfio.digital_read(ci(req)) === 1) ? "closed" : "open"});

    console.log("Status", result);

   res.json(result);
});

function notify( address, port, path, cb)
{
    if(!token)return;

    var options = {
        hostname: address,
        port: port,
        path: path,
        method: 'GET',
        headers: {

            Authorization: "Bearer "+token.token.token.access_token
        }
    };

    var req= https.request(options, function(res) {

        res.setEncoding('utf8');
        res.on('data', function (chunk) {

            try
            {
                cb( JSON.parse( chunk));
            }
            catch(e)
            {
                cb( undefined);
            }
        });
    });

    req.on('error', function(e) {

    });

    req.end();
}

function ci( req)
{
    var index= (+req.socket.server._connectionKey.split(":")[2])&1;

    return index|0;
}

app.get('/GarageController/*', function( req, res, next) {

    console.log(ci(req));

    res.json(

    {
        device:
        {
            name: (ci(req)?"Right":"Left")+" Garage Door",
            modelName:"lttlrck",
            serialNum:1,
            key:"uuid:RINCON_000E581339C201400::urn:schemas-upnp-org:device:GarageController:1_"+req.socket.server._connectionKey.split(":")[2]
        }
    }
    );
});

var credentials= JSON.parse( fs.readFileSync("credentials"));

var OAuth2 = require('simple-oauth2')({
    clientID: credentials.clientID,
    clientSecret: credentials.clientSecret,
    site: 'https://graph.api.smartthings.com',
    authorizationPath: '/oauth/authorize',
    tokenPath: '/oauth/token',
});

// Authorization uri definition
var authorization_uri = OAuth2.AuthCode.authorizeURL({
    redirect_uri: 'http://192.168.0.174:3000/callback',
    scope: 'app',
    state: '3(#0/!~'
});

// Initial page redirecting to Github
app.get('/auth', function (req, res) {
    res.redirect(authorization_uri);
});

var token;

try
{
    token = OAuth2.AccessToken.create( JSON.parse( fs.readFileSync("oauth")));

    console.log(token);
}
catch(e)
{
    console.log("No token, do auth");
}

// Callback service parsing the authorization token and asking for the access token
app.get('/callback', function (req, res) {
    var code = req.query.code;
    console.log('/callback', code);
    OAuth2.AuthCode.getToken({
        code: code,
        redirect_uri: 'http://192.168.0.174:3000/callback'
    }, saveToken);

    function saveToken(error, result) {
        if (error) { console.log('Access Token Error', error.message, result); }
        token = OAuth2.AccessToken.create(result);

        fs.writeFileSync( "oauth", JSON.stringify(token));

        console.log("token", token.token.access_token);

        res.send("OK", token);
    }
});

app.get('/', function (req, res) {
    res.end();
});

app.listen(3000);

console.log('Authorization server started on port 3000');

