package ma.hiresync.cv.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw CV text (from PDFBox) into a structured CV map.
 *
 * Used as a FALLBACK for optimizations created before the AI structured-rebuild
 * existed (optimizedCvJson == null). It is regex/heuristic based — good for
 * standard French CVs with CAPS section headers and "•" bullets, but the
 * AI-rebuilt structure is always preferred when available.
 *
 * Recognised sections: FORMATION, EXPÉRIENCE, PROJETS, COMPÉTENCES, LANGUES, DIVERS.
 */
@Component
@Slf4j
public class CvTextParser {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s.-]{7,}\\d)");
    private static final Pattern YEARS = Pattern.compile("\\d{4}\\s*[-–]\\s*\\d{4}|\\b\\d{4}\\b|[A-Za-zûéà]+\\s+\\d{4}\\s*[-–]\\s*[A-Za-zûéà]+\\s+\\d{4}|[A-Za-zûéà]+\\s+\\d{4}");

    private enum Sec { HEADER, FORMATION, EXPERIENCE, PROJETS, COMPETENCES, LANGUES, DIVERS, OTHER }

    public Map<String, Object> parse(String rawText, String fallbackJobTitle) {
        var result = new LinkedHashMap<String, Object>();

        // ── Normalise lines ──────────────────────────────────────────────────
        List<String> lines = new ArrayList<>();
        for (String l : rawText.split("\\r?\\n")) {
            String t = clean(l);
            if (!t.isBlank()) lines.add(t);
        }
        if (lines.isEmpty()) return minimal(fallbackJobTitle);

        // ── Group lines by section ───────────────────────────────────────────
        Map<Sec, List<String>> groups = new EnumMap<>(Sec.class);
        Sec current = Sec.HEADER;
        for (String line : lines) {
            Sec h = detectHeader(line);
            if (h != null) { current = h; groups.computeIfAbsent(h, k -> new ArrayList<>()); continue; }
            groups.computeIfAbsent(current, k -> new ArrayList<>()).add(line);
        }

        // ── Header block: name / title / contact / summary ───────────────────
        var header = groups.getOrDefault(Sec.HEADER, List.of());
        String name = header.isEmpty() ? "Candidat" : header.get(0);

        String email = "", phone = "", location = "";
        String title = "";
        var summaryParts = new ArrayList<String>();

        for (int i = 1; i < header.size(); i++) {
            String l = header.get(i);
            Matcher em = EMAIL.matcher(l);
            if (em.find()) { email = em.group(); continue; }
            Matcher ph = PHONE.matcher(l);
            if (ph.find() && l.replaceAll("[^0-9]", "").length() >= 8 && l.length() < 30) {
                phone = ph.group().trim(); continue;
            }
            // Location: short line with a city marker or "–"
            if (location.isEmpty() && l.length() < 40 && (l.contains("–") || l.contains("-") || hasCity(l))) {
                location = l.replaceAll("^[\\s\\d–-]+", "").trim();
                continue;
            }
            // Title: ALL-CAPS line (not the name)
            if (title.isEmpty() && isAllCaps(l) && l.length() < 50) { title = l; continue; }
            // Skip bio lines like "23 ans, Célibataire, permis B."
            if (l.matches("(?i).*\\b\\d{1,2}\\s*ans\\b.*") || l.matches("(?i).*permis\\s+[A-D].*")) continue;
            // Otherwise part of the summary
            if (!isAllCaps(l)) summaryParts.add(l);
        }

        result.put("fullName", capitalizeName(name));
        result.put("jobTitle", !title.isBlank() ? capitalizeName(title) : fallbackJobTitle);
        result.put("contact", Map.of(
            "email", email, "phone", phone, "location", location, "linkedin", ""));
        result.put("summary", String.join(" ", summaryParts).trim());

        // ── Experience ───────────────────────────────────────────────────────
        result.put("experience", parseExperience(joinBullets(groups.getOrDefault(Sec.EXPERIENCE, List.of()))));

        // ── Education ────────────────────────────────────────────────────────
        result.put("education", parseEducation(joinBullets(groups.getOrDefault(Sec.FORMATION, List.of()))));

        // ── Skills ───────────────────────────────────────────────────────────
        result.put("skills", parseSkills(joinBullets(groups.getOrDefault(Sec.COMPETENCES, List.of()))));

        // ── Languages ────────────────────────────────────────────────────────
        result.put("languages", parseLanguages(joinBullets(groups.getOrDefault(Sec.LANGUES, List.of()))));

        return result;
    }

    // ── Section header detection ─────────────────────────────────────────────
    private Sec detectHeader(String line) {
        if (line.length() > 35) return null;
        String u = stripAccents(line.toUpperCase());
        if (!isAllCaps(line)) {
            // allow headers that are exactly a known keyword even if mixed case
        }
        if (u.startsWith("FORMATION") || u.startsWith("EDUCATION")) return Sec.FORMATION;
        if (u.startsWith("EXPERIENCE")) return Sec.EXPERIENCE;
        if (u.startsWith("PROJET")) return Sec.PROJETS;
        if (u.startsWith("COMPETENCE") || u.startsWith("SKILLS")) return Sec.COMPETENCES;
        if (u.startsWith("LANGUE") || u.startsWith("LANGUAGE")) return Sec.LANGUES;
        if (u.startsWith("DIVERS") || u.startsWith("CENTRE") || u.startsWith("HOBB")) return Sec.DIVERS;
        return null;
    }

    /** Merge wrapped continuation lines into their preceding "•" bullet. */
    private List<String> joinBullets(List<String> lines) {
        var out = new ArrayList<String>();
        for (String l : lines) {
            boolean isBullet = l.startsWith("•") || l.startsWith("-") || l.startsWith("*");
            if (isBullet || out.isEmpty()) {
                out.add(l.replaceFirst("^[•*\\-]\\s*", "").trim());
            } else {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + l.trim());
            }
        }
        return out;
    }

    // ── Experience parsing ───────────────────────────────────────────────────
    private List<Map<String, Object>> parseExperience(List<String> bullets) {
        var list = new ArrayList<Map<String, Object>>();
        for (String b : bullets) {
            String dates = extractDates(b);
            String rest  = b;
            if (!dates.isEmpty()) rest = rest.replace(dates, "").replaceFirst("^[\\s:–-]+", "").trim();

            // role = text before first comma or quote; company = after " - " near end
            String role = rest, company = "";
            Matcher q = Pattern.compile("[«\"]([^»\"]+)[»\"]").matcher(rest);
            String description = "";
            if (q.find()) {
                description = q.group(1).trim();
                role = rest.substring(0, rest.indexOf(q.group())).replaceAll("[,:]\\s*$", "").trim();
            }
            int dash = rest.lastIndexOf(" - ");
            if (dash < 0) dash = rest.lastIndexOf(" – ");
            if (dash > 0) company = rest.substring(dash + 3).replaceAll("[.\\s]+$", "").trim();

            var bulletsList = new ArrayList<String>();
            if (!description.isEmpty()) bulletsList.add(capFirst(description));
            else bulletsList.add(capFirst(rest));

            list.add(new LinkedHashMap<>(Map.of(
                "role", role.isBlank() ? "Expérience" : capFirst(cut(role, 70)),
                "company", company.isBlank() ? "" : cut(company, 60),
                "dates", dates,
                "bullets", bulletsList
            )));
            if (list.size() >= 4) break;
        }
        return list;
    }

    // ── Education parsing ────────────────────────────────────────────────────
    private List<Map<String, Object>> parseEducation(List<String> bullets) {
        var list = new ArrayList<Map<String, Object>>();
        for (String b : bullets) {
            String dates = extractDates(b);
            String rest  = b;
            if (!dates.isEmpty()) rest = rest.replace(dates, "").replaceFirst("^[\\s:–-]+", "").trim();

            String degree = rest, school = "";
            int dash = rest.lastIndexOf(" - ");
            if (dash < 0) dash = rest.lastIndexOf(" – ");
            if (dash > 0) {
                degree = rest.substring(0, dash).trim();
                school = rest.substring(dash + 3).replaceAll("[.\\s]+$", "").trim();
            }
            degree = degree.replaceAll("[«»\"]", " ").replaceAll("\\s+", " ").replaceAll("[.\\s]+$", "").trim();

            list.add(new LinkedHashMap<>(Map.of(
                "degree", cut(degree, 90),
                "school", cut(school, 60),
                "dates", dates
            )));
            if (list.size() >= 5) break;
        }
        return list;
    }

    // ── Skills parsing ───────────────────────────────────────────────────────
    private List<String> parseSkills(List<String> bullets) {
        var skills = new LinkedHashSet<String>();
        for (String b : bullets) {
            // "Category: s1, s2, s3"  → take part after ":"
            String part = b.contains(":") ? b.substring(b.indexOf(':') + 1) : b;
            for (String s : part.split("[,;/]")) {
                String t = s.replaceAll("[.()]", " ").trim();
                t = t.replaceAll("\\b(notions?|connaissances?)\\b", "").trim();
                if (t.length() >= 2 && t.length() <= 28 && !t.matches(".*\\d{4}.*")) skills.add(t);
            }
        }
        return new ArrayList<>(skills).stream().limit(20).toList();
    }

    // ── Languages parsing ────────────────────────────────────────────────────
    private List<String> parseLanguages(List<String> bullets) {
        var langs = new ArrayList<String>();
        for (String b : bullets) {
            String t = b.replace(":", " — ").replaceAll("[.\\s]+$", "").trim();
            if (!t.isBlank()) langs.add(cut(t, 40));
        }
        return langs;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /**
     * Strip PDF icon glyphs (Private Use Area U+E000–U+F8FF, used by FontAwesome-style
     * icons embedded in CV PDFs), control chars, and collapse whitespace.
     */
    private String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xE000 && c <= 0xF8FF) continue;   // Private Use Area (PDF icons)
            if (c == 0xFEFF) continue;                  // BOM
            if (c == 0x0D || c == 0x09) { sb.append(' '); continue; }
            if (Character.getType(c) == Character.CONTROL) continue;
            sb.append(c);
        }
        // keep \u00AB \u00BB here \u2014 experience/education parsing relies on them to find
        // quoted descriptions; they are stripped later at display points.
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String extractDates(String s) {
        // "Avril 2024 – Juillet 2024" | "Février 2023" | "2024-2026"
        Matcher m = Pattern.compile(
            "([A-Za-zéûôàÉ]+\\s+\\d{4}\\s*[–-]\\s*[A-Za-zéûôàÉ]+\\s+\\d{4})" +
            "|([A-Za-zéûôàÉ]+\\s+\\d{4})" +
            "|(\\d{4}\\s*[–-]\\s*\\d{4})" +
            "|(\\d{4})").matcher(s);
        return m.find() ? m.group().trim() : "";
    }

    private boolean isAllCaps(String s) {
        String letters = s.replaceAll("[^A-Za-zÀ-ÿ]", "");
        if (letters.length() < 3) return false;
        return letters.chars().filter(Character::isLetter)
                      .allMatch(c -> Character.toUpperCase(c) == c);
    }

    private boolean hasCity(String l) {
        String u = stripAccents(l.toLowerCase());
        return List.of("casablanca","rabat","temara","kenitra","laayoune","tanger",
                       "marrakech","fes","agadir","meknes","oujda","sale","mohammedia")
                   .stream().anyMatch(u::contains);
    }

    private String capitalizeName(String s) {
        if (s == null || s.isBlank()) return s;
        // Keep ALL-CAPS names as Title Case for nicer display
        if (isAllCaps(s)) {
            String[] words = s.toLowerCase().split("\\s+");
            var sb = new StringBuilder();
            for (String w : words) {
                if (w.isBlank()) continue;
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
            return sb.toString().trim();
        }
        return s;
    }

    private String capFirst(String s) {
        s = s.trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String cut(String s, int max) {
        s = s.trim();
        return s.length() > max ? s.substring(0, max).trim() + "…" : s;
    }

    private String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                   .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private Map<String, Object> minimal(String jobTitle) {
        var m = new LinkedHashMap<String, Object>();
        m.put("fullName", "Candidat");
        m.put("jobTitle", jobTitle);
        m.put("contact", Map.of("email", "", "phone", "", "location", "", "linkedin", ""));
        m.put("summary", "");
        m.put("experience", List.of());
        m.put("education", List.of());
        m.put("skills", List.of());
        m.put("languages", List.of());
        return m;
    }
}
