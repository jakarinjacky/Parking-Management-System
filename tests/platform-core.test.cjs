const assert = require('node:assert/strict');
const C = require('../web/js/platform-core.js');
for (const type of Object.keys(C.templates)) {
  const cells = C.template(type);
  if (type === 'CUSTOM') assert.deepEqual(cells, []);
  else {
    assert.deepEqual(C.validate(cells), []);
    assert(C.route(cells, 'A2').length > 1);
    const blocked = cells.filter(c => c.id !== 'R1');
    assert(C.validate(blocked).some(e => e.includes('ทางเข้า')));
  }
}
const backwards=C.template('CONDO');
backwards.filter(c=>c.type==='ROAD').forEach(c=>{c.oneWay=true;c.rotation=2;});
assert(C.validate(backwards).length>0);
const duplicate=C.template('MALL'); duplicate.push({...duplicate[0]});
assert(C.validate(duplicate).some(e=>e.includes('ซ้ำ')));
const floor=C.template('HOTEL'); floor.find(c=>c.id==='A2').floor=2;
assert(C.validate(floor).length>0);
assert.equal(C.preview().sites.length,3);
assert(C.compatible('CAR','STANDARD'));
assert(!C.compatible('CAR','EV_CHARGING'));
assert(!C.compatible('MOTORCYCLE','STANDARD'));
assert(C.compatible('MOTORCYCLE','MOTORCYCLE'));
assert(C.compatible('TRUCK','LARGE'));
const sample=C.sampleLayout('CONDO');
assert.equal(C.validate(sample).length,0);
assert.equal(sample.filter(c=>c.type==='SLOT').length,40);
assert(sample.some(c=>c.floor===2&&c.type==='ENTRY'));
assert(C.route(sample,'F2A2').length>1);
assert.equal(C.validate(C.sampleLayout('CUSTOM')).length,0);
console.log('Platform domain JS: templates, paths, one-way, overlap, floors PASS');

// Execute with only getRandomValues, as on the deployed HTTP origin.
const vm=require('node:vm');
const context={crypto:{getRandomValues:array=>require('node:crypto').webcrypto.getRandomValues(array)}};
vm.runInNewContext(require('node:fs').readFileSync(require.resolve('../web/js/platform-core.js'),'utf8'),context);
const ids=Array.from({length:1000},()=>context.PlatformCore.createId());
assert.equal(new Set(ids).size,1000);
assert(ids.every(id=>/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id)));
