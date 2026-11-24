// Read-only survey of lab/labfinal: full mechanical layout, power path, slime adhesion and generator definition.
const {archive, readNbt} = require('./audit-lib.cjs');
const jar = archive('bridge/libs/dimdoors-5.4.4.jar');
const prefix = 'resourcepacks/default/data/dimdoors/pockets/';
const schem = readNbt(jar.read(prefix + 'schematic/lab/labfinal.schem'));
const genRaw = jar.read(prefix + 'generators/lab/labfinal.json');
const gen = JSON.parse(genRaw.toString('utf8'));

const W = schem.Width, H = schem.Height, L = schem.Length;
const palette = new Map();
for (const [state, idx] of Object.entries(schem.Palette)) palette.set(idx, state);

// Decode varint block data
const cells = new Map();
let index = 0, value = 0, shift = 0;
for (const rawByte of schem.BlockData) {
  const b = rawByte & 0xff;
  value |= (b & 127) << shift;
  if (b & 128) { shift += 7; continue; }
  const x = index % W, y = Math.floor(index / (W * L)), z = Math.floor(index / W) % L;
  cells.set(`${x},${y},${z}`, palette.get(value));
  index++; value = 0; shift = 0;
}

const components = ['minecraft:redstone_wire','minecraft:sticky_piston','minecraft:piston_head','minecraft:slime_block','minecraft:repeater','minecraft:lever'];
const key = (x, y, z) => `${x},${y},${z}`;
const at = (x, y, z) => cells.get(key(x, y, z)) ?? null;
const typeOf = s => s ? s.split('[', 2)[0] : null;
const prop = (s, k) => {
  if (!s) return null;
  const body = s.slice(s.indexOf('[') + 1, s.lastIndexOf(']'));
  for (const part of body.split(',')) {
    const eq = part.indexOf('=');
    if (eq >= 0 && part.slice(0, eq) === k) return part.slice(eq + 1);
  }
  return null;
};

console.log('=== Generator definition (lab/labfinal) ===');
console.log(genRaw.toString('utf8'));

console.log('\n=== Component inventory ===');
const found = [];
for (let y = 0; y < H; y++) for (let z = 0; z < L; z++) for (let x = 0; x < W; x++) {
  const s = at(x, y, z);
  if (!s) continue;
  const t = typeOf(s);
  if (components.includes(t)) {
    found.push({x, y, z, state: s});
  }
}
for (const f of found) console.log(`${f.x},${f.y},${f.z}  ${f.state}`);

// Piston analysis
console.log('\n=== Piston heads and their bases ===');
const heads = found.filter(f => f.state.startsWith('minecraft:piston_head'));
for (const h of heads) {
  const facing = prop(h.state, 'facing');
  const dir = {north:[0,0,-1],south:[0,0,1],east:[1,0,0],west:[-1,0,0],up:[0,1,0],down:[0,-1,0]}[facing];
  const base = `${h.x - dir[0]},${h.y - dir[1]},${h.z - dir[2]}`;
  console.log(`head ${h.x},${h.y},${h.z} facing=${facing} type=${prop(h.state,'type')} -> base at ${base} = ${at(h.x-dir[0],h.y-dir[1],h.z-dir[2])}`);
}

// Slime block: find piston that can push it
console.log('\n=== Slime block adjacency (piston-relevant) ===');
const slimes = found.filter(f => f.state.startsWith('minecraft:slime_block'));
for (const s of slimes) {
  console.log(`slime at ${s.x},${s.y},${s.z}`);
  for (const [name, dx, dy, dz] of [['north',0,0,-1],['south',0,0,1],['east',1,0,0],['west',-1,0,0],['up',0,1,0],['down',0,-1,0]]) {
    const n = at(s.x + dx, s.y + dy, s.z + dz);
    if (n && n !== 'minecraft:air') console.log(`  ${name}: ${n}`);
  }
}

// Redstone power sources: lever, repeater
console.log('\n=== Redstone neighbourhood (wire around lever/repeaters) ===');
for (const f of found.filter(f => f.state.startsWith('minecraft:lever') || f.state.startsWith('minecraft:repeater'))) {
  console.log(`${f.x},${f.y},${f.z} ${f.state}`);
  for (const [name, dx, dy, dz] of [['north',0,0,-1],['south',0,0,1],['east',1,0,0],['west',-1,0,0],['up',0,1,0],['down',0,-1,0]]) {
    const n = at(f.x + dx, f.y + dy, f.z + dz);
    console.log(`  ${name}: ${n ?? '(out of bounds)'}`);
  }
}

// Entities
console.log('\n=== Entities (positions) ===');
const count = new Map();
for (const e of schem.Entities) {
  count.set(e.Id, (count.get(e.Id) ?? 0) + 1);
  if (e.Id === 'minecraft:painting' || e.Id === 'minecraft:armor_stand') {
    const p = e.Pos; console.log(`${e.Id} at ${p.map(v => Math.round(v)).join(',')}`);
  }
}
console.log([...count.entries()].map(([k, v]) => `${k}: ${v}`).join('  '));

// Corridor content
console.log('\n=== P2 east corridor (x=11..23, y=91, z=58) ===');
for (let x = 11; x <= 23; x++) console.log(`  (${x},91,58): ${at(x, 91, 58)}`);
console.log('\n=== P1 up corridor (x=12, y=91..104, z=58) ===');
for (let y = 91; y <= 104; y++) console.log(`  (12,${y},58): ${at(12, y, 58)}`);
console.log('\n=== Slime neighbours (12,92,58) full ===');
for (const [name, dx, dy, dz] of [['north',0,0,-1],['south',0,0,1],['east',1,0,0],['west',-1,0,0],['up',0,1,0],['down',0,-1,0]]) {
  console.log(`  ${name}: ${at(12 + dx, 92 + dy, 58 + dz)}`);
}
console.log('\n=== quartz south-of-slime (12,92,59) neighbours ===');
for (const [name, dx, dy, dz] of [['north',0,0,-1],['south',0,0,1],['east',1,0,0],['west',-1,0,0],['up',0,1,0],['down',0,-1,0]]) {
  console.log(`  ${name}: ${at(12 + dx, 92 + dy, 59 + dz)}`);
}
console.log('\n=== quartz above-slime (12,93,58) neighbours ===');
for (const [name, dx, dy, dz] of [['north',0,0,-1],['south',0,0,1],['east',1,0,0],['west',-1,0,0],['up',0,1,0],['down',0,-1,0]]) {
  console.log(`  ${name}: ${at(12 + dx, 93 + dy, 58 + dz)}`);
}
console.log('\n=== above lever (11,94..97,62) ===');
for (let y = 94; y <= 97; y++) console.log(`  (11,${y},62): ${at(11, y, 62)}`);

// Air pocket locations within bounding box (potential piston travel corridors)
console.log('\n=== Non-air cells adjacent to piston heads (travel corridor) ===');
for (const h of heads) {
  const facing = prop(h.state, 'facing');
  const dir = {north:[0,0,-1],south:[0,0,1],east:[1,0,0],west:[-1,0,0],up:[0,1,0],down:[0,-1,0]}[facing];
  for (let d = 1; d <= 13; d++) {
    const s = at(h.x + dir[0]*d, h.y + dir[1]*d, h.z + dir[2]*d);
    if (s === null) { console.log(`head ${h.x},${h.y},${h.z} +${d} ${facing}: out of bounds`); break; }
    if (s !== 'minecraft:air' && !s.startsWith('minecraft:air')) {
      console.log(`head ${h.x},${h.y},${h.z} +${d} ${facing}: ${s}`);
      break;
    }
  }
}
