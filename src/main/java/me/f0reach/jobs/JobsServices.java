package me.f0reach.jobs;

import me.f0reach.jobs.config.ConfigLoader;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.i18n.LocaleRegistry;
import me.f0reach.jobs.i18n.MissingKeyReporter;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import me.f0reach.jobs.listener.PlayerJoinListener;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.mysql.MySqlActionLogRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDailyRewardTotalRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDataSource;
import me.f0reach.jobs.persistence.mysql.MySqlPlayerJobRepository;
import me.f0reach.jobs.persistence.mysql.SchemaInitializer;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.registry.ShadowDetector;
import me.f0reach.jobs.registry.TagResolver;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.ui.DialogService;
import me.f0reach.jobs.ui.SpecialtyChangeDialog;
import me.f0reach.jobs.ui.SpecialtySelectDialog;
import me.f0reach.jobs.ui.StatusDialog;
import me.f0reach.jobs.util.AsyncExecutor;
import me.f0reach.jobs.yaml.JobYamlLoader;
import me.f0reach.jobs.yaml.YamlErrors;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * 起動時に組み立てた全 service の参照を持つ facade。
 * Phase を追うごとに保持するサービスが増える。
 */
public final class JobsServices {
    /** InMemoryKVStore の maxEntries デフォルト。config で上書きされない場合に使う。 */
    private static final long DEFAULT_KVS_MAX_ENTRIES = 500_000L;

    private final JobsPlugin plugin;
    private final AsyncExecutor asyncExecutor;

    private PluginConfig config;
    private LocaleRegistry localeRegistry;
    private I18n i18n;

    private final JobRegistry jobRegistry = new JobRegistry();
    private final ActionKeyDeriver actionKeyDeriver = new ActionKeyDeriver();
    private final TagResolver tagResolver = new TagResolver();
    private final ShadowDetector shadowDetector = new ShadowDetector(tagResolver);

    private JobsKVStore kvStore;
    private MySqlDataSource dataSource;
    private PlayerJobRepository playerJobRepository;
    private ActionLogRepository actionLogRepository;
    private DailyRewardTotalRepository dailyRewardTotalRepository;

    private SpecialtyService specialtyService;
    private DialogService dialogService;
    private SpecialtySelectDialog specialtySelectDialog;
    private SpecialtyChangeDialog specialtyChangeDialog;
    private StatusDialog statusDialog;

    public JobsServices(JobsPlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = new AsyncExecutor(plugin);
    }

    /**
     * 起動時に全 service を wire する。
     * 順序は docs/plan/threading.md 「起動時 (onEnable)」に従う。
     */
    public void wire() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = new ConfigLoader().load(plugin.getConfig());

        this.localeRegistry = new LocaleRegistry(plugin);
        this.localeRegistry.load();
        this.i18n = new I18n(localeRegistry);
        new MissingKeyReporter(plugin, localeRegistry).report();

        wirePersistence();
        wireKvs();
        loadJobs();
        wireSpecialty();
        wireDialogs();
        registerListeners();
    }

    private void wirePersistence() {
        this.dataSource = new MySqlDataSource(config.persistence());
        try {
            dataSource.healthCheck();
            new SchemaInitializer(dataSource.dataSource()).initialize();
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize MySQL persistence", e);
        }
        this.playerJobRepository = new MySqlPlayerJobRepository(dataSource.dataSource());
        this.actionLogRepository = new MySqlActionLogRepository(dataSource.dataSource());
        this.dailyRewardTotalRepository = new MySqlDailyRewardTotalRepository(dataSource.dataSource());
    }

    private void wireKvs() {
        long maxEntries = plugin.getConfig().getLong("kvs.memory.max_entries", DEFAULT_KVS_MAX_ENTRIES);
        this.kvStore = new InMemoryKVStore(maxEntries);
    }

    private void wireSpecialty() {
        CooldownPolicy policy = new CooldownPolicy(config.specialtyMode().changePolicy());
        this.specialtyService = new SpecialtyService(plugin, playerJobRepository, jobRegistry, policy);
    }

    private void wireDialogs() {
        this.dialogService = new DialogService(asyncExecutor);
        this.specialtySelectDialog = new SpecialtySelectDialog(i18n, jobRegistry, specialtyService, dialogService);
        this.specialtyChangeDialog = new SpecialtyChangeDialog(i18n, jobRegistry, specialtyService, dialogService);
        this.statusDialog = new StatusDialog(i18n, specialtyService, dialogService);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(specialtyService, specialtySelectDialog),
                plugin
        );
    }

    public void loadJobs() {
        File jobsDir = new File(plugin.getDataFolder(), "jobs");
        if (!jobsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            jobsDir.mkdirs();
        }
        JobYamlLoader loader = new JobYamlLoader(actionKeyDeriver);
        JobYamlLoader.LoadResult result = loader.loadDirectory(jobsDir);
        for (YamlErrors.Entry error : result.errors().entries()) {
            plugin.getLogger().warning(
                    "[" + error.file() + "] " + error.path() + ": " + error.message()
            );
        }
        jobRegistry.loadAll(result.jobs());

        int totalRewards = result.jobs().stream().mapToInt(j -> j.rewards().size()).sum();
        plugin.getLogger().info(
                result.jobs().size() + " jobs, " + totalRewards + " rewards loaded"
        );
    }

    /**
     * サーバー起動完了後に呼ぶ。タグ resolve と shadow 検出を行う。
     */
    public void runShadowDetection() {
        tagResolver.invalidateAll();
        for (JobDefinition job : jobRegistry.all()) {
            for (ShadowDetector.ShadowWarning warning : shadowDetector.detect(job)) {
                plugin.getLogger().log(Level.WARNING,
                        "Shadow in job '" + warning.jobId() + "': " + warning.reason()
                );
            }
        }
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public JobsPlugin plugin() { return plugin; }
    public AsyncExecutor asyncExecutor() { return asyncExecutor; }
    public PluginConfig config() { return config; }
    public LocaleRegistry localeRegistry() { return localeRegistry; }
    public I18n i18n() { return i18n; }
    public JobRegistry jobRegistry() { return jobRegistry; }
    public TagResolver tagResolver() { return tagResolver; }
    public ActionKeyDeriver actionKeyDeriver() { return actionKeyDeriver; }
    public JobsKVStore kvStore() { return kvStore; }
    public PlayerJobRepository playerJobRepository() { return playerJobRepository; }
    public ActionLogRepository actionLogRepository() { return actionLogRepository; }
    public DailyRewardTotalRepository dailyRewardTotalRepository() { return dailyRewardTotalRepository; }
    public SpecialtyService specialtyService() { return specialtyService; }
    public DialogService dialogService() { return dialogService; }
    public SpecialtySelectDialog specialtySelectDialog() { return specialtySelectDialog; }
    public SpecialtyChangeDialog specialtyChangeDialog() { return specialtyChangeDialog; }
    public StatusDialog statusDialog() { return statusDialog; }
}
