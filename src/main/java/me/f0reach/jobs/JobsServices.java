package me.f0reach.jobs;

import me.f0reach.jobs.antiautomation.AntiAutomationCheck;
import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.antiautomation.AutoFedProcessingCheck;
import me.f0reach.jobs.antiautomation.BreedNonPlayerBreederCheck;
import me.f0reach.jobs.antiautomation.OperatorTracker;
import me.f0reach.jobs.antiautomation.PlacementRecorder;
import me.f0reach.jobs.antiautomation.PlantedFlagWriter;
import me.f0reach.jobs.antiautomation.RecentlyPlacedBreakCheck;
import me.f0reach.jobs.antiautomation.SpawnerOriginCheck;
import me.f0reach.jobs.antiautomation.TradeRecorder;
import me.f0reach.jobs.antiautomation.UnplantedCropCheck;
import me.f0reach.jobs.antiautomation.VillagerRepeatTradeCheck;
import me.f0reach.jobs.config.ConfigLoader;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.native_.BlockBreakListener;
import me.f0reach.jobs.detection.native_.BlockPlaceListener;
import me.f0reach.jobs.detection.native_.BreedListener;
import me.f0reach.jobs.detection.native_.BrewListener;
import me.f0reach.jobs.detection.native_.ConsumeListener;
import me.f0reach.jobs.detection.native_.CraftListener;
import me.f0reach.jobs.detection.native_.EnchantListener;
import me.f0reach.jobs.detection.native_.EntityKilledListener;
import me.f0reach.jobs.detection.native_.FishListener;
import me.f0reach.jobs.detection.native_.FurnaceExtractListener;
import me.f0reach.jobs.detection.native_.RepairListener;
import me.f0reach.jobs.detection.native_.ShearListener;
import me.f0reach.jobs.detection.native_.TameListener;
import me.f0reach.jobs.detection.native_.VillagerTradeListener;
import me.f0reach.jobs.detection.tnt.TntPrimerTracker;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.economy.VaultEconomyAdapter;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.i18n.LocaleRegistry;
import me.f0reach.jobs.i18n.MissingKeyReporter;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import me.f0reach.jobs.listener.PlayerJoinListener;
import me.f0reach.jobs.listener.SpecialtyChangedListener;
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.dailycap.DailyTotalCache;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.async.ActionLogWriteQueue;
import me.f0reach.jobs.persistence.async.BatchFlushWorker;
import me.f0reach.jobs.persistence.mysql.MySqlActionLogRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDailyRewardTotalRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDataSource;
import me.f0reach.jobs.persistence.mysql.MySqlPlayerJobRepository;
import me.f0reach.jobs.persistence.mysql.SchemaInitializer;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.pipeline.stage.ActionLogStage;
import me.f0reach.jobs.pipeline.stage.AntiAutomationStage;
import me.f0reach.jobs.pipeline.stage.BaseRewardStage;
import me.f0reach.jobs.pipeline.stage.BuiltinModifierStage;
import me.f0reach.jobs.pipeline.stage.EconomyTransferStage;
import me.f0reach.jobs.pipeline.stage.MatcherStage;
import me.f0reach.jobs.pipeline.stage.RareRollStage;
import me.f0reach.jobs.pipeline.stage.RewardRoundingStage;
import me.f0reach.jobs.pipeline.stage.SpecialtyStage;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.registry.ShadowDetector;
import me.f0reach.jobs.registry.TagResolver;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.ui.DialogService;
import me.f0reach.jobs.ui.SpecialtyChangeDialog;
import me.f0reach.jobs.ui.SpecialtySelectDialog;
import me.f0reach.jobs.util.AsyncExecutor;
import me.f0reach.jobs.yaml.JobYamlLoader;
import me.f0reach.jobs.yaml.YamlErrors;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * 起動時に組み立てた全 service の参照を持つ facade。
 */
public final class JobsServices {

    private static final long DEFAULT_KVS_MAX_ENTRIES = 500_000L;
    private static final int ACTION_LOG_QUEUE_CAPACITY = 100_000;
    private static final long BATCH_DRAIN_TIMEOUT_MS = 30_000;

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

    private VaultEconomyAdapter economy;
    private ActionLogWriteQueue actionLogQueue;
    private BatchFlushWorker batchFlushWorker;
    private RewardMatcher rewardMatcher;
    private RewardPipeline rewardPipeline;
    private EventDispatcher eventDispatcher;

    private VarietyPenaltyEvaluator varietyPenaltyEvaluator;
    private DailyTotalCache dailyTotalCache;
    private DailyCapEvaluator dailyCapEvaluator;

    private AntiAutomationCoordinator antiAutomationCoordinator;
    private PlantedFlagWriter plantedFlagWriter;
    private PlacementRecorder placementRecorder;
    private TradeRecorder tradeRecorder;
    private OperatorTracker operatorTracker;

