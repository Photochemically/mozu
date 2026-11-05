// Local archive/NBT metadata reader. Never executes embedded commands or extracts archive paths.
const fs=require('node:fs');
const zlib=require('node:zlib');
const crypto=require('node:crypto');
function archive(file) {
  const buffer=fs.readFileSync(file),entries=new Map();let end=buffer.length-22;
  while(end>=Math.max(0,buffer.length-65557) && buffer.readUInt32LE(end)!==0x06054b50)end--;
  if(end<0 || buffer.readUInt32LE(end)!==0x06054b50)throw Error('ZIP directory missing');
  let p=buffer.readUInt32LE(end+16);const count=buffer.readUInt16LE(end+10);
  for(let i=0;i<count;i++) {
    if(buffer.readUInt32LE(p)!==0x02014b50)throw Error('Invalid ZIP entry');
    const n=buffer.readUInt16LE(p+28),extra=buffer.readUInt16LE(p+30),comment=buffer.readUInt16LE(p+32);
    const name=buffer.toString('utf8',p+46,p+46+n);
    if(entries.has(name))throw Error('Duplicate ZIP entry: '+name);
    entries.set(name,{method:buffer.readUInt16LE(p+10),compressed:buffer.readUInt32LE(p+20),size:buffer.readUInt32LE(p+24),local:buffer.readUInt32LE(p+42)});
    p+=46+n+extra+comment;
  }
  return {names:[...entries.keys()],sha256:crypto.createHash('sha256').update(buffer).digest('hex'),read(name) {
    const e=entries.get(name);if(!e)throw Error('Missing ZIP entry: '+name);
    if(e.size>128*1024*1024)throw Error('Oversized entry: '+name);
    const offset=e.local+30+buffer.readUInt16LE(e.local+26)+buffer.readUInt16LE(e.local+28);
    if(offset+e.compressed>buffer.length)throw Error('Truncated ZIP payload');
    const packed=buffer.subarray(offset,offset+e.compressed);
    const data=e.method===0?packed:e.method===8?zlib.inflateRawSync(packed,{maxOutputLength:128*1024*1024}):null;
    if(!data || data.length!==e.size)throw Error('Unsupported or damaged ZIP payload: '+name);return data;
  }};
}
function readNbt(input) {
  const data=input[0]===0x1f && input[1]===0x8b?zlib.gunzipSync(input,{maxOutputLength:128*1024*1024}):input;
  let p=0;
  const take=n=>{if(!Number.isSafeInteger(n)||n<0||p+n>data.length)throw Error('Truncated NBT');const at=p;p+=n;return at;};
  const byte=()=>data.readUInt8(take(1)),integer=()=>data.readInt32BE(take(4));
  const string=()=>{const n=data.readUInt16BE(take(2)),at=take(n);return data.toString('utf8',at,at+n);};
  function value(type,depth=0) {
    if(depth>128)throw Error('NBT nesting limit');
    switch(type) {
      case 1:return data.readInt8(take(1));case 2:return data.readInt16BE(take(2));case 3:return integer();
      case 4:return data.readBigInt64BE(take(8)).toString();case 5:return data.readFloatBE(take(4));case 6:return data.readDoubleBE(take(8));
      case 7:{const n=integer(),at=take(n);return data.subarray(at,at+n);}case 8:return string();
      case 9:{const type=byte(),n=integer();if(n<0||n>data.length||(type===0&&n!==0))throw Error('Invalid NBT list');return Array.from({length:n},()=>value(type,depth+1));}
      case 10:{const out=Object.create(null);for(let type=byte();type;type=byte()){const key=string();if(Object.hasOwn(out,key))throw Error('Duplicate NBT key');out[key]=value(type,depth+1);}return out;}
      case 11:{const n=integer();if(n<0||n>data.length/4)throw Error('Invalid NBT int array');return Array.from({length:n},integer);}
      case 12:{const n=integer();if(n<0||n>data.length/8)throw Error('Invalid NBT long array');return Array.from({length:n},()=>value(4,depth+1));}
      default:throw Error('Unsupported NBT type '+type);
    }
  }
  if(byte()!==10)throw Error('NBT root must be a compound');string();const result=value(10);
  if(p!==data.length)throw Error('Trailing NBT data');return result;
}
const identifier=value=>value.includes(':')?value:'minecraft:'+value;
function reachable(start,pools,templates) {
  const todo=[identifier(start)],seen=new Set(),locations=new Set(),missing=new Set(),unsupported=new Set();
  function element(e) {
    if(e.element_type==='minecraft:empty_pool_element')return;
    if(e.element_type==='minecraft:list_pool_element'){for(const child of e.elements||[])element(child);unsupported.add(e.element_type);return;}
    if(!['minecraft:single_pool_element','minecraft:legacy_single_pool_element'].includes(e.element_type)){unsupported.add(e.element_type||'missing');return;}
    const location=identifier(e.location);locations.add(location);const template=templates.get(location);
    if(!template){missing.add('template:'+location);return;}
    for(const pool of template.jigsawPools)todo.push(pool);
  }
  while(todo.length) {
    const id=todo.pop();if(id==='minecraft:empty'||seen.has(id))continue;seen.add(id);
    const pool=pools.get(id);if(!pool){missing.add('pool:'+id);continue;}
    if(pool.fallback)todo.push(identifier(pool.fallback));
    for(const item of pool.elements||[])if(item.weight>0)element(item.element);
  }
  return {pools:[...seen].sort(),templates:[...locations].sort(),missing:[...missing].sort(),unsupported:[...unsupported].sort()};
}
module.exports={archive,readNbt,identifier,reachable};
