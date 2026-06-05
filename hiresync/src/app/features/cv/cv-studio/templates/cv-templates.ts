import { StructuredCv, StudioOptions } from '../../../../core/models/studio.model';

/**
 * CV template renderers.
 *
 * Each function returns a COMPLETE standalone HTML document (A4-sized).
 * The same string is used for:
 *   - the live preview  → injected into an <iframe srcdoc>
 *   - the PDF download   → POSTed to the backend, rendered by headless Chrome
 *
 * Because both use the identical HTML, the preview is guaranteed to match the PDF.
 */

// ── HTML escaping (CVs contain user text) ─────────────────────────────────────
function esc(s: string | undefined | null): string {
  if (!s) return '';
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Shared base: A4 page reset + Google font import ──────────────────────────
function docShell(bodyHtml: string, font: string): string {
  return `<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  html, body { width:210mm; }
  body { font-family:${font}; color:#1A202C; -webkit-print-color-adjust:exact; print-color-adjust:exact; }
  .page { width:210mm; min-height:297mm; background:#fff; position:relative; }
  a { color:inherit; text-decoration:none; }
</style></head><body>${bodyHtml}</body></html>`;
}

// ══════════════════════════════════════════════════════════════════════════════
//  TEMPLATE 1 — MODERN (coloured sidebar + photo)
// ══════════════════════════════════════════════════════════════════════════════
export function renderModern(cv: StructuredCv, o: StudioOptions): string {
  const accent = o.accentColor;

  const photoBlock = (o.showPhoto && cv.photo)
    ? `<div style="width:120px;height:120px;border-radius:50%;overflow:hidden;margin:0 auto 22px;border:4px solid rgba(255,255,255,.25);">
         <img src="${cv.photo}" style="width:100%;height:100%;object-fit:cover;" alt="photo"/>
       </div>`
    : (o.showPhoto
        ? `<div style="width:120px;height:120px;border-radius:50%;background:rgba(255,255,255,.15);margin:0 auto 22px;display:flex;align-items:center;justify-content:center;font-size:42px;font-weight:700;color:#fff;">${esc(initials(cv.fullName))}</div>`
        : '');

  const contact = cv.contact || {};
  const contactItems = [
    contact.email    ? `<div style="margin-bottom:8px;word-break:break-all;">✉ ${esc(contact.email)}</div>` : '',
    contact.phone    ? `<div style="margin-bottom:8px;">☎ ${esc(contact.phone)}</div>` : '',
    contact.location ? `<div style="margin-bottom:8px;">⚲ ${esc(contact.location)}</div>` : '',
    contact.linkedin ? `<div style="margin-bottom:8px;word-break:break-all;">in ${esc(contact.linkedin)}</div>` : '',
  ].join('');

  const skills = (cv.skills || []).map(s =>
    `<div style="background:rgba(255,255,255,.12);padding:5px 11px;border-radius:6px;margin:0 6px 7px 0;display:inline-block;font-size:11.5px;">${esc(s)}</div>`
  ).join('');

  const languages = (cv.languages || []).map(l =>
    `<div style="margin-bottom:6px;font-size:12px;">${esc(l)}</div>`
  ).join('');

  const experience = (cv.experience || []).map(exp => `
    <div style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;align-items:baseline;">
        <span style="font-weight:700;font-size:14px;color:#1A202C;">${esc(exp.role)}</span>
        <span style="font-size:11px;color:#94A3B8;white-space:nowrap;">${esc(exp.dates)}</span>
      </div>
      <div style="font-size:12.5px;color:${accent};font-weight:600;margin-bottom:6px;">${esc(exp.company)}</div>
      <ul style="margin:0;padding-left:16px;">
        ${(exp.bullets || []).map(b => `<li style="font-size:12px;color:#475569;line-height:1.55;margin-bottom:3px;">${esc(b)}</li>`).join('')}
      </ul>
    </div>`).join('');

  const education = (cv.education || []).map(ed => `
    <div style="margin-bottom:12px;">
      <div style="font-weight:700;font-size:13px;">${esc(ed.degree)}</div>
      <div style="font-size:12px;color:${accent};font-weight:600;">${esc(ed.school)}</div>
      <div style="font-size:11px;color:#94A3B8;">${esc(ed.dates)}</div>
    </div>`).join('');

  const sidebarTitle = (t: string) =>
    `<div style="font-size:13px;font-weight:700;letter-spacing:1px;margin:22px 0 6px;color:#fff;">${t}</div>
     <div style="height:2px;width:34px;background:rgba(255,255,255,.5);margin-bottom:12px;"></div>`;

  const mainTitle = (t: string) =>
    `<div style="font-size:15px;font-weight:800;letter-spacing:.5px;color:${accent};margin:20px 0 4px;">${t}</div>
     <div style="height:2px;width:46px;background:${accent};margin-bottom:14px;"></div>`;

  const body = `
  <div class="page" style="display:flex;">
    <!-- Sidebar -->
    <aside style="width:34%;background:${accent};color:#fff;padding:34px 24px;">
      ${photoBlock}
      ${sidebarTitle('CONTACT')}
      <div style="font-size:12px;line-height:1.5;">${contactItems}</div>
      ${cv.skills?.length ? sidebarTitle('COMPÉTENCES') + skills : ''}
      ${cv.languages?.length ? sidebarTitle('LANGUES') + languages : ''}
    </aside>

    <!-- Main -->
    <main style="width:66%;padding:34px 30px;">
      <h1 style="font-size:28px;font-weight:800;line-height:1.1;color:#1A202C;">${esc(cv.fullName)}</h1>
      <div style="font-size:15px;color:${accent};font-weight:600;margin-top:4px;">${esc(cv.jobTitle)}</div>

      ${cv.summary ? mainTitle('PROFIL') + `<p style="font-size:12.5px;color:#475569;line-height:1.65;">${esc(cv.summary)}</p>` : ''}
      ${cv.experience?.length ? mainTitle('EXPÉRIENCE') + experience : ''}
      ${cv.education?.length ? mainTitle('FORMATION') + education : ''}
    </main>
  </div>`;

  return docShell(body, o.fontFamily);
}

// ══════════════════════════════════════════════════════════════════════════════
//  TEMPLATE 2 — CLASSIC (single column, ATS-safe, recruiter-friendly)
// ══════════════════════════════════════════════════════════════════════════════
export function renderClassic(cv: StructuredCv, o: StudioOptions): string {
  const accent = o.accentColor;
  const contact = cv.contact || {};

  const contactLine = [contact.email, contact.phone, contact.location, contact.linkedin]
    .filter(Boolean).map(esc).join('  |  ');

  const sectionTitle = (t: string) =>
    `<div style="font-size:14px;font-weight:700;letter-spacing:1.5px;color:${accent};margin:22px 0 4px;border-bottom:2px solid ${accent};padding-bottom:4px;">${t}</div>`;

  const photoHeader = (o.showPhoto && cv.photo)
    ? `<img src="${cv.photo}" style="width:84px;height:84px;border-radius:8px;object-fit:cover;float:right;margin-left:16px;" alt="photo"/>`
    : '';

  const experience = (cv.experience || []).map(exp => `
    <div style="margin:12px 0;">
      <div style="display:flex;justify-content:space-between;align-items:baseline;">
        <span style="font-weight:700;font-size:14px;">${esc(exp.role)} — ${esc(exp.company)}</span>
        <span style="font-size:11.5px;color:#64748B;white-space:nowrap;">${esc(exp.dates)}</span>
      </div>
      <ul style="margin:5px 0 0;padding-left:18px;">
        ${(exp.bullets || []).map(b => `<li style="font-size:12.5px;color:#334155;line-height:1.6;margin-bottom:3px;">${esc(b)}</li>`).join('')}
      </ul>
    </div>`).join('');

  const education = (cv.education || []).map(ed => `
    <div style="margin:8px 0;display:flex;justify-content:space-between;">
      <span><strong style="font-size:13px;">${esc(ed.degree)}</strong> — ${esc(ed.school)}</span>
      <span style="font-size:11.5px;color:#64748B;">${esc(ed.dates)}</span>
    </div>`).join('');

  const skills = (cv.skills || []).map(esc).join('  •  ');
  const languages = (cv.languages || []).map(esc).join('  •  ');

  const body = `
  <div class="page" style="padding:40px 46px;">
    <!-- Header -->
    <header style="border-bottom:3px solid ${accent};padding-bottom:16px;margin-bottom:6px;overflow:hidden;">
      ${photoHeader}
      <h1 style="font-size:30px;font-weight:800;color:#1A202C;letter-spacing:.5px;">${esc(cv.fullName)}</h1>
      <div style="font-size:15px;color:${accent};font-weight:600;margin:4px 0 10px;">${esc(cv.jobTitle)}</div>
      <div style="font-size:12px;color:#64748B;">${contactLine}</div>
    </header>

    ${cv.summary ? sectionTitle('PROFIL') + `<p style="font-size:12.5px;color:#334155;line-height:1.65;margin-top:6px;">${esc(cv.summary)}</p>` : ''}
    ${cv.experience?.length ? sectionTitle('EXPÉRIENCE PROFESSIONNELLE') + experience : ''}
    ${cv.education?.length ? sectionTitle('FORMATION') + education : ''}
    ${cv.skills?.length ? sectionTitle('COMPÉTENCES') + `<p style="font-size:12.5px;color:#334155;line-height:1.7;margin-top:6px;">${skills}</p>` : ''}
    ${cv.languages?.length ? sectionTitle('LANGUES') + `<p style="font-size:12.5px;color:#334155;margin-top:6px;">${languages}</p>` : ''}
  </div>`;

  return docShell(body, o.fontFamily);
}

// ── Dispatcher ────────────────────────────────────────────────────────────────
export function renderTemplate(cv: StructuredCv, o: StudioOptions): string {
  return o.template === 'classic' ? renderClassic(cv, o) : renderModern(cv, o);
}

function initials(name: string): string {
  return (name || '?').split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('');
}
