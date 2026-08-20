# Lofts Farm — site

The Lofts Farm economy sim, served as a static site from Cloudflare Workers at
**https://farm.fenley.ai**.

The whole app is one self-contained file (`public/index.html`) — inline CSS and JS,
no external requests, state kept in `localStorage`. There is no build step.

## Layout

    public/index.html   the app
    public/_headers     security headers (not served as an asset)
    wrangler.jsonc      Worker + custom domain config

## Prerequisites

- `fenley.ai` must already be a zone in the Cloudflare account you log in as
  (i.e. its nameservers point at Cloudflare). `custom_domain` routes can only be
  created for zones on the same account.

## Deploy

    npm install
    npx wrangler login     # one-time, opens a browser
    npm run deploy

The `custom_domain: true` route in `wrangler.jsonc` makes Cloudflare create and
manage the `farm.fenley.ai` DNS record automatically — nothing to add by hand
in the dashboard. Certificate issuance takes a couple of minutes on first deploy.

Each deploy also publishes to `lofts-farm.<your-subdomain>.workers.dev`.

## Local preview

    npm run dev           # http://localhost:8787

## Updating the app

Replace `public/index.html` and run `npm run deploy`. Assets are content-hashed
and uploaded incrementally, so redeploys only push what changed.
