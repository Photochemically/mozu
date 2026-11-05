const {test}=require('node:test'),assert=require('node:assert/strict');
const {readNbt,reachable}=require('./audit-lib.cjs');
test('NBT parser rejects truncation, negative arrays and duplicate keys',()=>{
  assert.deepEqual({...readNbt(Buffer.from([10,0,0,0]))},{});
  assert.throws(()=>readNbt(Buffer.from([10,0,0])));
  assert.throws(()=>readNbt(Buffer.from([10,0,0,7,0,1,120,255,255,255,255,0])));
  assert.throws(()=>readNbt(Buffer.from([10,0,0,1,0,1,120,1,1,0,1,120,2,0])));
});
test('Cyclic jigsaw graph and fallback terminate and include alternative templates only once',()=>{
  const single=id=>({weight:1,element:{element_type:'minecraft:single_pool_element',location:id}});
  const pools=new Map([['x:a',{fallback:'x:b',elements:[single('x:one')]}],['x:b',{fallback:'minecraft:empty',elements:[single('x:two'),single('x:one')]}]]);
  const templates=new Map([['x:one',{jigsawPools:['x:a']}],['x:two',{jigsawPools:['x:b']}]]);
  assert.deepEqual(reachable('x:a',pools,templates),{pools:['x:a','x:b'],templates:['x:one','x:two'],missing:[],unsupported:[]});
});
test('Missing resources and unsupported elements are explicit, zero weights are not candidates',()=>{
  const pools=new Map([['x:a',{fallback:'x:missing',elements:[{weight:1,element:{element_type:'minecraft:feature_pool_element'}},
    {weight:1,element:{element_type:'minecraft:single_pool_element',location:'x:gone'}},{weight:0,element:{element_type:'minecraft:single_pool_element',location:'x:disabled'}}]}]]);
  const r=reachable('x:a',pools,new Map());assert.deepEqual(r.missing,['pool:x:missing','template:x:gone']);
  assert.deepEqual(r.unsupported,['minecraft:feature_pool_element']);assert.deepEqual(r.templates,['x:gone']);
});
