# Self-hosted web edition

The web edition is a small Node.js reverse proxy you run beside RomM. Visitors sign in independently with their own RomM accounts. It serves the connection screen at `/__rommayo/` and the full RomM site at `/`.

```sh
ROMMAYO_UPSTREAM=https://romm.example.com npm run start:web
```

By default it listens on `127.0.0.1:8787`. Set `HOST=0.0.0.0` only when you intend to expose it to your LAN or a reverse proxy. Put HTTPS in front of it for remote use.

```sh
ROMMAYO_UPSTREAM=http://romm:8080 docker compose up -d --build
```

The container must be able to resolve and reach the upstream. Replace the example with your own server address. `localhost` inside Docker means that container, not your host or RomM container. The default Compose port publishes on the host; restrict it to your intended network or reverse proxy.

Give the web edition a dedicated hostname. The upstream currently needs RomM at its root path. Users cannot turn this service into an arbitrary proxy by entering another server address; changing the upstream is an operator action through `ROMMAYO_UPSTREAM`.

Cookies stay in each visitor's browser; the proxy does not persist credentials or sessions. Passwords pass through the proxy in memory only while logging in, so run the service on infrastructure you trust. HTTP between the proxy and RomM is unencrypted; use HTTPS when that network is not trusted. Cookie domain attributes are rewritten for the companion hostname, while secure and HttpOnly attributes are preserved.

Uploads and downloads stream without a ROM-size limit imposed by the wrapper. Range requests and WebSocket upgrades pass through. Small HTML documents receive the optional blue theme and a Rommayo manifest; binary game files are never rewritten. No offline game cache is added.

Configure SSO redirect URIs and RomM's public URL for the companion hostname if your identity provider requires exact callback URLs. Reverse-proxy authentication, third-party callback flows and streaming configurations need validation for your deployment. Browser install/add-to-home-screen availability depends on your browser, HTTPS and RomM's service worker; the manifest alone does not guarantee offline support or installability.

This service is not a publicly hosted portal to anyone's private server. It is intended to be deployed by each server's operator. No private server address is built into the public project.
