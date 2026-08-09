/**
 * Parser QRP/EMF extraído de docs/poc/index.html (prova de conceito).
 * Mantido como referência legível; a implementação de produção é o port Java.
 * Não executar como fonte de verdade do MVP — use InterPdvQrpParser.
 */
export function u32(view, offset) {
  return view.getUint32(offset, true);
}
export function i32(view, offset) {
  return view.getInt32(offset, true);
}
export function isEmfAt(bytes, view, i) {
  return (
    i + 56 <= bytes.length &&
    u32(view, i) === 1 &&
    bytes[i + 40] === 0x20 &&
    bytes[i + 41] === 0x45 &&
    bytes[i + 42] === 0x4d &&
    bytes[i + 43] === 0x46
  );
}
export function findEmfPages(buffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  const pages = [];
  for (let i = 0; i + 56 < bytes.length; i++) {
    if (!isEmfAt(bytes, view, i)) continue;
    const nBytes = u32(view, i + 48);
    if (nBytes > 80 && i + nBytes <= bytes.length) {
      pages.push({ start: i, length: nBytes });
      i += nBytes - 1;
    }
  }
  return pages;
}
export function parseEmfTexts(buffer, page, pageIndex) {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const dec = new TextDecoder("utf-16le");
  const out = [];
  let pos = page.start;
  const end = page.start + page.length;
  let guard = 0;
  while (pos + 8 <= end && guard++ < 100000) {
    const type = u32(view, pos);
    const size = u32(view, pos + 4);
    if (size < 8 || pos + size > end) break;
    if (type === 84 && size >= 76) {
      const x = i32(view, pos + 36);
      const y = i32(view, pos + 40);
      const chars = u32(view, pos + 44);
      const off = u32(view, pos + 48);
      const from = pos + off;
      const to = from + chars * 2;
      if (chars < 10000 && from >= pos && to <= pos + size) {
        const text = dec.decode(bytes.slice(from, to)).replace(/\0/g, "").trim();
        if (text) out.push({ page: pageIndex, x, y, text });
      }
    }
    pos += size;
    if (type === 14) break;
  }
  return out;
}
export const brNumber = (s) => {
  if (s == null || s === "") return null;
  let v = String(s).replace(/R\$\s*/g, "").trim();
  if (v.includes(",") && v.includes(".")) v = v.replace(/\./g, "").replace(",", ".");
  else if (v.includes(",")) v = v.replace(",", ".");
  v = v.replace(/[^0-9+\-.]/g, "");
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};
