upnp-ssdp
=========

UPnP SSDP node module implements the SSDP device discovery as specified in
http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.0-20080424.pdf

The rough idea is for the server to listen for 'M-SEARCH' multicast messages on
239.255.255.250 (port 1900). The server responds to the client with it's location
(typically an IP address and a port).

Installation
------------
npm install upnp-ssdp

Usage
-----
Announce your server using announce().

```
var Ssdp = require('upnp-ssdp');
var server = Ssdp();
server.announce('device:server');
```

Clients can now discover the server using search().

````
var Ssdp = require('upnp-ssdp');
var client = Ssdp();
client.on('up', function (address) {
    console.log('server found', address);
});
client.on('down', function (address) {
    console.log('server ' + address + ' not responding anymore');
});
client.on('error', function (err) {
    console.log('error initiating SSDP search', err);
});
client.search('device:server');
````

API
---
* Methods
  * announce(service)
  * search(service)
* Events
  * up(address)
  * down(address)
  * error(err)
