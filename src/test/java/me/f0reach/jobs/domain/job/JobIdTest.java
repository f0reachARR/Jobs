package me.f0reach.jobs.domain.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobIdTest {

    @Test
    void acceptsLowerAlphaNumericUnderscore() {
        assertEquals("combat", new JobId("combat").value());
        assertEquals("mining_deep", new JobId("mining_deep").value());
        assertEquals("job1", new JobId("job1").value());
    }

    @Test
    void rejectsUppercase() {
        assertThrows(IllegalArgumentException.class, () -> new JobId("Combat"));
    }

    @Test
    void rejectsSpecialChars() {
        assertThrows(IllegalArgumentException.class, () -> new JobId("combat-x"));
        assertThrows(IllegalArgumentException.class, () -> new JobId("combat x"));
        assertThrows(IllegalArgumentException.class, () -> new JobId(""));
    }
}