    public JobsServices(JobsPlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = new AsyncExecutor(plugin);
    }

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
        wireEconomy();
        wireSpecialty();
        wireDialogs();
        wireBuiltinModifiers();
        wireAntiAutomation();
        wirePipeline();
        registerListeners();
    }

    private void wireAntiAutomation() {
        UnplantedCropCheck unplanted = new UnplantedCropCheck(plugin);
        this.plantedFlagWriter = new PlantedFlagWriter(unplanted.key());
        this.placementRecorder = new PlacementRecorder(kvStore);
        this.tradeRecorder = new TradeRecorder(kvStore);
        // operator_ttl_sec は job ごとに違い得るが、Phase 7 では簡便にジョブ全域で最大値を採用する。
        int operatorTtlSec = jobRegistry.all().stream()
                .map(j -> j.antiAutomation() == null ? null : j.antiAutomation().autoFedProcessing())
                .filter(java.util.Objects::nonNull)
                .mapToInt(cfg -> cfg.operatorTtlSec())
                .max().orElse(60);
        this.operatorTracker = new OperatorTracker(kvStore, operatorTtlSec);

        List<AntiAutomationCheck> checks = List.of(
                new SpawnerOriginCheck(),
                unplanted,
                new RecentlyPlacedBreakCheck(kvStore),
                new AutoFedProcessingCheck(kvStore),
                new VillagerRepeatTradeCheck(kvStore),
                new BreedNonPlayerBreederCheck()
        );
        this.antiAutomationCoordinator = new AntiAutomationCoordinator(plugin, checks);
    }

    private void wireBuiltinModifiers() {
        this.varietyPenaltyEvaluator = new VarietyPenaltyEvaluator(plugin, actionLogRepository, asyncExecutor);
        this.dailyTotalCache = new DailyTotalCache(
                plugin,
                dailyRewardTotalRepository,
                actionLogRepository,
                asyncExecutor,
                java.time.Clock.systemUTC(),
                ZoneId.systemDefault(),
                config.dailyCap().scope()
        );
        this.dailyCapEvaluator = new DailyCapEvaluator(dailyTotalCache, config.dailyCap());
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

    private void wireEconomy() {
        this.economy = VaultEconomyAdapter.setup();
    }

    private void wireSpecialty() {
        CooldownPolicy policy = new CooldownPolicy(config.specialtyMode().changePolicy());
        this.specialtyService = new SpecialtyService(plugin, playerJobRepository, jobRegistry, policy);
    }

    private void wireDialogs() {
        this.dialogService = new DialogService(asyncExecutor);
        this.specialtySelectDialog = new SpecialtySelectDialog(i18n, jobRegistry, specialtyService, dialogService);
        this.specialtyChangeDialog = new SpecialtyChangeDialog(i18n, jobRegistry, specialtyService, dialogService);
    }

    private void wirePipeline() {
        this.actionLogQueue = new ActionLogWriteQueue(ACTION_LOG_QUEUE_CAPACITY);
        this.batchFlushWorker = new BatchFlushWorker(
                plugin,
                actionLogQueue,
                actionLogRepository,
                dailyRewardTotalRepository,
                ZoneId.systemDefault());
        batchFlushWorker.start();

        RandomGenerator rng = new SplittableRandom();
        this.rewardMatcher = new RewardMatcher(tagResolver);
        List<Stage> stages = List.of(
                new MatcherStage(),
                new SpecialtyStage(specialtyService),
                new AntiAutomationStage(antiAutomationCoordinator),
                new BaseRewardStage(rng),
                new RareRollStage(rng),
                new BuiltinModifierStage(varietyPenaltyEvaluator, dailyCapEvaluator),
                // ExtensionModifierStage / SplitterStage は Phase 8
                new RewardRoundingStage(plugin, config.reward()),
                new EconomyTransferStage(plugin, economy),
                new ActionLogStage(plugin, actionLogQueue, batchFlushWorker, asyncExecutor)
        // AdvancementRevokeStage は Phase 9
        );
        this.rewardPipeline = new RewardPipeline(plugin, jobRegistry, stages);
        this.eventDispatcher = new EventDispatcher(specialtyService, jobRegistry, rewardMatcher, rewardPipeline);
    }

    private void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();

        pm.registerEvents(
                new PlayerJoinListener(
                        specialtyService,
                        specialtySelectDialog,
                        varietyPenaltyEvaluator,
                        dailyTotalCache,
                        jobRegistry,
                        config.specialtyMode().showSelectDialogOnJoin()),
                plugin);
        pm.registerEvents(
                new SpecialtyChangedListener(varietyPenaltyEvaluator, jobRegistry),
                plugin);

        for (Listener listener : List.of(
                new EntityKilledListener(eventDispatcher),
                new BlockBreakListener(eventDispatcher),
                new BlockPlaceListener(eventDispatcher, plantedFlagWriter, placementRecorder,
                        specialtyService, jobRegistry),
                new FishListener(eventDispatcher),
                new FurnaceExtractListener(eventDispatcher),
                new CraftListener(eventDispatcher),
                new EnchantListener(eventDispatcher),
                new RepairListener(eventDispatcher),
                new BreedListener(eventDispatcher),
                new TameListener(eventDispatcher),
                new ShearListener(eventDispatcher),
                new ConsumeListener(eventDispatcher),
                new VillagerTradeListener(eventDispatcher, tradeRecorder, specialtyService, jobRegistry),
                new BrewListener(eventDispatcher),
                new TntPrimerTracker(plugin, eventDispatcher),
                operatorTracker)) {
            pm.registerEvents(listener, plugin);
        }
    }

    /** サンプルとして同梱している職業定義。plugins/Jobs/jobs/ が空のときのみ展開する。 */
    private static final List<String> DEFAULT_JOB_RESOURCES = List.of(
            "jobs/combat.yml",
            "jobs/mining.yml",
            "jobs/farming.yml",
            "jobs/crafter.yml",
            "jobs/explorer.yml");

    public void loadJobs() {
        File jobsDir = new File(plugin.getDataFolder(), "jobs");
        if (!jobsDir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            jobsDir.mkdirs();
        }
        ensureDefaultJobsInstalled(jobsDir);

        JobYamlLoader loader = new JobYamlLoader(actionKeyDeriver);
        JobYamlLoader.LoadResult result = loader.loadDirectory(jobsDir);
        for (YamlErrors.Entry error : result.errors().entries()) {
            plugin.getLogger().warning(
                    "[" + error.file() + "] " + error.path() + ": " + error.message());
        }
        jobRegistry.loadAll(result.jobs());

        int totalRewards = result.jobs().stream().mapToInt(j -> j.rewards().size()).sum();
        plugin.getLogger().info(
                result.jobs().size() + " jobs, " + totalRewards + " rewards loaded");
    }

    /**
     * plugins/Jobs/jobs/ に *.yml が 1 つも無ければ、jar 内のサンプルを saveResource で展開する。
     * 既存の YAML があれば触らない。ユーザが空にすれば再展開される。
     */
    private void ensureDefaultJobsInstalled(File jobsDir) {
        File[] existing = jobsDir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (existing != null && existing.length > 0)
            return;

        for (String resource : DEFAULT_JOB_RESOURCES) {
            try {
                plugin.saveResource(resource, false);
            } catch (IllegalArgumentException e) {
                // jar 内にリソースが無い場合のみ発生。同梱漏れとして WARNING。
                plugin.getLogger().warning("Bundled resource missing: " + resource);
            }
        }
        plugin.getLogger().info(
                "Installed " + DEFAULT_JOB_RESOURCES.size() + " default job files to plugins/"
                        + plugin.getName() + "/jobs/");
    }

    public void runShadowDetection() {
        tagResolver.invalidateAll();
        for (JobDefinition job : jobRegistry.all()) {
            for (ShadowDetector.ShadowWarning warning : shadowDetector.detect(job)) {
                plugin.getLogger().log(Level.WARNING,
                        "Shadow in job '" + warning.jobId() + "': " + warning.reason());
            }
        }
    }

    public void shutdown() {
        // 停止順は threading.md 「停止時 (onDisable)」に従う。
        if (batchFlushWorker != null) {
            batchFlushWorker.drainAndStop(BATCH_DRAIN_TIMEOUT_MS);
            batchFlushWorker = null;
        }
        asyncExecutor.shutdown();
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public JobsPlugin plugin() {
        return plugin;
    }

    public AsyncExecutor asyncExecutor() {
        return asyncExecutor;
    }

    public PluginConfig config() {
        return config;
    }

    public LocaleRegistry localeRegistry() {
        return localeRegistry;
    }

    public I18n i18n() {
        return i18n;
    }

    public JobRegistry jobRegistry() {
        return jobRegistry;
    }

    public TagResolver tagResolver() {
        return tagResolver;
    }

    public ActionKeyDeriver actionKeyDeriver() {
        return actionKeyDeriver;
    }

    public JobsKVStore kvStore() {
        return kvStore;
    }

    public PlayerJobRepository playerJobRepository() {
        return playerJobRepository;
    }

    public ActionLogRepository actionLogRepository() {
        return actionLogRepository;
    }

    public DailyRewardTotalRepository dailyRewardTotalRepository() {
        return dailyRewardTotalRepository;
    }

    public SpecialtyService specialtyService() {
        return specialtyService;
    }

    public DialogService dialogService() {
        return dialogService;
    }

    public SpecialtySelectDialog specialtySelectDialog() {
        return specialtySelectDialog;
    }

    public SpecialtyChangeDialog specialtyChangeDialog() {
        return specialtyChangeDialog;
    }

    public VaultEconomyAdapter economy() {
        return economy;
    }

    public ActionLogWriteQueue actionLogQueue() {
        return actionLogQueue;
    }

    public BatchFlushWorker batchFlushWorker() {
        return batchFlushWorker;
    }

    public RewardMatcher rewardMatcher() {
        return rewardMatcher;
    }

    public RewardPipeline rewardPipeline() {
        return rewardPipeline;
    }

    public EventDispatcher eventDispatcher() {
        return eventDispatcher;
    }

    public VarietyPenaltyEvaluator varietyPenaltyEvaluator() {
        return varietyPenaltyEvaluator;
    }

    public DailyTotalCache dailyTotalCache() {
        return dailyTotalCache;
    }

    public DailyCapEvaluator dailyCapEvaluator() {
        return dailyCapEvaluator;
    }
}
