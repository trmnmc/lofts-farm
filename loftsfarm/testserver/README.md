# Test server

The slice needs its own Paper 1.21.10 server with a superflat world. Do not
install it into `lofts-dev` — that carries Lofts v1 and a live Tebex secret,
and v2 is designed to be isolated from both.

Minimum setup:

1. A folder with `paper-1.21.10.jar`.
2. `eula.txt` containing `eula=true`.
3. `server.properties` with `level-type=flat` and `online-mode=false` for local testing.
4. `plugins/LoftsFarm.jar` from `../build/`.

Then follow `../ACCEPTANCE.md`.
