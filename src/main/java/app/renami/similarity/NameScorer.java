package app.renami.similarity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * String similarity in [0..1]: combination of normalized Levenshtein ratio and token-set Jaccard.
 * Deliberately simple and explainable; swap in fancier metrics per-datasource later.
 */
public final class NameScorer {

    private NameScorer() {
    }

    public static String canonical(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9 ]", " ");
        return n.replaceAll("\\s{2,}", " ").trim();
    }

    /** Levenshtein similarity: 1 - distance/maxLen. */
    public static double levenshteinSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 1.0 : 1.0 - (double) distance / maxLen;
    }

    /** Jaccard index over word sets. */
    public static double tokenSimilarity(String a, String b) {
        Set<String> ta = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.split(" ")));
        if (ta.isEmpty() && tb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** Overall score in [0..1] over already-canonical strings. */
    public static double score(String canonicalA, String canonicalB) {
        double lev = levenshteinSimilarity(canonicalA, canonicalB);
        double jac = tokenSimilarity(canonicalA, canonicalB);
        double base = 0.6 * lev + 0.4 * jac;
        // containment boost: "breaking bad" vs "breaking bad 2008" is a strong match
        if (!canonicalA.isEmpty() && (canonicalA.contains(canonicalB) || canonicalB.contains(canonicalA))) {
            base = Math.max(base, 0.9);
        }
        return base;
    }

    public static double scoreRaw(String a, String b) {
        return score(canonical(a), canonical(b));
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
