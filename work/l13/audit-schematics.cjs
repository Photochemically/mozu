// Read-only audit of schematic NBT inside the exact installed-version JAR.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const jar = fs.readFileSync(path.join(__dirname, 'bridge/libs/dimdoors-5.4.4.jar'));
const structuresOnly = process.argv.includes('--structures');
let end = jar.length - 22;
while (end >= Math.max(0, jar.length - 65557) && jar.readUInt32LE(end) !== 0x06054b50) end--;
if (end < 0) throw new Error('ZIP directory missing');
let cursor = jar.readUInt32LE(end + 16);
const count = jar.readUInt16LE(end + 10);
const entries = [];
for (let i = 0; i < count; i++) {
  if (jar.readUInt32LE(cursor) !== 0x02014b50) throw new Error('Bad ZIP entry');
  const len = jar.readUInt16LE(cursor + 28), extra = jar.readUInt16LE(cursor + 30), comment = jar.readUInt16LE(cursor + 32);
  const name = jar.toString('utf8', cursor + 46, cursor + 46 + len);
  const method = jar.readUInt16LE(cursor + 10), size = jar.readUInt32LE(cursor + 20), local = jar.readUInt32LE(cursor + 42);
  cursor += 46 + len + extra + comment;
  if (structuresOnly ? !/^data\/.*\.(nbt|schem)$/.test(name) : (!name.startsWith('resourcepacks/') || !name.endsWith('.schem'))) continue;
  const offset = local + 30 + jar.readUInt16LE(local + 26) + jar.readUInt16LE(local + 28);
  const packed = jar.subarray(offset, offset + size);
  let data;
  if (method === 0) data = packed;
  else if (method === 8) data = zlib.inflateRawSync(packed);
  else throw new Error('Unsupported ZIP method ' + method);
  if (data[0] === 0x1f && data[1] === 0x8b) data = zlib.gunzipSync(data);
  entries.push({ name, nbt: readNbt(data) });
}
function readNbt(data) {
  let p = 0;
  const take = (n) => { if (n < 0 || p + n > data.length) throw new Error('Truncated NBT'); const a = p; p += n; return a; };
  const u8 = () => data.readUInt8(take(1));
  const i32 = () => data.readInt32BE(take(4));
  const str = () => { const n = data.readUInt16BE(take(2)); const a = take(n); return data.toString('utf8', a, a + n); };
  function value(type) {
    switch (type) {
      case 1: return data.readInt8(take(1));
      case 2: return data.readInt16BE(take(2));
      case 3: return i32();
      case 4: return data.readBigInt64BE(take(8)).toString();
      case 5: return data.readFloatBE(take(4));
      case 6: return data.readDoubleBE(take(8));
      case 7: { const n = i32(), a = take(n); return data.subarray(a, a + n); }
      case 8: return str();
      case 9: { const child = u8(), n = i32(); if(n < 0 || n > data.length) throw new Error('Invalid NBT list'); return Array.from({length:n}, () => value(child)); }
      case 10: { const result = {}; for (let t = u8(); t !== 0; t = u8()) { const key = str(); result[key] = value(t); } return result; }
      case 11: { const n = i32(); if(n < 0 || n > data.length / 4) throw new Error('Invalid int array'); return Array.from({length:n}, i32); }
      case 12: { const n = i32(); if(n < 0 || n > data.length / 8) throw new Error('Invalid long array'); return Array.from({length:n}, () => value(4)); }
      default: throw new Error('Unsupported NBT tag ' + type);
    }
  }
  const type = u8(); str(); const result = value(type);
  if (p !== data.length) throw new Error('Trailing NBT data');
  return result;
}
const report = entries.map(({name, nbt}) => {
  const palette = nbt.palette ? nbt.palette.map(p=>p.Name) : Object.keys(nbt.Palette || nbt.Blocks?.Palette || {});
  const entities = nbt.BlockEntities || nbt.Blocks?.BlockEntities || (nbt.blocks || []).filter(b=>b.nbt).map(b=>({...b.nbt,Pos:b.pos}));
  return {
    path:name, dimensions:nbt.size || [nbt.Width,nbt.Height,nbt.Length],
    goldBlocks:palette.filter(x=>/gold.*door/.test(x)),
    containers:entities.filter(e=>/chest|barrel/.test(e.Id || e.id || '')).map(e=>({id:e.Id||e.id,pos:e.Pos,loot:e.LootTable,items:e.Items?.length})),
    hazards:palette.filter(x=>/spawner|tnt|lava|dispenser|tripwire|pressure_plate/.test(x)),
    rifts:entities.filter(e=>/rift/.test(e.Id || e.id || '')),
    entities:nbt.Entities?.length || 0
  };
});
fs.writeFileSync(path.join(__dirname,structuresOnly?'audit/dimdoors-structure-nbt-audit.json':'audit/dimdoors-schematic-audit.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify({schematics:report.length, goldRooms:report.filter(r=>r.goldBlocks.length).map(r=>({path:r.path,rifts:r.rifts})),treasure:report.filter(r=>/treasure/.test(r.path))},null,2));
