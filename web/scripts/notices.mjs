import { readFile, readdir, writeFile } from 'node:fs/promises'

const lock = JSON.parse(await readFile(new URL('../package-lock.json', import.meta.url), 'utf8'))
const root = new URL('../', import.meta.url)
const sections = ['Signet Campus browser bundle — third-party notices\n\nOriginal application code is MIT licensed. The following components retain their upstream licenses. Decorative artwork was generated for Signet Campus.']
for (const [path, entry] of Object.entries(lock.packages).sort(([a], [b]) => a.localeCompare(b))) {
  if (!path || entry.dev) continue
  const directory = new URL(`${path}/`, root)
  const pkg = JSON.parse(await readFile(new URL('package.json', directory), 'utf8'))
  const files = (await readdir(directory)).filter(name => /^(licen[cs]e|copying|notice|ofl)(\.|$)/i.test(name)).sort()
  if (!files.length) throw new Error(`Missing license file for ${pkg.name}`)
  sections.push(`${pkg.name}@${pkg.version}\nLicense: ${pkg.license}\nSource: ${typeof pkg.repository === 'object' ? pkg.repository.url : pkg.repository || entry.resolved}`)
  for (const file of files) sections.push(`${file}\n${await readFile(new URL(file, directory), 'utf8')}`)
}
await writeFile(new URL('public/THIRD_PARTY_NOTICES.txt', root), `${sections.join('\n\n' + '='.repeat(72) + '\n\n')}\n`)
console.log('Browser dependency notices generated from locked runtime packages.')
