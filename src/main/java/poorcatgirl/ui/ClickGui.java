
package poorcatgirl.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import poorcatgirl.Poorcatgirl;
import poorcatgirl.module.Module;
import poorcatgirl.module.modules.*;
import poorcatgirl.ui.components.BindComponent;
import poorcatgirl.ui.components.CategoryComponent;
import poorcatgirl.ui.components.KeyPropertyComponent;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ClickGui extends GuiScreen {
    private static ClickGui instance;
    private static final long OPEN_ANIMATION_MS = 330L;
    private static final long OPEN_STAGGER_MS = 14L;
    private static final long CLOSE_ANIMATION_MS = 190L;
    private static final long CLOSE_STAGGER_MS = 8L;
    private final File configFile = new File("./config/Poorcatgirl/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;
    private long openAnimationStart = System.nanoTime();
    private boolean closing = false;
    private long closeAnimationStart = 0L;
    private boolean closeScreenRequested = false;
    private boolean guiKeyAwaitingRelease = false;

    public ClickGui() {
        instance = this;

        List<Module> combatModules = new ArrayList<>();
        combatModules.add(Poorcatgirl.moduleManager.getModule(AimAssist.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(AutoClicker.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(KillAura.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(Wtap.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(Velocity.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(Freeze.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(Reach.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(NoHitDelay.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(AntiFireball.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(LagRange.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(HitBox.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(MoreKB.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(Refill.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(HitSelect.class));
        combatModules.add(Poorcatgirl.moduleManager.getModule(HitFlick.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(Poorcatgirl.moduleManager.getModule(AntiAFK.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Fly.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(AirStall.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Speed.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(LongJump.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Sprint.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(SafeWalk.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Jesus.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Blink.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(NoFall.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(NoSlow.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(KeepSprint.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(Eagle.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(AntiVoid.class));
        movementModules.add(Poorcatgirl.moduleManager.getModule(GodBridge.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(Poorcatgirl.moduleManager.getModule(ESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Chams.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(FullBright.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Tracers.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(NameTags.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Xray.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(BetterChat.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(TargetHUD.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Indicators.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(BedESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(ItemESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(ViewClip.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(HUD.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Fishcake.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(GuiModule.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(ChestESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Trajectories.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Radar.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(HitParticleEffects.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(TeamHealthDisplay.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Arrows.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(BlockESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(BlockOverlay.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(BreakProgress.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(DamageTags.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(DamageTint.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(FallView.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Freelook.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Holdlook.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(ItemPhysics.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(MobESP.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(MotionBlur.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(PotionHUD.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(Saturation.class));
        renderModules.add(Poorcatgirl.moduleManager.getModule(TNTTimer.class));

        List<Module> playerModules = new ArrayList<>();
        playerModules.add(Poorcatgirl.moduleManager.getModule(AutoHeal.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(AutoTool.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(ChestStealer.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(InvManager.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(InvWalk.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(Scaffold.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(Telly.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(AutoBlockIn.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(SpeedMine.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(FastPlace.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(GhostHand.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(MCF.class));
        playerModules.add(Poorcatgirl.moduleManager.getModule(AntiDebuff.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(Poorcatgirl.moduleManager.getModule(Spammer.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(BedNuker.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(BedTracker.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(LightningTracker.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(NoRotate.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(NickHider.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(Poorcatgirl.moduleManager.getModule(InventoryClicker.class));

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        playerModules.sort(comparator);
        miscModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(combatModules);
        registered.addAll(movementModules);
        registered.addAll(renderModules);
        registered.addAll(playerModules);
        registered.addAll(miscModules);

        for (Module module : Poorcatgirl.moduleManager.modules.values()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        this.categoryList = new ArrayList<>();
        int topOffset = 5;

        CategoryComponent combat = new CategoryComponent("Combat", combatModules);
        combat.setY(topOffset);
        categoryList.add(combat);
        topOffset += 20;

        CategoryComponent movement = new CategoryComponent("Movement", movementModules);
        movement.setY(topOffset);
        categoryList.add(movement);
        topOffset += 20;

        CategoryComponent render = new CategoryComponent("Render", renderModules);
        render.setY(topOffset);
        categoryList.add(render);
        topOffset += 20;

        CategoryComponent player = new CategoryComponent("Player", playerModules);
        player.setY(topOffset);
        categoryList.add(player);
        topOffset += 20;

        CategoryComponent misc = new CategoryComponent("Misc", miscModules);
        misc.setY(topOffset);
        categoryList.add(misc);

        loadPositions();
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public void initGui() {
        super.initGui();
        this.openAnimationStart = System.nanoTime();
        this.closing = false;
        this.closeAnimationStart = 0L;
        this.closeScreenRequested = false;
        this.guiKeyAwaitingRelease = true;
        poorcatgirl.util.FrostedGlass.invalidateGuiBackdrop();
        Lang.load();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.guiKeyAwaitingRelease) {
            GuiModule gui = Poorcatgirl.moduleManager == null ? null
                    : (GuiModule) Poorcatgirl.moduleManager.getModule(GuiModule.class);
            if (gui == null || !Keyboard.isKeyDown(gui.getKey())) {
                this.guiKeyAwaitingRelease = false;
            }
        }
    }

    private boolean opening() {
        long total = OPEN_ANIMATION_MS + OPEN_STAGGER_MS * Math.max(0, this.categoryList.size() - 1);
        return !this.closing && System.nanoTime() - this.openAnimationStart < total * 1_000_000L;
    }

    private void beginClose() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        this.closeAnimationStart = System.nanoTime();
    }

    private boolean closeFinished() {
        long total = CLOSE_ANIMATION_MS + CLOSE_STAGGER_MS * Math.max(0, this.categoryList.size() - 1);
        return this.closing && System.nanoTime() - this.closeAnimationStart >= total * 1_000_000L;
    }

    private static float clamp01(float t) {
        return t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
    }

    private static float easeOutCubic(float t) {
        t = clamp01(t);
        float p = 1.0F - t;
        return 1.0F - p * p * p;
    }

    private static float easeOutQuint(float t) {
        t = clamp01(t);
        float p = 1.0F - t;
        return 1.0F - p * p * p * p * p;
    }

    private static float easeOutExpo(float t) {
        t = clamp01(t);
        return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    private static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    private long closeDurationMs() {
        return CLOSE_ANIMATION_MS + CLOSE_STAGGER_MS * Math.max(0, this.categoryList.size() - 1);
    }

    public void drawScreen(int x, int y, float p) {
        long now = System.nanoTime();
        double elapsed = (now - this.openAnimationStart) / 1_000_000.0;
        double closeElapsed = this.closing ? (now - this.closeAnimationStart) / 1_000_000.0 : 0.0;
        float overlayT = this.closing
                ? clamp01((float) (closeElapsed / (double) closeDurationMs()))
                : clamp01((float) (elapsed / 200.0));
        int overlayAlpha = this.closing
                ? (int) (100.0F * (1.0F - easeOutCubic(overlayT)))
                : (int) (100.0F * easeOutCubic(overlayT));
        poorcatgirl.util.FrostedGlass.setGuiBackdropDim(overlayAlpha / 255.0F);
        if (this.hasExpandedCategory()) {
            poorcatgirl.util.FrostedGlass.captureScreen();
        }
        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, overlayAlpha).getRGB());

        mc.fontRendererObj.drawStringWithShadow("Poorcatgirl " + Poorcatgirl.version, 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT * 2, new Color(60, 162, 253).getRGB());
        mc.fontRendererObj.drawStringWithShadow("作者: 蓝喵喵 (ltmy_233)", 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT, new Color(60, 162, 253).getRGB());

        boolean animating = opening() || this.closing;
        double animElapsed = this.closing ? closeElapsed : elapsed;
        double animMs = this.closing ? CLOSE_ANIMATION_MS : OPEN_ANIMATION_MS;
        double stagger = this.closing ? CLOSE_STAGGER_MS : OPEN_STAGGER_MS;
        for (int i = 0; i < categoryList.size(); i++) {
            CategoryComponent category = categoryList.get(i);
            double delay = i * stagger;
            float raw = (float) ((animElapsed - delay) / animMs);
            if (raw <= 0.0F && !this.closing) {
                continue;
            }
            int originalY = category.getY();
            int offset = this.closing
                    ? -Math.round((this.height + 160.0F) * easeInQuad(raw))
                    : Math.round(-56.0F * (1.0F - easeOutExpo(raw)));
            category.setY(originalY + offset);
            category.render(this.fontRendererObj);
            category.setY(originalY);
            if (!animating) {
                category.handleDrag(x, y);
            }

            for (Component module : category.getModules()) {
                module.update(x, y);
            }
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0 && !animating) {
            int notches = wheel / 120;
            if (notches == 0) {
                notches = wheel > 0 ? 1 : -1;
            }
            for (CategoryComponent category : categoryList) {
                category.onScroll(x, y, notches);
            }
        }

        if (this.closeFinished() && !this.closeScreenRequested) {
            this.closeScreenRequested = true;
            this.mc.displayGuiScreen(null);
        }
    }

    private boolean hasExpandedCategory() {
        for (CategoryComponent category : this.categoryList) {
            if (category.isOpened() && !category.getModules().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void mouseClicked(int x, int y, int mouseButton) {
        if (opening() || this.closing) {
            return;
        }
        Iterator<CategoryComponent> btnCat = categoryList.iterator();
        while (true) {
            CategoryComponent category;
            do {
                do {
                    if (!btnCat.hasNext()) {
                        return;
                    }

                    category = btnCat.next();
                    if (category.insideArea(x, y) && !category.isHovered(x, y) && !category.mousePressed(x, y) && mouseButton == 0) {
                        category.mousePressed(true);
                        category.xx = x - category.getX();
                        category.yy = y - category.getY();
                    }

                    if (category.mousePressed(x, y) && mouseButton == 0) {
                        category.setOpened(!category.isOpened());
                    }

                    if (category.isHovered(x, y) && mouseButton == 0) {
                        category.setPin(!category.isPin());
                    }
                } while (!category.isOpened());
            } while (category.getModules().isEmpty());

            for (Component c : category.getModules()) {
                c.mouseDown(x, y, mouseButton);
            }
        }

    }

    public void mouseReleased(int x, int y, int mouseButton) {
        if (opening() || this.closing) {
            return;
        }
        Iterator<CategoryComponent> iterator = categoryList.iterator();

        CategoryComponent categoryComponent;
        while (iterator.hasNext()) {
            categoryComponent = iterator.next();
            if (mouseButton == 0) {
                categoryComponent.mousePressed(false);
            }
        }

        iterator = categoryList.iterator();

        while (true) {
            do {
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }

                    categoryComponent = iterator.next();
                } while (!categoryComponent.isOpened());
            } while (categoryComponent.getModules().isEmpty());

            for (Component component : categoryComponent.getModules()) {
                component.mouseReleased(x, y, mouseButton);
            }
        }
    }

    public void keyTyped(char typedChar, int key) {
        if (this.closing) {
            return;
        }
        GuiModule gui = Poorcatgirl.moduleManager == null ? null
                : (GuiModule) Poorcatgirl.moduleManager.getModule(GuiModule.class);
        if (gui != null && key != 0 && key == gui.getKey()) {
            if (Keyboard.isRepeatEvent() || this.guiKeyAwaitingRelease
                    || System.nanoTime() - this.openAnimationStart < 150_000_000L) {
                return;
            }
            this.beginClose();
            return;
        }
        if (key != 1 && opening()) {
            return;
        }
        if (key == 1) {
            if (BindComponent.consumeEscape(typedChar, key)) {
                return;
            }
            if (KeyPropertyComponent.consumeEscape(typedChar, key)) {
                return;
            }
            this.beginClose();
        } else {
            Iterator<CategoryComponent> btnCat = categoryList.iterator();

            while (true) {
                CategoryComponent cat;
                do {
                    do {
                        if (!btnCat.hasNext()) {
                            return;
                        }

                        cat = btnCat.next();
                    } while (!cat.isOpened());
                } while (cat.getModules().isEmpty());

                for (Component component : cat.getModules()) {
                    component.keyTyped(typedChar, key);
                }
            }
        }
    }

    public void onGuiClosed() {
        savePositions();
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
