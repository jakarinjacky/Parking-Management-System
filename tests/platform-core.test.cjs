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
console.log('Platform domain JS: templates, paths, one-way, overlap, floors PASS');
