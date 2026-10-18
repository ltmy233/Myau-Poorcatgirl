package myau.ui.impl.mainmenu;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.world.demo.DemoWorldServer;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class MyauMainMenu extends GuiScreen implements GuiYesNoCallback {
    private static final Logger logger = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private static int bgTextureId = -1;

    private float updateCounter;
    private GuiButton buttonResetDemo;
    private String openGLWarning1;
    private String openGLWarning2;
    private String openGLWarningLink;
    public static final String field_96138_a = "Please click HERE for more information.";
    private int field_92024_r, field_92023_s, field_92022_t, field_92021_u, field_92020_v, field_92019_w;
    private GuiButton altsButton;
    private GuiButton modButton;
    private boolean field_183502_L;
    private GuiScreen field_183503_M;
    private int glassX;
    private int glassY;
    private int glassW;
    private int glassH;

    public MyauMainMenu() {
        this.openGLWarning2 = field_96138_a;
        this.field_183502_L = false;
        this.updateCounter = RANDOM.nextFloat();
        this.openGLWarning1 = "";
    }

    private boolean func_183501_a() {
        return Minecraft.getMinecraft().gameSettings.getOptionOrdinalValue(
                net.minecraft.client.settings.GameSettings.Options.REALMS_NOTIFICATIONS) && this.field_183503_M != null;
    }

    public void updateScreen() {
        if (this.func_183501_a()) {
            this.field_183503_M.updateScreen();
        }
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    protected void keyTyped(char typedChar, int keyCode) throws IOException {
    }

    public void initGui() {
        int bw = 100;
        int bh = 16;
        int gap = 4;
        int rows = this.mc.isDemo() ? 4 : 6;
        int rx = this.width - 12 - bw;
        int colH = rows * bh + (rows - 1) * gap;
        int y = (this.height - colH) / 2;
        this.glassX = rx - 7;
        this.glassY = y - 7;
        this.glassW = bw + 14;
        this.glassH = colH + 14;

        if (this.mc.isDemo()) {
            this.buttonList.add(new GuiButton(11, rx, y, bw, bh, I18n.format("menu.playdemo")));
            y += bh + gap;
            this.buttonList.add(this.buttonResetDemo = new GuiButton(12, rx, y, bw, bh, I18n.format("menu.resetdemo")));
            ISaveFormat isaveformat = this.mc.getSaveLoader();
            WorldInfo worldinfo = isaveformat.getWorldInfo("Demo_World");
            if (worldinfo == null) {
                this.buttonResetDemo.enabled = false;
            }
            y += bh + gap;
        } else {
            this.buttonList.add(new GuiButton(1, rx, y, bw, bh, I18n.format("menu.singleplayer")));
            y += bh + gap;
            this.buttonList.add(new GuiButton(2, rx, y, bw, bh, I18n.format("menu.multiplayer")));
            y += bh + gap;
            this.buttonList.add(this.modButton = new GuiButton(6, rx, y, bw, bh, I18n.format("fml.menu.mods")));
            y += bh + gap;
            this.buttonList.add(this.altsButton = new GuiButton(14, rx, y, bw, bh, "Alt Manager"));
            y += bh + gap;
        }
        this.buttonList.add(new GuiButton(0, rx, y, bw, bh, I18n.format("menu.options")));
        y += bh + gap;
        this.buttonList.add(new GuiButton(4, rx, y, bw, bh, I18n.format("menu.quit")));

        synchronized (this) {
            this.field_92023_s = this.fontRendererObj.getStringWidth(this.openGLWarning1);
            this.field_92024_r = this.fontRendererObj.getStringWidth(this.openGLWarning2);
            int k = Math.max(this.field_92023_s, this.field_92024_r);
            this.field_92022_t = (this.width - k) / 2;
            this.field_92021_u = ((GuiButton) this.buttonList.get(0)).yPosition - 24;
            this.field_92020_v = this.field_92022_t + k;
            this.field_92019_w = this.field_92021_u + 24;
        }

        this.mc.setConnectedToRealms(false);

        if (Minecraft.getMinecraft().gameSettings.getOptionOrdinalValue(
                net.minecraft.client.settings.GameSettings.Options.REALMS_NOTIFICATIONS) && !this.field_183502_L) {
            RealmsBridge realmsbridge = new RealmsBridge();
            this.field_183503_M = realmsbridge.getNotificationScreen(this);
            this.field_183502_L = true;
        }

        if (this.func_183501_a()) {
            this.field_183503_M.setGuiSize(this.width, this.height);
            this.field_183503_M.initGui();
        }
    }

    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
        if (button.id == 5) this.mc.displayGuiScreen(new GuiLanguage(this, this.mc.gameSettings, this.mc.getLanguageManager()));
        if (button.id == 1) this.mc.displayGuiScreen(new GuiSelectWorld(this));
        if (button.id == 2) this.mc.displayGuiScreen(new GuiMultiplayer(this));
        if (button.id == 4) this.mc.shutdown();
        if (button.id == 14) this.mc.displayGuiScreen(new me.ksyz.accountmanager.gui.GuiAccountManager(this));
        if (button.id == 11) this.mc.launchIntegratedServer("Demo_World", "Demo_World", DemoWorldServer.demoWorldSettings);
        if (button.id == 12) {
            ISaveFormat isaveformat = this.mc.getSaveLoader();
            WorldInfo worldinfo = isaveformat.getWorldInfo("Demo_World");
            if (worldinfo != null) {
                GuiYesNo guiyesno = GuiSelectWorld.makeDeleteWorldYesNo(this, worldinfo.getWorldName(), 12);
                this.mc.displayGuiScreen(guiyesno);
            }
        }
    }

    public void confirmClicked(boolean result, int id) {
        if (result && id == 12) {
            ISaveFormat isaveformat = this.mc.getSaveLoader();
            isaveformat.flushCache();
            isaveformat.deleteWorldDirectory("Demo_World");
            this.mc.displayGuiScreen(this);
        } else if (id == 13) {
            if (result) {
                try {
                    Class<?> oclass = Class.forName("java.awt.Desktop");
                    Object object = oclass.getMethod("getDesktop").invoke(null);
                    oclass.getMethod("browse", URI.class).invoke(object, new URI(this.openGLWarningLink));
                } catch (Throwable throwable) {
                    logger.error("Couldn't open link", throwable);
                }
            }
            this.mc.displayGuiScreen(this);
        }
    }

    // 背景图：视频壁纸没就绪就用静态图
    private void drawBackground() {
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        if (myau.wallpaper.WallpaperPlayer.get().draw(sr)) return;

        if (bgTextureId < 0) {
            try {
                InputStream stream = getClass().getClassLoader()
                        .getResourceAsStream("assets/myau/textures/background.png");
                if (stream == null) {
                    logger.error("Background image not found in assets");
                    bgTextureId = -2;
                    return;
                }
                BufferedImage img = ImageIO.read(stream);
                stream.close();
                if (img == null) { bgTextureId = -2; return; }

                int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
                ByteBuffer buf = BufferUtils.createByteBuffer(pixels.length * 4);
                for (int pixel : pixels) {
                    buf.put((byte) ((pixel >> 16) & 0xFF));
                    buf.put((byte) ((pixel >> 8) & 0xFF));
                    buf.put((byte) (pixel & 0xFF));
                    buf.put((byte) ((pixel >> 24) & 0xFF));
                }
                buf.flip();

                bgTextureId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, bgTextureId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, img.getWidth(), img.getHeight(),
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                logger.info("Background texture loaded: " + img.getWidth() + "x" + img.getHeight());
            } catch (Exception e) {
                logger.error("Failed to load background", e);
                bgTextureId = -2;
                return;
            }
        }

        if (bgTextureId < 0) return;

        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bgTextureId);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0.0D, h, 0.0D).tex(0.0D, 1.0D).endVertex();
        wr.pos(w, h, 0.0D).tex(1.0D, 1.0D).endVertex();
        wr.pos(w, 0.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        wr.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        tess.draw();
        GlStateManager.enableAlpha();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        myau.wallpaper.MenuBackground.draw(new ScaledResolution(mc));
        myau.module.modules.HUD hud = myau.module.modules.HUD.get();
        boolean useGlass = hud == null || hud.glassOn(hud.glassGui);
        if (!useGlass || !myau.util.FrostedGlass.drawFrosted(this.glassX, this.glassY, this.glassW, this.glassH, 12.0F,
                this.width, this.height, 1.0F)) {
            myau.util.RenderUtil.drawRoundedRect(this.glassX, this.glassY, this.glassW, this.glassH, 12.0F, 0x59000000);
        }

        // 标题：优先渲染器，失败退回手画
        if (!TitleRenderer.render(this.width, this.height)) {
            String title = "Poorcatgirl";
            GlStateManager.pushMatrix();
            float scale = 3.5F;
            GlStateManager.scale(scale, scale, scale);
            int tw = this.fontRendererObj.getStringWidth(title);
            this.fontRendererObj.drawStringWithShadow(title,
                    this.width / (2F * scale) - tw / 2F,
                    this.height / 8F / scale, 0xFFFFFF);
            GlStateManager.popMatrix();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.openGLWarning1 != null && this.openGLWarning1.length() > 0) {
            drawRect(this.field_92022_t - 2, this.field_92021_u - 2, this.field_92020_v + 2, this.field_92019_w - 1, 1428160512);
            this.drawString(this.fontRendererObj, this.openGLWarning1, this.field_92022_t, this.field_92021_u, -1);
            this.drawString(this.fontRendererObj, this.openGLWarning2, (this.width - this.field_92024_r) / 2,
                    ((GuiButton) this.buttonList.get(0)).yPosition - 12, -1);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (this.func_183501_a()) {
            this.field_183503_M.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (this.openGLWarning1.length() > 0 && mouseX >= this.field_92022_t && mouseX <= this.field_92020_v
                && mouseY >= this.field_92021_u && mouseY <= this.field_92019_w) {
            GuiConfirmOpenLink guiconfirmopenlink = new GuiConfirmOpenLink(this, this.openGLWarningLink, 13, true);
            guiconfirmopenlink.disableSecurityWarning();
            this.mc.displayGuiScreen(guiconfirmopenlink);
        }

        if (this.func_183501_a()) {
            ((myau.mixin.IAccessorGuiScreen) this.field_183503_M).callMouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    public void onGuiClosed() {
        myau.wallpaper.WallpaperPlayer.get().pause();
        if (this.field_183503_M != null) {
            this.field_183503_M.onGuiClosed();
        }
    }
}
