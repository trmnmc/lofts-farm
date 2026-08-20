// The game is authored as an artifact *body* — it opens at <meta name="viewport">
// with no doctype, <html>, or <meta charset>. Served raw that yields quirks mode
// and windows-1252 decoding (mangled em-dashes). Wrap it into a real document.
//
// The fragment leads with head content (meta/title/style) and then body content,
// so the parser closes <head> and opens <body> on its own at the first element.
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'

const fragment = await readFile(new URL('./src/app.html', import.meta.url), 'utf8')

if (/<!doctype|<html[\s>]/i.test(fragment)) {
  throw new Error('src/app.html already looks like a full document — wrapper would nest <html>. Update build.mjs.')
}

const stamp = createHash("sha256").update(fragment).digest("hex").slice(0,7)
const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
${fragment.trimEnd()}
</body>
</html>
`

await mkdir(new URL('./public/', import.meta.url), { recursive: true })
const stamped = html.replace(/__BUILD__/g, stamp)
await writeFile(new URL('./public/index.html', import.meta.url), stamped, 'utf8')
console.log(`built public/index.html (${Buffer.byteLength(stamped)+" build "+stamp} bytes)`)
