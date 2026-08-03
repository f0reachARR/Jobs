package me.f0reach.jobs;

import me.f0reach.jobs.api.JobsApi;
import me.f0reach.jobs.api.JobsApiImpl;
import me.f0reach.jobs.api.lifecycle.JobsPluginReadyEvent;
import me.f0reach.jobs.api.specialty.PlayerJobServiceImpl;
import me.f0reach.jobs.antiautomation.AntiAutomationCheck;
import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.antiautomation.AntiAutomationNotifier;
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
import me.f0reach.jobs.detection.advancement.AdvancementDatapackInstaller;
import me.f0reach.jobs.detection.advancement.AdvancementListener;
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
import me.f0reach.jobs.economy.AmountFormatter;
import me.f0reach.jobs.economy.VaultAmountFormatter;
import me.f0reach.jobs.economy.VaultEconomyAdapter;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.i18n.LocaleRegistry;
import me.f0reach.jobs.i18n.MissingKeyReporter;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import me.f0reach.jobs.listener.PlayerJoinListener;
import me.f0reach.jobs.listener.SpecialtyChangedListener;
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.modifier.ExtensionModifierChain;
import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.dailycap.DailyTotalCache;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.persistence.ActionLogQueryServiceImpl;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.persistence.PlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.async.ActionLogWriteQueue;
import me.f0reach.jobs.persistence.async.BatchFlushWorker;
import me.f0reach.jobs.persistence.mysql.MySqlActionLogRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDailyRewardTotalRepository;
import me.f0reach.jobs.persistence.mysql.MySqlDataSource;
import me.f0reach.jobs.persistence.mysql.MySqlPlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.mysql.MySqlPlayerJobRepository;
import me.f0reach.jobs.persistence.mysql.SchemaInitializer;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.pipeline.async.MainThreadRewardDispatcher;
import me.f0reach.jobs.pipeline.async.MainWorkQueue;
import me.f0reach.jobs.pipeline.async.RewardDispatcher;
import me.f0reach.jobs.pipeline.async.RewardWorkQueue;
import me.f0reach.jobs.pipeline.async.RewardWorker;
import me.f0reach.jobs.pipeline.async.WorkerRewardDispatcher;
import me.f0reach.jobs.pipeline.stage.ActionLogStage;
import me.f0reach.jobs.pipeline.stage.AdvancementRevokeStage;
import me.f0reach.jobs.pipeline.stage.AntiAutomationStage;
import me.f0reach.jobs.pipeline.stage.BaseRewardStage;
import me.f0reach.jobs.pipeline.stage.BuiltinModifierStage;
import me.f0reach.jobs.pipeline.stage.EconomyTransferStage;
import me.f0reach.jobs.pipeline.stage.ExtensionModifierStage;
import me.f0reach.jobs.pipeline.stage.MatcherStage;
import me.f0reach.jobs.pipeline.stage.RareRollStage;
import me.f0reach.jobs.pipeline.stage.RewardRoundingStage;
import me.f0reach.jobs.pipeline.stage.SpecialtyStage;
import me.f0reach.jobs.pipeline.stage.SplitterStage;
import me.f0reach.jobs.splitter.SplitterChain;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.registry.ShadowDetector;
import me.f0reach.jobs.registry.TagResolver;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.ui.DialogService;
import me.f0reach.jobs.ui.JobConditionsDialog;
import me.f0reach.jobs.ui.JobConditionsFormatter;
import me.f0reach.jobs.ui.SpecialtyCooldownDialog;
import me.f0reach.jobs.ui.SpecialtyListDialog;
import me.f0reach.jobs.util.AsyncExecutor;
import me.f0reach.jobs.yaml.JobYamlLoader;
import me.f0reach.jobs.yaml.YamlErrors;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

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
    private PlayerJobHistoryRepository playerJobHistoryRepository;
    private ActionLogRepository actionLogRepository;
    private DailyRewardTotalRepository dailyRewardTotalRepository;

    private SpecialtyService specialtyService;
    private DialogService dialogService;
    private JobConditionsFormatter jobConditionsFormatter;
    private JobConditionsDialog jobConditionsDialog;
    private SpecialtyListDialog specialtyListDialog;
    private SpecialtyCooldownDialog specialtyCooldownDialog;

    private VaultEconomyAdapter economy;
    private AmountFormatter amountFormatter;
    private ActionLogWriteQueue actionLogQueue;
    private BatchFlushWorker batchFlushWorker;
    private RewardMatcher rewardMatcher;
    private RewardPipeline rewardPipeline;
    private EventDispatcher eventDispatcher;

    private RewardWorkQueue rewardWorkQueue;
    private RewardWorker rewardWorker;
    private MainWorkQueue mainWorkQueue;
    private RewardDispatcher rewardDispatcher;
    private BukkitTask mainWorkDrainTask;

    private VarietyPenaltyEvaluator varietyPenaltyEvaluator;
    private DailyTotalCache dailyTotalCache;
    private DailyCapEvaluator dailyCapEvaluator;

    private AntiAutomationCoordinator antiAutomationCoordinator;
    private AntiAutomationNotifier antiAutomationNotifier;
    private PlantedFlagWriter plantedFlagWriter;
    private PlacementRecorder placementRecorder;
    private TradeRecorder tradeRecorder;
    private OperatorTracker operatorTracker;

    private ExtensionModifierChain extensionModifierChain;
    private SplitterChain splitterChain;
    private ActionLogQueryServiceImpl queryService;
    private JobsApi jobsApi;

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
        wireRewardAsync();
        wireBuiltinModifiers();
        wireAntiAutomation();
        wireExtensions();
        wirePipeline();
        registerListeners();
    }

    private void wireExtensions() {
        // ワーカーは 1 本なので、遅い拡張実装は全プレイヤーの報酬処理を詰まらせる。
        var slowReporter = new me.f0reach.jobs.modifier.SlowExtensionReporter(
                plugin.getLogger(), config.reward().async().slowExtensionThresholdMs());
        this.extensionModifierChain = new ExtensionModifierChain(plugin, slowReporter);
        this.splitterChain = new SplitterChain(plugin, slowReporter);
        this.queryService = new ActionLogQueryServiceImpl(actionLogRepository, asyncExecutor);
        PlayerJobServiceImpl playerJobService =
                new PlayerJobServiceImpl(specialtyService, playerJobRepository, asyncExecutor);
        this.jobsApi = new JobsApiImpl(extensionModifierChain, splitterChain, queryService, playerJobService);
    }

    /**
     * onEnable の最後に呼ぶ。外部プラグインが Modifier / Splitter を register する契機を通知する。
     * 呼び出しは main thread から (Bukkit event の同期発火)。
     */
    public void fireReadyEvent() {
        plugin.getServer().getPluginManager().callEvent(new JobsPluginReadyEvent(jobsApi));
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
                .filter(cfg -> cfg.enabled())
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
        this.antiAutomationNotifier = new AntiAutomationNotifier(
                i18n, config.antiAutomation().notifyActionBar());
    }

    /**
     * 報酬パイプラインの非同期実行基盤を組む。
     *
     * <p>{@code DailyTotalCache} と {@code VarietyPenaltyEvaluator} が
     * {@link RewardDispatcher} を要求するので、{@link #wireBuiltinModifiers()} より先に呼ぶ。
     * docs/plan/async-reward-pipeline.md を参照。
     */
    private void wireRewardAsync() {
        PluginConfig.AsyncConfig async = config.reward().async();
        this.mainWorkQueue = new MainWorkQueue(plugin.getLogger(), async.mainWorkPerTick());
        if (!async.enabled()) {
            // 全段階を main thread で同期実行する（非同期化前の挙動）。
            this.rewardDispatcher = new MainThreadRewardDispatcher(asyncExecutor);
            plugin.getLogger().info("reward.async.enabled=false: running the whole pipeline on the main thread");
            return;
        }
        this.rewardWorkQueue = new RewardWorkQueue(
                plugin.getLogger(), async.queueCapacity(), async.backlogWarnRatios());
        this.rewardWorker = new RewardWorker(plugin.getLogger(), rewardWorkQueue);
        this.rewardWorker.start();
        this.rewardDispatcher = new WorkerRewardDispatcher(rewardWorkQueue);
    }

    private void wireBuiltinModifiers() {
        this.varietyPenaltyEvaluator = new VarietyPenaltyEvaluator(
                plugin, actionLogRepository, asyncExecutor, rewardDispatcher);
        this.dailyTotalCache = new DailyTotalCache(
                plugin,
                dailyRewardTotalRepository,
                actionLogRepository,
                asyncExecutor,
                rewardDispatcher,
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
        this.playerJobHistoryRepository = new MySqlPlayerJobHistoryRepository(dataSource.dataSource());
        this.actionLogRepository = new MySqlActionLogRepository(dataSource.dataSource());
        this.dailyRewardTotalRepository = new MySqlDailyRewardTotalRepository(dataSource.dataSource());
    }

    private void wireKvs() {
        long maxEntries = plugin.getConfig().getLong("kvs.memory.max_entries", DEFAULT_KVS_MAX_ENTRIES);
        this.kvStore = new InMemoryKVStore(maxEntries);
    }

    private void wireEconomy() {
        this.economy = VaultEconomyAdapter.setup();
        this.amountFormatter = new VaultAmountFormatter(economy);
    }

    private void wireSpecialty() {
        CooldownPolicy policy = new CooldownPolicy(config.specialtyMode().changePolicy());
        this.specialtyService = new SpecialtyService(
                plugin, playerJobRepository, playerJobHistoryRepository, jobRegistry, policy);
    }

    private void wireDialogs() {
        this.dialogService = new DialogService(asyncExecutor);
        this.jobConditionsFormatter = new JobConditionsFormatter(i18n, amountFormatter, tagResolver);
        this.jobConditionsDialog = new JobConditionsDialog(
                i18n, jobRegistry, specialtyService, dialogService,
                jobConditionsFormatter, config.specialtyMode());
        this.specialtyListDialog = new SpecialtyListDialog(
                i18n, jobRegistry, specialtyService, dialogService,
                jobConditionsDialog, config.specialtyMode());
        this.specialtyCooldownDialog = new SpecialtyCooldownDialog(i18n, specialtyService, dialogService);
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
        PluginConfig.AsyncConfig async = config.reward().async();
        // 段階 12 (revokeCriteria) は prologue の末尾に置く。ワーカー経由にすると
        // advancement の再発火が遅れ、その間のイベントを取りこぼす
        // (docs/plan/async-reward-pipeline.md 「段階 12 の前倒し」)。
        List<Stage> stages = List.of(
                new MatcherStage(),
                new SpecialtyStage(specialtyService),
                new AntiAutomationStage(antiAutomationCoordinator, antiAutomationNotifier),
                new AdvancementRevokeStage(plugin),
                new BaseRewardStage(rng),
                new RareRollStage(rng, asyncExecutor),
                new BuiltinModifierStage(varietyPenaltyEvaluator, dailyCapEvaluator, ZoneId.systemDefault()),
                new ExtensionModifierStage(extensionModifierChain),
                new SplitterStage(splitterChain),
                new RewardRoundingStage(plugin, config.reward()),
                new EconomyTransferStage(plugin, economy, mainWorkQueue, async.economyOnMain()),
                new ActionLogStage(plugin, actionLogQueue, batchFlushWorker, asyncExecutor)
        );
        this.rewardPipeline = new RewardPipeline(plugin, jobRegistry, rewardDispatcher, stages);
        this.eventDispatcher = new EventDispatcher(specialtyService, jobRegistry, rewardMatcher, rewardPipeline);

        // economy_on_main のときだけ main thread 側のドレイナが要る。
        if (async.enabled() && async.economyOnMain()) {
            this.mainWorkDrainTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, mainWorkQueue::drainTick, 1L, 1L);
        }
    }

    private void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();

        pm.registerEvents(
                new PlayerJoinListener(
                        specialtyService,
                        specialtyListDialog,
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
                new AdvancementListener(eventDispatcher),
                operatorTracker)) {
            pm.registerEvents(listener, plugin);
        }
    }

    /** onEnable の後半で呼び、同梱の advancement datapack を配置する。 */
    public void installAdvancementDatapack() {
        new AdvancementDatapackInstaller(plugin).install();
    }

    /**
     * /jobs reload の実装。config を除いて再読込する。
     * <ul>
     *   <li>lang/*.yml を再読込</li>
     *   <li>jobs/*.yml を再読込 (registry を差し替え)</li>
     *   <li>tag cache を破棄</li>
     *   <li>variety curve キャッシュを破棄</li>
     *   <li>advancement datapack を再展開</li>
     * </ul>
     * config.yml 自体は再読込しない (persistence / kvs 種別の再構築が絡み、複雑さの割にニーズが薄いため)。
     * config を変える場合は再起動を要求する。
     */
    public void reload() {
        localeRegistry.load();
        new MissingKeyReporter(plugin, localeRegistry).report();
        loadJobs();
        runShadowDetection();
        if (varietyPenaltyEvaluator != null) varietyPenaltyEvaluator.invalidateCurves();
        installAdvancementDatapack();
    }

    /** サンプルとして同梱している職業定義。plugins/Jobs/jobs/ が空のときのみ展開する。 */
    private static final List<String> DEFAULT_JOB_RESOURCES = List.of(
            "jobs/combat.yml",
            "jobs/mining.yml",
            "jobs/farming.yml",
            "jobs/smelting.yml",
            "jobs/fishing.yml");

    public void loadJobs() {
        File jobsDir = new File(plugin.getDataFolder(), "jobs");
        if (!jobsDir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            jobsDir.mkdirs();
        }
        ensureDefaultJobsInstalled(jobsDir);

        JobYamlLoader loader = new JobYamlLoader(
                actionKeyDeriver,
                config.antiAutomation().defaults());
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
        // 報酬ワーカーを先に drain する。行動ログの enqueue は段階 11 で行われるので、
        // BatchFlushWorker より後に止めるとログが落ちる。
        if (mainWorkDrainTask != null) {
            mainWorkDrainTask.cancel();
            mainWorkDrainTask = null;
        }
        if (rewardWorker != null) {
            rewardWorker.drainAndStop(config.reward().async().drainTimeoutMs());
            rewardWorker = null;
        }
        // onDisable は main thread なので、ドレイナが止まっていてもここで直接空にすれば
        // 送金は正しいスレッドで完了する (docs/plan/async-reward-pipeline.md 「停止時の順序」)。
        if (mainWorkQueue != null) {
            int drained = mainWorkQueue.drainAllInline();
            if (drained > 0) {
                plugin.getLogger().info("drained " + drained + " pending main-thread reward task(s)");
            }
            mainWorkQueue = null;
        }
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

    public PlayerJobHistoryRepository playerJobHistoryRepository() {
        return playerJobHistoryRepository;
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

    public SpecialtyListDialog specialtyListDialog() {
        return specialtyListDialog;
    }

    public SpecialtyCooldownDialog specialtyCooldownDialog() {
        return specialtyCooldownDialog;
    }

    public JobConditionsDialog jobConditionsDialog() {
        return jobConditionsDialog;
    }

    public JobConditionsFormatter jobConditionsFormatter() {
        return jobConditionsFormatter;
    }

    public VaultEconomyAdapter economy() {
        return economy;
    }

    public AmountFormatter amountFormatter() {
        return amountFormatter;
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

    /** {@code /jobs admin queue} 用。async 無効時は null。 */
    public RewardWorkQueue rewardWorkQueue() {
        return rewardWorkQueue;
    }

    /** {@code /jobs admin queue} 用。async 無効時は null。 */
    public RewardWorker rewardWorker() {
        return rewardWorker;
    }

    /** {@code /jobs admin queue} 用。 */
    public MainWorkQueue mainWorkQueue() {
        return mainWorkQueue;
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

    public ExtensionModifierChain extensionModifierChain() {
        return extensionModifierChain;
    }

    public SplitterChain splitterChain() {
        return splitterChain;
    }

    public JobsApi jobsApi() {
        return jobsApi;
    }
}
