import { readFile, readdir, writeFile } from 'node:fs/promises';

const lock = JSON.parse(await readFile(new URL('./package-lock.json', import.meta.url)));
const sections = ['Signet Campus acceptance tools — third-party notices\n\nThese packages are development-only and are not included in the browser bundle or application server. Original application code is MIT licensed; upstream components retain their own licenses.'];
for (const [path, entry] of Object.entries(lock.packages).sort(([a], [b]) => a.localeCompare(b))) {
  if (!path) continue;
  const directory = new URL(`./${path}/`, import.meta.url);
  const pkg = JSON.parse(await readFile(new URL('package.json', directory)));
  const files = (await readdir(directory)).filter(name => /^(licen[cs]e|copying|notice)(\.|$)/i.test(name)).sort();
  if (!files.length) throw new Error(`Missing license file: ${pkg.name}`);
  sections.push(`${pkg.name}@${pkg.version}\nLicense: ${pkg.license}\nSource: ${typeof pkg.repository === 'object' ? pkg.repository.url : pkg.repository || entry.resolved}`);
  for (const file of files) sections.push(`${file}\n${await readFile(new URL(file, directory), 'utf8')}`);
}
await writeFile(new URL('./THIRD_PARTY_NOTICES.txt', import.meta.url), sections.join('\n\n' + '='.repeat(72) + '\n\n') + '\n');
console.log('Acceptance dependency notices generated from the lockfile');
