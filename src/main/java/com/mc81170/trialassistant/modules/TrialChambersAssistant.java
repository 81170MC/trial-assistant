package com.mc81170.trialassistant.modules;

import com.mc81170.trialassistant.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TrialChambersAssistant extends Module {

    public enum Language {
        English,
        Spanish,
        Portuguese,
        Russian
    }

    // ---------- Settings ----------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgHud = settings.createGroup("HUD");
    private final SettingGroup sgLabels = settings.createGroup("Spawner Labels");
    private final SettingGroup sgSpawner = settings.createGroup("Spawners");

    private final Setting<Language> language = sgGeneral.add(new EnumSetting.Builder<Language>()
        .name("language").description("Language of the on-screen text.")
        .defaultValue(Language.English).build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius").description("Radius (in blocks) in which vaults and spawners are searched.")
        .defaultValue(64).min(16).sliderMax(128).build());

    private final Setting<Integer> triggerDistance = sgGeneral.add(new IntSetting.Builder()
        .name("trigger-distance").description("Distance to a vault/spawner at which you are considered to be inside a trial chamber.")
        .defaultValue(20).min(8).sliderMax(48).build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval").description("Ticks between block entity scans.")
        .defaultValue(20).min(5).sliderMax(100).build());

    private final Setting<Boolean> markNearest = sgGeneral.add(new BoolSetting.Builder()
        .name("mark-nearest-used").description("Toggle on to mark the nearest vault (within 8 blocks) as used.")
        .defaultValue(false)
        .onChanged(v -> {
            if (v) {
                markNearestVault();
                this.markNearest.set(false);
            }
        })
        .build());

    private final Setting<Boolean> showUsed = sgRender.add(new BoolSetting.Builder()
        .name("show-used").description("Keep showing vaults that have already been used.")
        .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> normalColor = sgRender.add(new ColorSetting.Builder()
        .name("normal-color").defaultValue(new SettingColor(255, 215, 0, 200)).build());

    private final Setting<SettingColor> ominousColor = sgRender.add(new ColorSetting.Builder()
        .name("ominous-color").defaultValue(new SettingColor(170, 0, 170, 200)).build());

    private final Setting<SettingColor> usedColor = sgRender.add(new ColorSetting.Builder()
        .name("used-color").defaultValue(new SettingColor(0, 0, 0, 220)).build());

    private final Setting<Boolean> showHud = sgHud.add(new BoolSetting.Builder()
        .name("show-hud").description("Show the unused vault counter on screen.")
        .defaultValue(true).build());

    private final Setting<Integer> hudX = sgHud.add(new IntSetting.Builder()
        .name("x").defaultValue(10).min(0).sliderMax(1000).build());

    private final Setting<Integer> hudY = sgHud.add(new IntSetting.Builder()
        .name("y").defaultValue(40).min(0).sliderMax(600).build());

    private final Setting<Double> hudScale = sgHud.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(0.8).min(0.5).sliderMax(2.0).build());

    private final Setting<Boolean> showLabels = sgLabels.add(new BoolSetting.Builder()
        .name("show-labels").description("Draw the mob counter floating above each spawner.")
        .defaultValue(true).build());

    private final Setting<Boolean> showCooldown = sgLabels.add(new BoolSetting.Builder()
        .name("show-cooldown").description("Also draw the cooldown above spawners that were already used.")
        .defaultValue(true).build());

    private final Setting<Integer> labelRange = sgLabels.add(new IntSetting.Builder()
        .name("label-range").description("Only draw labels for spawners within this distance.")
        .defaultValue(48).min(8).sliderMax(128).build());

    private final Setting<Double> labelScale = sgLabels.add(new DoubleSetting.Builder()
        .name("label-scale").defaultValue(1.0).min(0.5).sliderMax(3.0).build());

    private final Setting<Double> labelHeight = sgLabels.add(new DoubleSetting.Builder()
        .name("label-height").description("Height of the label above the spawner block.")
        .defaultValue(1.6).min(0.5).sliderMax(4.0).build());

    private final Setting<Integer> totalMobs = sgSpawner.add(new IntSetting.Builder()
        .name("total-mobs").description("Total mobs per round (1 player).")
        .defaultValue(6).min(1).sliderMax(30).build());

    private final Setting<Integer> simultaneousMobs = sgSpawner.add(new IntSetting.Builder()
        .name("simultaneous-mobs").description("Mobs alive at the same time (1 player).")
        .defaultValue(2).min(1).sliderMax(10).build());

    private final Setting<Integer> ticksBetweenSpawns = sgSpawner.add(new IntSetting.Builder()
        .name("ticks-between-spawns").defaultValue(40).min(1).sliderMax(200).build());

    private final Setting<Integer> cooldownMinutes = sgSpawner.add(new IntSetting.Builder()
        .name("cooldown-minutes").defaultValue(30).min(1).sliderMax(60).build());

    private final Setting<Double> mobRange = sgSpawner.add(new DoubleSetting.Builder()
        .name("mob-range").description("Radius around the spawner in which mobs are counted.")
        .defaultValue(8.0).min(4.0).sliderMax(16.0).build());

    // ---------- State ----------
    private static final Color GOLD = new Color(255, 215, 0);
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color GRAY = new Color(170, 170, 170);
    private static final Color PURPLE = new Color(210, 110, 255);
    private static final Color WHITE = new Color(255, 255, 255);

    private static class VaultInfo {
        boolean ominous;
        boolean used;
    }

    private static class SpawnerInfo {
        boolean ominous;
        String state = "";
        final Set<Integer> everSeen = new HashSet<>();
        final Set<Integer> alive = new HashSet<>();
        int killed;
        long lastSpawnTick = -1;
        long cooldownEndTick = -1;
    }

    private final Map<BlockPos, VaultInfo> vaults = new HashMap<>();
    private final Map<BlockPos, SpawnerInfo> spawners = new HashMap<>();
    private final Vector3d labelPos = new Vector3d();
    private final Set<String> persistedUsed = new LinkedHashSet<>();
    private boolean inTrial = false;
    private int ticks = 0;
    private Level lastLevel;

    public TrialChambersAssistant() {
        super(AddonTemplate.CATEGORY, "trial-chambers-assistant", "Trial Chambers Assistant: unused vaults and spawner status.");
    }

    private String tr(String en, String es, String pt, String ru) {
        return switch (language.get()) {
            case Spanish -> es;
            case Portuguese -> pt;
            case Russian -> ru;
            default -> en;
        };
    }

    @Override
    public void onActivate() {
        loadUsed();
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    //  ======= Persistence of used vaults
    private Path usedFile() {
        return FabricLoader.getInstance().getGameDir().resolve("meteor-client").resolve("trial-assistant-used-vaults.txt");
    }

    private void loadUsed() {
        persistedUsed.clear();
        try {
            Path f = usedFile();
            if (Files.exists(f)) persistedUsed.addAll(Files.readAllLines(f, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private void saveUsed() {
        try {
            Path f = usedFile();
            Files.createDirectories(f.getParent());
            Files.write(f, persistedUsed, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private String worldKey() {
        if (mc.level == null) return "unknown|unknown";
        String world;
        if (mc.getCurrentServer() != null) world = mc.getCurrentServer().ip;
        else if (mc.getSingleplayerServer() != null) world = mc.getSingleplayerServer().getWorldData().getLevelName();
        else world = "unknown";
        return world + "|" + mc.level.dimension();
    }

    private String vaultKey(BlockPos pos) {
        return worldKey() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void markUsed(BlockPos pos, VaultInfo v) {
        v.used = true;
        persistedUsed.add(vaultKey(pos));
        saveUsed();
    }

    private void markNearestVault() {
        if (mc.player == null || mc.level == null || vaults.isEmpty()) return;
        BlockPos pp = mc.player.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : vaults.keySet()) {
            double d = p.distSqr(pp);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        if (best != null && bestSq <= 64) {
            VaultInfo info = vaults.get(best);
            if (info != null) {
                markUsed(best, info);
                info(tr("Vault marked as used.", "Vault marcado como usado.", "Cofre marcado como usado.",
                    "Хранилище отмечено как использованное."));
            }
        }
    }

    private boolean isClosestPlayer(BlockPos pos) {
        if (mc.player == null || mc.level == null) return true;
        double mine = mc.player.blockPosition().distSqr(pos);
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.blockPosition().distSqr(pos) < mine) return false;
        }
        return true;
    }

    private void reset() {
        vaults.clear();
        spawners.clear();
        inTrial = false;
        ticks = 0;
    }

    // ================ Tick
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            reset();
            lastLevel = null;
            return;
        }

        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }

        if (++ticks % scanInterval.get() == 0) scan();
        if (!inTrial) return;

        updateVaults();
        updateSpawners();
    }

    private void scan() {
        if (mc.player == null || mc.level == null) return;
        BlockPos pp = mc.player.blockPosition();
        int range = scanRadius.get();
        double rangeSq = (double) range * range;
        int chunkRadius = (range >> 4) + 1;
        int pcx = pp.getX() >> 4;
        int pcz = pp.getZ() >> 4;

        List<BlockEntity> found = new ArrayList<>();
        double nearestSq = Double.MAX_VALUE;

        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof VaultBlockEntity) && !(be instanceof TrialSpawnerBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(pp);
                    if (d > rangeSq) continue;
                    found.add(be);
                    nearestSq = Math.min(nearestSq, d);
                }
            }
        }

        if (!inTrial) {
            double trigger = triggerDistance.get();
            if (nearestSq <= trigger * trigger) inTrial = true;
            else return;
        }

        for (BlockEntity be : found) {
            BlockPos pos = be.getBlockPos();
            if (be instanceof VaultBlockEntity) {
                vaults.computeIfAbsent(pos, p -> {
                    VaultInfo vi = new VaultInfo();
                    vi.used = persistedUsed.contains(vaultKey(p));
                    return vi;
                });
            }
            else spawners.computeIfAbsent(pos, p -> new SpawnerInfo());
        }

        double leaveSq = Math.pow(range + 32, 2);
        boolean anyNear = false;
        for (BlockPos p : vaults.keySet()) if (p.distSqr(pp) <= leaveSq) { anyNear = true; break; }
        if (!anyNear) for (BlockPos p : spawners.keySet()) if (p.distSqr(pp) <= leaveSq) { anyNear = true; break; }
        if (!anyNear) reset();
    }

    private void updateVaults() {
        if (mc.level == null || mc.player == null) return;
        Iterator<Map.Entry<BlockPos, VaultInfo>> vit = vaults.entrySet().iterator();

        while (vit.hasNext()) {
            Map.Entry<BlockPos, VaultInfo> entry = vit.next();
            BlockPos pos = entry.getKey();
            VaultInfo v = entry.getValue();
            BlockState s = mc.level.getBlockState(pos);
            if (!s.hasProperty(BlockStateProperties.VAULT_STATE)) {
                if (mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    vit.remove();
                    if (persistedUsed.remove(vaultKey(pos))) saveUsed();
                }
                continue;
            }

            v.ominous = s.hasProperty(BlockStateProperties.OMINOUS) && s.getValue(BlockStateProperties.OMINOUS);

            String st = s.getValue(BlockStateProperties.VAULT_STATE).name();
            boolean opening = st.equals("UNLOCKING") || st.equals("EJECTING");

            if (opening && !v.used
                && mc.player.blockPosition().distSqr(pos) <= 36
                && isClosestPlayer(pos)) {
                markUsed(pos, v);
            }
        }
    }

    private void updateSpawners() {
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<BlockPos, SpawnerInfo>> sit = spawners.entrySet().iterator();
        while (sit.hasNext()) {
            Map.Entry<BlockPos, SpawnerInfo> entry = sit.next();
            BlockPos pos = entry.getKey();
            SpawnerInfo info = entry.getValue();
            BlockState s = mc.level.getBlockState(pos);
            if (!s.hasProperty(BlockStateProperties.TRIAL_SPAWNER_STATE)) {
                if (mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) sit.remove();
                continue;
            }

            info.ominous = s.hasProperty(BlockStateProperties.OMINOUS) && s.getValue(BlockStateProperties.OMINOUS);
            String st = s.getValue(BlockStateProperties.TRIAL_SPAWNER_STATE).name();

            if (!st.equals(info.state)) {
                if (st.equals("ACTIVE")) {
                    info.everSeen.clear();
                    info.alive.clear();
                    info.killed = 0;
                    info.lastSpawnTick = now;
                }
                if (st.equals("COOLDOWN") && !info.state.isEmpty()) {
                    info.cooldownEndTick = now + cooldownMinutes.get() * 60L * 20L;
                }
                info.state = st;
            }

            if (st.equals("ACTIVE")) trackMobs(pos, info, now);
        }
    }

    private void trackMobs(BlockPos pos, SpawnerInfo info, long now) {
        if (mc.level == null) return;
        Iterator<Integer> it = info.alive.iterator();
        while (it.hasNext()) {
            Entity ent = mc.level.getEntity(it.next());
            if (ent == null || !ent.isAlive()) {
                info.killed++;
                it.remove();
            }
        }

        AABB box = new AABB(pos).inflate(mobRange.get());
        for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, box)) {
            if (!(mob instanceof Enemy) || !mob.isAlive()) continue;
            if (info.everSeen.add(mob.getId())) {
                info.alive.add(mob.getId());
                info.lastSpawnTick = now;
            }
        }
    }

    private int playersNear(BlockPos pos, double radius) {
        if (mc.level == null) return 0;
        int n = 0;
        double r2 = radius * radius;
        for (Player p : mc.level.players()) {
            if (p.blockPosition().distSqr(pos) <= r2) n++;
        }
        return n;
    }

    // ---------- 3D render ----------
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!inTrial || mc.level == null) return;

        for (Map.Entry<BlockPos, VaultInfo> e : vaults.entrySet()) {
            VaultInfo v = e.getValue();
            if (v.used && !showUsed.get()) continue;

            Color line = v.used ? usedColor.get() : (v.ominous ? ominousColor.get() : normalColor.get());
            Color side = new Color(line.r, line.g, line.b, line.a / 4);
            event.renderer.box(e.getKey(), side, line, shapeMode.get(), 0);
        }
    }

    // ---------- 2D render ----------
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (!inTrial) return;

        if (showHud.get()) renderVaultCounter();
        if (showLabels.get()) renderSpawnerLabels();
    }

    private void renderVaultCounter() {
        int unused = 0, unusedOminous = 0;
        for (VaultInfo v : vaults.values()) {
            if (v.used) continue;
            unused++;
            if (v.ominous) unusedOminous++;
        }

        String header;
        if (unused == 0) {
            header = tr("Vaults: all used", "Vaults: todas usadas", "Cofres: todos usados",
                "Хранилища: все использованы");
        } else {
            header = tr("Unused vaults: ", "Vaults sin usar: ", "Cofres não usados: ",
                "Неиспользованные хранилища: ") + unused
                + (unusedOminous > 0 ? " (" + unusedOminous + tr(" ominous)", " ominosas)", " sinistros)",
                " зловещих)") : "");
        }

        TextRenderer text = TextRenderer.get();
        text.begin(hudScale.get());
        text.render(header, hudX.get(), hudY.get(), unused == 0 ? GREEN : GOLD, true);
        text.end();
    }

    private void renderSpawnerLabels() {
        if (mc.level == null || mc.player == null) return;
        long now = mc.level.getGameTime();
        BlockPos pp = mc.player.blockPosition();
        double rangeSq = (double) labelRange.get() * labelRange.get();
        TextRenderer text = TextRenderer.get();

        for (Map.Entry<BlockPos, SpawnerInfo> e : spawners.entrySet()) {
            BlockPos pos = e.getKey();
            SpawnerInfo info = e.getValue();
            if (pos.distSqr(pp) > rangeSq) continue;

            List<String> lines = labelLines(pos, info, now);
            if (lines.isEmpty()) continue;

            labelPos.set(pos.getX() + 0.5, pos.getY() + labelHeight.get(), pos.getZ() + 0.5);
            if (!NametagUtils.to2D(labelPos, labelScale.get())) continue;

            Color color = info.state.equals("COOLDOWN") ? GRAY : (info.ominous ? PURPLE : WHITE);

            NametagUtils.begin(labelPos);
            text.beginBig();

            double lineHeight = text.getHeight(true) + 2;
            double y = -lineHeight * lines.size();
            for (String line : lines) {
                double w = text.getWidth(line, true);
                text.render(line, -w / 2, y, color, true);
                y += lineHeight;
            }

            text.end();
            NametagUtils.end();
        }
    }

    private List<String> labelLines(BlockPos pos, SpawnerInfo info, long now) {
        List<String> lines = new ArrayList<>();

        // Título base para ubicar el spawner a través de las paredes desde otras habitaciones
        String spawnerTitle = tr("Spawner", "Spawner", "Spawner", "Спавнер");

        switch (info.state) {
            case "ACTIVE" -> {
                int extra = Math.max(0, playersNear(pos, 14) - 1);
                int total = totalMobs.get() + 2 * extra;

                int cap = simultaneousMobs.get() + extra;
                int alive = info.alive.size();
                int remaining = Math.max(0, total - info.killed);

                if (remaining == 0) {
                    lines.add(spawnerTitle);
                    lines.add(tr("Round complete", "Ronda completa", "Rodada completa",
                        "Раунд завершён"));
                    break;
                }

                // Muestra el título del spawner y abajo los contadores de enemigos
                lines.add(spawnerTitle);
                lines.add(tr("Left: ", "Faltan: ", "Faltam: ", "Осталось: ") + remaining
                    + "  (" + tr("alive ", "vivos ", "vivos ", "живых ") + alive + ")");

                if (info.everSeen.size() < total) {
                    if (alive >= cap) {
                        lines.add(tr("Next: when one dies", "Próximo: al matar uno", "Próximo: ao matar um",
                            "Следующий: после убийства"));
                    } else {
                        long left = Math.max(0, info.lastSpawnTick + ticksBetweenSpawns.get() - now);
                        lines.add(String.format(tr("Next in %.1fs", "Próximo en %.1fs", "Próximo em %.1fs",
                            "Следующий через %.1f с"), left / 20.0));
                    }
                }
            }
            case "COOLDOWN" -> {
                lines.add(spawnerTitle);
                if (!showCooldown.get()) break;
                String cd = tr("Cooldown", "Cooldown", "Cooldown", "Перезарядка");
                if (info.cooldownEndTick < 0) {
                    lines.add(cd);
                } else {
                    long left = Math.max(0, info.cooldownEndTick - now) / 20;
                    lines.add(cd + String.format(" %d:%02d", left / 60, left % 60));
                }
            }
            default -> {
                // Muestra la etiqueta base incluso si el estado está cargando o inactivo
                lines.add(spawnerTitle);
            }
        }

        return lines;
    }



}
