package com.maxwell.chronos.config;
import com.maxwell.chronos.service.CompanyLetterRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Freeze previously issued letters before accepting settings changes. */
@Component @RequiredArgsConstructor
public class LetterSnapshotMigration implements ApplicationRunner {
    private final CompanyLetterRequestService letters;
    @Override public void run(ApplicationArguments args){letters.freezeHistoricalLetters();}
}
