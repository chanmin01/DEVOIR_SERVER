package com.benly.archive.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CompanyNameNormalizer {

    private static final Pattern CORP_MARK = Pattern.compile("주식회사|\\(주\\)|㈜");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^0-9a-z가-힣]");

    private CompanyNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        s = CORP_MARK.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        return NOT_ALLOWED.matcher(s).replaceAll("");
    }
}
