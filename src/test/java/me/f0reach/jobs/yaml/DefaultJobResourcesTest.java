package me.f0reach.jobs.yaml;

import me.f0reach.jobs.registry.ActionKeyDeriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jar に同梱している 5 個のデフォルト職業定義がエラーなく読める（起動時の
 * ensureDefaultJobsInstalled で配置されたときの挙動と一致する）ことを確認する。
 */
class DefaultJobResourcesTest {

    private static final List<String> RESOURCES = List.of(
            "jobs/combat.yml",
            "jobs/mining.yml",
            "jobs/farming.yml",
            "jobs/smelting.yml",
            "jobs/fishing.yml"
    );

    @BeforeAll
    static void setUp() { MockBukkit.mock(); }

    @AfterAll
    static void tearDown() { MockBukkit.unmock(); }

    @Test
    void allDefaultJobsLoadWithoutErrors(@TempDir Path dir) throws Exception {
        for (String path : RESOURCES) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(in, "resource missing: " + path);
                Path target = dir.resolve(path.substring("jobs/".length()));
                Files.copy(in, target);
            }
        }
        JobYamlLoader.LoadResult result = new JobYamlLoader(new ActionKeyDeriver()).loadDirectory(dir.toFile());
        assertFalse(result.errors().hasErrors(),
                () -> "unexpected YAML errors: " + result.errors().entries());
        assertEquals(RESOURCES.size(), result.jobs().size());
        // それぞれに少なくとも 1 件の reward と description が入っていること。
        for (var job : result.jobs()) {
            assertTrue(job.rewards().size() > 0, "no rewards in " + job.id());
            assertNotNull(job.description(), "no description in " + job.id());
            assertFalse(job.description().isBlank(), "blank description in " + job.id());
        }
    }
}
