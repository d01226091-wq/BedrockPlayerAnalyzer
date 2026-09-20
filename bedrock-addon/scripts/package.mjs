import { cp, mkdir, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const root = new URL('..', import.meta.url).pathname;
const out = join(root, 'dist');
await rm(out, { recursive: true, force: true });
await mkdir(join(out, 'BP/scripts'), { recursive: true });
await cp(join(root, 'BP/manifest.json'), join(out, 'BP/manifest.json'));
await cp(join(root, 'RP/manifest.json'), join(out, 'RP/manifest.json'));
const manifest = JSON.parse(await (await import('node:fs/promises')).readFile(join(out, 'BP/manifest.json'), 'utf8'));
manifest.modules[0].entry = 'scripts/main.js';
await writeFile(join(out, 'BP/manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log('Bedrock Analyzer build is in bedrock-addon/dist');
