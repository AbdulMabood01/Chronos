package com.maxwell.chronos.service;
import com.maxwell.chronos.domain.User;
import java.time.Instant;
public final class LegalTerms {
    public static final String VERSION="2026-10-09";
    private LegalTerms() {}
    public static void requireCurrent(String version){if(!VERSION.equals(version))throw new IllegalArgumentException("Accept the current Terms of Use before continuing");}
    public static void accept(User user,String version){requireCurrent(version);user.setTermsVersion(version);user.setTermsAcceptedAt(Instant.now());}
}
