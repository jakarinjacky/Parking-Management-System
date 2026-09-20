/* Minimal native XLSX export: UTF-8 OOXML in a ZIP archive, no network dependency.
 * Text cells are explicitly inline strings, so user input cannot become formulas.
 */
(() => {
    'use strict';
    const encoder = new TextEncoder();
    const xml = value => String(value ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
    function zip(files) {
        const local = [], central = [];
        let offset = 0, centralSize = 0;
        const header = size => new DataView(new ArrayBuffer(size));
        for (const [path, text] of Object.entries(files)) {
            const name = encoder.encode(path), data = encoder.encode(text);
            let crc = 0xffffffff;
            for (const byte of data) {
                crc ^= byte;
                for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
            }
            crc = (crc ^ 0xffffffff) >>> 0;
            const h = header(30);
            h.setUint32(0, 0x04034b50, true); h.setUint16(4, 20, true);
            h.setUint16(12, 33, true); // ZIP minimum date: 1980-01-01
            h.setUint32(14, crc, true); h.setUint32(18, data.length, true); h.setUint32(22, data.length, true);
            h.setUint16(26, name.length, true);
            local.push(h.buffer, name, data);
            const c = header(46);
            c.setUint32(0, 0x02014b50, true); c.setUint16(4, 20, true); c.setUint16(6, 20, true);
            c.setUint16(14, 33, true); c.setUint32(16, crc, true);
            c.setUint32(20, data.length, true); c.setUint32(24, data.length, true);
            c.setUint16(28, name.length, true); c.setUint32(42, offset, true);
            central.push(c.buffer, name); centralSize += 46 + name.length;
            offset += 30 + name.length + data.length;
        }
        const end = header(22), count = Object.keys(files).length;
        end.setUint32(0, 0x06054b50, true); end.setUint16(8, count, true); end.setUint16(10, count, true);
        end.setUint32(12, centralSize, true); end.setUint32(16, offset, true);
        return new Blob([...local, ...central, end.buffer], {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
    }
    function workbook(records, filters) {
        const rows = [
            ['ประวัติรถเข้าออก', `ตั้งแต่ ${filters[0]} ถึง ${filters[1]}`, `ทะเบียน: ${filters[2] || 'ทั้งหมด'}`],
            ['Ticket ID', 'ทะเบียนรถ', 'ประเภทรถ', 'ชั้น', 'ช่องจอด', 'เวลาเข้า (ตามระบบ)', 'เวลาออก (ตามระบบ)', 'สถานะ', 'ค่าบริการ (บาท)'],
            ...records.map(r => [r.ticketId, r.licensePlate, r.vehicleTypeDisplay || r.vehicleType,
                Number(r.floorNumber || 0), r.slotNumber, r.entryTime, r.exitTime || '',
                r.status === 'EXITED' ? 'ออกแล้ว' : 'ยังอยู่ในลาน', Number(r.fee || 0)])
        ];
        const sheet = rows.map((row, i) => `<row r="${i + 1}">${row.map((value, j) => {
            const ref = `${String.fromCharCode(65 + j)}${i + 1}`;
            return typeof value === 'number' && Number.isFinite(value)
                ? `<c r="${ref}"${j === 8 ? ' s="1"' : ''}><v>${value}</v></c>`
                : `<c r="${ref}" t="inlineStr"><is><t xml:space="preserve">${xml(value)}</t></is></c>`;
        }).join('')}</row>`).join('');
        const ns = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
        const rel = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
        return zip({
            '[Content_Types].xml': '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>',
            '_rels/.rels': `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="${rel}/officeDocument" Target="xl/workbook.xml"/></Relationships>`,
            'xl/workbook.xml': `<workbook xmlns="${ns}" xmlns:r="${rel}"><sheets><sheet name="ประวัติรถเข้าออก" sheetId="1" r:id="rId1"/></sheets></workbook>`,
            'xl/_rels/workbook.xml.rels': `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="${rel}/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="${rel}/styles" Target="styles.xml"/></Relationships>`,
            'xl/styles.xml': `<styleSheet xmlns="${ns}"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`,
            'xl/worksheets/sheet1.xml': `<worksheet xmlns="${ns}"><sheetViews><sheetView workbookViewId="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="3" width="28" customWidth="1"/><col min="4" max="5" width="12" customWidth="1"/><col min="6" max="7" width="28" customWidth="1"/><col min="8" max="9" width="20" customWidth="1"/></cols><sheetData>${sheet}</sheetData><autoFilter ref="A2:I${rows.length}"/></worksheet>`
        });
    }
    let records = [], filters = [];
    const readFilters = () => ['historyFrom', 'historyTo', 'historyPlate'].map(id => document.getElementById(id)?.value.trim() || '');
    window.setHistoryExportRecords = items => {
        records = items.map(item => ({...item})); filters = readFilters();
        const button = document.getElementById('historyExportButton');
        if (button) button.disabled = !records.length;
    };
    for (const id of ['historyFrom', 'historyTo', 'historyPlate']) {
        document.getElementById(id)?.addEventListener('input', () => window.setHistoryExportRecords([]));
    }
    window.exportParkingHistory = () => {
        const message = document.getElementById('historyExportMessage');
        if (!records.length || JSON.stringify(filters) !== JSON.stringify(readFilters())) {
            message.textContent = 'กรุณาค้นหาข้อมูลตามตัวกรองก่อนดาวน์โหลด'; return;
        }
        try {
            const url = URL.createObjectURL(workbook(records, filters));
            const link = document.createElement('a');
            link.href = url; link.download = `parking-history_${filters[0]}_${filters[1]}.xlsx`;
            document.body.appendChild(link); link.click(); link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 10000);
            message.textContent = `ส่งออก ${records.length} รายการเป็น Excel แล้ว`;
        } catch (error) { message.textContent = 'ส่งออกไม่สำเร็จ กรุณาลองใหม่'; }
    };
})();
