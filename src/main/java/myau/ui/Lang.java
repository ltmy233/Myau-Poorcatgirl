package myau.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Lang {
    public static final String EN = "EN";
    public static final String ZH = "ZH";

    public static String language = ZH;

    private static final File LANG_FILE = new File("./config/Myau/", "gui.lang");
    private static final Map<String, String> DICT = new HashMap<String, String>();

    static {
        put("Combat", "战斗");
        put("Movement", "移动");
        put("Render", "渲染");
        put("Player", "玩家");
        put("Misc", "杂项");

        put("language", "语言");
        put("Bind", "绑定");
        put("Press a key...", "按下任意键...");
        put("Settings", "设置");
        put("Search", "搜索");
        put("Search...", "搜索...");
        put("Enabled", "启用");
        put("Disabled", "禁用");

        put("AimAssist", "自瞄");
        put("AutoClicker", "连点");
        put("KillAura", "杀戮光环");
        put("aps", "每秒攻击");
        put("auto-block", "自动格挡");
        put("attack-range", "攻击距离");
        put("swing-range", "挥击距离");
        put("block-range", "格挡距离");
        put("rotations", "旋转模式");
        put("smoothing", "旋转平滑");
        put("sort", "目标排序");
        put("switch-delay", "切换延迟");
        put("targets", "目标数量");
        put("target-invis", "攻击隐身目标");
        put("attack-mobs", "攻击生物");
        put("disable-in-inventory", "打开背包时禁用");
        put("disable-while-blocking", "格挡时禁用");
        put("disable-while-mining", "挖掘时禁用");
        put("hit-through-blocks", "穿墙攻击");
        put("ignore-teammates", "忽略队友");
        put("manual-block", "手动格挡");
        put("prioritize-enemies", "优先敌人");
        put("require-mouse-down", "需要按住鼠标");
        put("silent-swing", "静默挥击");
        put("weapon-only", "仅武器");
        put("info", "信息显示");
        put("show-target", "显示目标");
        put("WTap", "WTap");
        put("Velocity", "防击退");
        put("Freeze", "冻结");
        put("Reach", "攻击距离");
        put("TargetStrafe", "目标环绕");
        put("NoHitDelay", "无攻击延迟");
        put("AntiFireball", "防火球");
        put("LagRange", "延迟范围");
        put("HitBox", "碰撞箱");
        put("MoreKB", "更多击退");
        put("Refill", "自动补充");
        put("HitSelect", "点击选择");
        put("HitFlick", "甩击");
        put("AntiAFK", "反挂机");
        put("Fly", "飞行");
        put("AirStall", "卡空");
        put("Speed", "速度");
        put("LongJump", "远跳");
        put("Sprint", "自动疾跑");
        put("SafeWalk", "安全行走");
        put("Jesus", "轻功水上漂");
        put("Blink", "闪现");
        put("NoFall", "无摔落");
        put("NoSlow", "无减速");
        put("KeepSprint", "保持疾跑");
        put("Eagle", "边缘潜行");
        put("NoJumpDelay", "无跳跃延迟");
        put("AntiVoid", "反虚空");

        put("ESP", "ESP");
        put("Chams", "透视");
        put("Fullbright", "全亮");
        put("Tracers", "追踪线");
        put("NameTags", "名牌");
        put("Xray", "矿透");
        put("TargetHUD", "目标信息");
        put("Indicators", "抛射物警告");
        put("BedESP", "床透视");
        put("ItemESP", "物品ESP");
        put("ViewClip", "视角穿透");
        put("NoHurtCam", "无受伤镜头");
        put("HUD", "HUD");
        put("Fishcake", "鱼饼");
        put("ClickGui", "点击界面");
        put("ChestESP", "箱子ESP");
        put("Trajectories", "轨迹预测");
        put("Radar", "雷达");
        put("HitParticleEffects", "命中粒子效果");
        put("TeamHealthDisplay", "队伍血量显示");
        put("弹幕", "弹幕");

        put("AutoHeal", "自动治疗");
        put("AutoTool", "自动工具");
        put("ChestStealer", "箱子偷取");
        put("InvManager", "背包管理");
        put("InvWalk", "背包行走");
        put("Telly", "Telly搭路");
        put("AutoBlockIn", "自动格挡");
        put("SpeedMine", "快速挖掘");
        put("FastPlace", "快速放置");
        put("GhostHand", "幽灵手");
        put("MCF", "中键好友");
        put("AntiDebuff", "防负面药水");
        put("Spammer", "刷屏器");
        put("BedNuker", "自动挖床");
        put("BedTracker", "床追踪");
        put("LightningTracker", "闪电追踪器");
        put("NoRotate", "禁用旋转");
        put("NickHider", "昵称隐藏");
        put("AntiObbyTrap", "反黑曜石陷阱");
        put("AntiObfuscate", "反混淆");
        put("AutoAnduril", "自动安杜里尔");
        put("InventoryClicker", "背包连点");

        put("BetterChat", "聊天栏美化");
        put("CameraClip", "相机无阻挡");
        put("Trajectories v2", "抛物线 v2");
        put("FreeCamera", "自由视角");
        put("KillEffect", "击杀效果");
        put("TNTTimer", "TNT计时器");
        put("MotionBlur", "动态模糊");

        put("air", "空中");
        put("alerts", "提醒");
        put("alerts-on-pearl", "珍珠提醒");
        put("allow-mining", "允许挖掘");
        put("allow-tools", "允许工具");
        put("angle", "角度");
        put("animals", "动物");
        put("animations", "动画");
        put("armor", "护甲");
        put("arrows", "箭头");
        put("auto-armor", "自动穿甲");
        put("auto-block-require-press", "自动格挡需按住");
        put("auto-close", "自动关闭");
        put("auto-scale", "自动缩放");
        put("auto-swap", "自动切换");
        put("avatar", "头像");
        put("background", "背景");
        put("bar", "色条");
        put("better-bob-view", "优化视角摇晃");
        put("blazes", "烈焰人");
        put("blindness", "失明");
        put("blink-timer", "延迟计时");
        put("block-hit", "格挡");
        put("block-overlay", "方块彩色边框");
        put("blocks-only", "仅手持方块");
        put("blood", "血液");
        put("bossbar", "Boss血条");
        put("bosses", "Boss");
        put("bot-check", "假人检查");
        put("bots", "隐藏假人");
        put("bow", "弓");
        put("bow-sprint", "持弓疾跑");
        put("box", "方框");
        put("box-outline", "方框轮廓");
        put("break-blocks", "破坏方块");
        put("breaking", "挖掘");
        put("canes", "甘蔗");
        put("canes-tracers", "甘蔗追踪线");
        put("caves-only", "仅洞穴");
        put("chat-outline", "聊天描边");
        put("chat-preview", "聊天预览");
        put("check-durability", "检查耐久");
        put("chests", "箱子");
        put("clear", "透明背景");
        put("click-gui", "点击界面");
        put("coal", "煤");
        put("coal-tracers", "煤追踪线");
        put("creepers", "苦力怕");
        put("crosshair", "准星");
        put("debug-log", "调试日志");
        put("diamond", "钻石");
        put("diamonds", "钻石");
        put("diamonds-tracers", "钻石追踪线");
        put("direction-check", "方向检查");
        put("attack-direction", "攻击方向");
        put("glow-size", "泛光大小");
        put("glow-opacity", "泛光透明度");
        put("glow-blur", "泛光模糊度");
        put("animation-speed", "动画速度");
        put("NONE", "无");
        put("DEFAULT", "默认");
        put("HUD", "HUD");
        put("SILENT", "静默");
        put("LOCK_VIEW", "锁定视角");
        put("LOCK VIEW", "锁定视角");
        put("ATTACK", "攻击");
        put("SWING", "挥击");
        put("MANUAL", "手动");
        put("VANILLA", "原版");
        put("FAKE", "伪格挡");
        put("PARTIAL", "部分格挡");
        put("INTERACT A", "交互A");
        put("INTERACT B", "交互B");
        put("SWAP", "切换");
        put("HYPIXEL A", "Hypixel A");
        put("HYPIXEL B", "Hypixel B");
        put("DISTANCE", "距离");
        put("HEALTH", "生命值");
        put("HURT_TIME", "受伤时间");
        put("YAW", "水平角");
        put("RAINBOW", "彩虹");
        put("CHROMA", "彩色渐变");
        put("LEFT", "左");
        put("CENTER", "居中");
        put("RIGHT", "右");
        put("TOP", "上");
        put("MIDDLE", "中");
        put("BOTTOM", "下");
        put("OUTGOING", "发送");
        put("BIDIRECTIONAL", "双向");
        put("GAMMA", "伽马");
        put("EFFECT", "药水效果");
        put("disable-safewalk", "禁用安全行走");
        put("display", "显示样式");
        put("drop-shadow", "阴影");
        put("drop-trash", "丢弃垃圾");
        put("eat-particles", "进食粒子");
        put("effects", "效果");
        put("egg", "蛋");
        put("emerald", "绿宝石");
        put("emeralds", "绿宝石");
        put("emeralds-tracers", "绿宝石追踪线");
        put("enable", "启用");
        put("ender-chests", "末影箱");
        put("endermen", "末影人");
        put("enemies", "敌人");
        put("exp-bar", "经验条");
        put("explosion", "爆炸");
        put("fade-out", "淡出");
        put("fake-check", "假动作检查");
        put("fill", "填充");
        put("fire", "火焰");
        put("fireballs", "火球");
        put("flat", "扁平");
        put("fog", "迷雾");
        put("food-sprint", "饥饿疾跑");
        put("fov-fix", "视野修复");
        put("friends", "好友");
        put("gold", "金");
        put("gold-tracers", "金追踪线");
        put("golems", "傀儡");
        put("ground-only", "仅地面");
        put("ground-spoof", "地面伪装");
        put("head", "头");
        put("health-bar", "显示生命值");
        put("health-color", "血量颜色");
        put("held-item-name", "手持物品名称");
        put("history", "聊天历史");
        put("hotbar", "快捷栏");
        put("hud", "HUD");
        put("hud-shadow", "HUD投影");
        put("hurt-check", "受伤检查");
        put("ignore-self", "忽略自身");
        put("ignore-weapons", "忽略武器");
        put("impact", "打击感");
        put("indicator", "指示器");
        put("intelligent", "智能");
        put("inventory", "背包");
        put("inventory-check", "背包检查");
        put("iron", "铁");
        put("iron-tracers", "铁追踪线");
        put("item-count", "物品计数");
        put("item-spoof", "物品伪装");
        put("ka-only", "仅光环");
        put("lapis", "青金石");
        put("lapis-tracers", "青金石追踪线");
        put("lava", "岩浆");
        put("level", "等级");
        put("lightning", "闪电");
        put("lines", "线条");
        put("lock-move-dey", "锁定移动键");
        put("lock-time", "锁定时间");
        put("lower-case", "小写");
        put("macro", "宏");
        put("macro-on-pearl", "珍珠宏");
        put("map", "地图");
        put("mobs", "怪物");
        put("moon", "月亮");
        put("more-armor", "更多护甲");
        put("more-sword", "更多剑");
        put("name", "名称");
        put("name-check", "名称检查");
        put("nausea", "反胃");
        put("no-push", "无推挤");
        put("notifications", "通知");
        put("obsidian", "黑曜石");
        put("only-crits", "仅暴击");
        put("only-enemy", "仅敌人");
        put("only-ground", "仅地面");
        put("only-self", "仅自身");
        put("only-team", "仅队友");
        put("only-third-person", "仅第三人称");
        put("only-when-looking", "仅注视时");
        put("only-when-moving", "仅移动时");
        put("outline", "轮廓");
        put("pearls", "珍珠");
        put("pitch-check", "俯仰角检查");
        put("place-fix", "放置修复");
        put("placing", "放置");
        put("players", "玩家");
        put("portal", "传送门");
        put("print", "打印");
        put("projectiles", "投射物");
        put("quartz", "石英");
        put("randomization", "随机角度");
        put("reach-only", "仅攻击距离");
        put("redstone", "红石");
        put("redstone-tracers", "红石追踪线");
        put("regen-check", "回血检查");
        put("render-other", "渲染他人");
        put("render-timer", "渲染计时");
        put("require-press", "需按住");
        put("rotate", "旋转");
        put("rotations", "旋转");
        put("rounded", "圆角");
        put("scoreboard", "计分板");
        put("self", "自身");
        put("send-packets", "发送数据包");
        put("set-air", "设置空气");
        put("shadow", "阴影");
        put("show-absorption", "显示吸收");
        put("show-activation-hitbox", "显示触发碰撞箱");
        put("show-armor", "显示护甲");
        put("show-count", "显示数量");
        put("show-distance", "显示距离");
        put("show-hands", "显示手臂");
        put("show-heads", "显示头像");
        put("show-health", "显示生命值");
        put("show-progress", "显示进度");
        put("show-pvp", "显示PvP");
        put("show-self", "显示自己");
        put("show-suffix", "显示后缀");
        put("show-tab", "显示Tab");
        put("show-target", "显示目标");
        put("show-text", "显示文本");
        put("show-ticks", "显示刻数");
        put("show-timer", "显示计时");
        put("silverfish", "蠹虫");
        put("skip-interactable", "跳过可交互");
        put("skip-obsidian", "跳过黑曜石");
        put("skip-trash", "跳过垃圾");
        put("smooth", "平滑");
        put("smooth-number", "平滑数字");
        put("sneak-only", "仅潜行");
        put("sneaking-only", "仅潜行时");
        put("snowball", "雪球");
        put("sound", "音效");
        put("spawners", "刷怪笼");
        put("spawners-tracers", "刷怪笼追踪线");
        put("speed-check", "速度检测");
        put("speed-only", "仅速度/飞行模块");
        put("spin", "旋转");
        put("sprint-only", "仅疾跑时");
        put("suffix", "显示后缀");
        put("suffixes", "后缀");
        put("sun", "太阳");
        put("surroundings", "周围");
        put("swing", "挥手");
        put("switch-back", "切换回来");
        put("sword-sprint", "持剑疾跑");
        put("team-only", "仅队伍");
        put("teams", "队伍");
        put("through-walls", "穿墙");
        put("timestamps", "时间戳");
        put("title", "标题");
        put("toggle-alerts", "切换提醒");
        put("toggle-sounds", "切换音效");
        put("tool-check", "工具检查");
        put("tracers", "追踪线");
        put("trapped-chests", "陷阱箱");
        put("vignette", "暗角");
        put("void-haze", "虚空迷雾");
        put("warts", "地狱疣");
        put("warts-tracers", "地狱疣追踪线");
        put("water", "水");
        put("weapons-only", "仅武器");
        put("weather", "天气");
        put("whitelist", "白名单");

        put("accent-color", "强调色");
        put("bar-color", "进度条颜色");
        put("bg-color", "背景颜色");
        put("color", "颜色");
        put("color-2", "颜色 2");
        put("color-bottom", "底部颜色");
        put("color-top", "顶部颜色");
        put("cross-color", "准星颜色");
        put("custom-color", "自定义颜色");
        put("custom-color-1", "自定义颜色1");
        put("custom-color-2", "自定义颜色");
        put("custom-color-3", "自定义颜色3");
        put("chest", "箱子");
        put("ender-chest", "末影箱");
        put("trapped-chest", "陷阱箱");
        put("fill-color", "填充颜色");
        put("fog-color", "迷雾颜色");
        put("friends-color", "好友颜色");
        put("glow-color", "发光颜色");
        put("impact-color", "打击颜色");
        put("line-color", "线条颜色");
        put("outline-color", "边框颜色");
        put("safe-color", "安全颜色");
        put("secondary-color", "次要颜色");
        put("side-color", "侧面颜色");
        put("slot-color", "槽位颜色");
        put("text-color", "文本颜色");
        put("attack-range", "攻击范围");
        put("auto-block-max-aps", "自动格挡最大APS");
        put("auto-block-min-aps", "自动格挡最小APS");
        put("auto-block-range", "自动格挡距离");
        put("block-hit-ticks", "格挡刻数");
        put("color-speed", "颜色速度");
        put("corner-radius", "圆角半径");
        put("delay", "延迟");
        put("distance", "距离");
        put("dot-radius", "点半径");
        put("duration", "持续时间");
        put("eye-height", "眼睛高度");
        put("fog-end", "迷雾终点");
        put("fog-start", "迷雾起点");
        put("font-scale", "字体缩放");
        put("friction", "摩擦力");
        put("height-offset", "高度偏移");
        put("hit-box-horizontal", "碰撞箱水平");
        put("hit-box-vertical", "碰撞箱垂直");
        put("horizontal-speed", "水平速度");
        put("hud-scale", "HUD缩放");
        put("line-width", "线条宽度");
        put("max-delay", "最大延迟");
        put("min-delay", "最小延迟");
        put("motion", "移动倍率");
        put("multiplier", "倍率");
        put("offset", "偏移");
        put("padding", "内边距");
        put("pos-x", "X坐标");
        put("pos-y", "Y坐标");
        put("pos-z", "Z坐标");
        put("radius", "半径");
        put("range", "范围");
        put("ratio", "比例");
        put("rot-x", "旋转X");
        put("rot-y", "旋转Y");
        put("rot-z", "旋转Z");
        put("scale", "缩放");
        put("size", "大小");
        put("speed", "速度");
        put("speed-motion", "速度效果移动倍率");
        put("spin-speed", "旋转速度");
        put("strength", "强度");
        put("swing-range", "挥动距离");
        put("swing-speed", "挥手速度");
        put("third-person-distance", "第三人称距离");
        put("travel-time", "飞行时间");
        put("vertical-speed", "垂直速度");
        put("x-offset", "X 偏移");
        put("y-offset", "Y 偏移");
        put("alerts-frequency", "提醒频率");
        put("alerts-range", "提醒范围");
        put("amount", "数量");
        put("angle-step", "角度步进");
        put("arrow", "箭");
        put("auto-armor-interval", "自动护甲间隔");
        put("axe-slot", "斧头槽位");
        put("blocks", "方块");
        put("blocks-slot", "方块槽位");
        put("bow-slot", "弓槽位");
        put("caves-radius", "洞穴半径");
        put("close-delay", "关闭延迟");
        put("count", "数量");
        put("debug", "调试");
        put("delay-ticks", "延迟刻数");
        put("fov", "视野");
        put("gold-apple-slot", "金苹果槽位");
        put("height", "高度");
        put("hold", "按住");
        put("hold-time", "按住时间");
        put("hud-offset-x", "HUD X偏移");
        put("hud-offset-y", "HUD Y偏移");
        put("hurt-time", "受伤时间");
        put("interval", "间隔");
        put("life", "生命");
        put("macro-delay", "宏延迟");
        put("macro-range", "宏范围");
        put("max-aps", "最大APS");
        put("max-count", "最大数量");
        put("max-cps", "最大CPS");
        put("max-players", "最大玩家数");
        put("max-timeout", "最大超时");
        put("min-aps", "最小APS");
        put("min-cps", "最小CPS");
        put("number-delay", "数字延迟");
        put("offset-x", "偏移X");
        put("offset-y", "垂直偏移");
        put("open-delay", "打开延迟");
        put("pickaxe-slot", "镐子槽位");
        put("place-delay", "放置延迟");
        put("points", "路径点数");
        put("position", "位置");
        put("projectile-slot", "投射物槽位");
        put("radar-radius", "雷达半径");
        put("random", "随机");
        put("render-time", "渲染时间");
        put("rotation-tolerance", "旋转容差");
        put("shovel-slot", "铲槽位");
        put("steps", "步数");
        put("switch-delay", "切换延迟");
        put("sword-slot", "剑槽位");
        put("text-offset", "文字偏移");
        put("ticks", "刻数");
        put("time", "时间");
        put("update-delay", "更新延迟");
        put("updates-per-tick", "每刻更新数");
        put("width", "宽度");

        put("alerts-sound", "提醒音效");
        put("auto-block", "自动防砍");
        put("bow-mode", "弓模式");
        put("color-mode", "颜色模式");
        put("enabled-while", "启用时机");
        put("food-mode", "食物模式");
        put("hud-position-x", "HUD位置X");
        put("hud-position-y", "HUD位置Y");
        put("ignore-velocity", "忽略击退");
        put("mode", "模式");
        put("move-fix", "移动修复");
        put("physics", "物理");
        put("position-x", "位置X");
        put("position-y", "位置Y");
        put("render-mode", "渲染模式");
        put("show-hitbox", "显示碰撞箱");
        put("show-position", "显示位置");
        put("sort", "排序");
        put("style", "样式");
        put("sword-mode", "剑模式");
        put("team-check-mode", "队伍检查模式");
        put("trigger", "触发");

        put("background-alpha", "背景不透明度");
        put("bg-alpha", "背景不透明度");
        put("bow-motion", "弓运动");
        put("chance", "概率");
        put("color-brightness", "颜色亮度");
        put("color-saturation", "颜色饱和度");
        put("delay-chance", "延迟概率");
        put("density", "密度");
        put("explosions-horizontal", "爆炸水平");
        put("explosions-vertical", "爆炸垂直");
        put("fill-alpha", "填充不透明度");
        put("food-motion", "食物运动");
        put("health", "生命值");
        put("horizontal", "横轴");
        put("input-bg-alpha", "输入背景不透明度");
        put("intensity", "强度");
        put("line-alpha", "线条不透明度");
        put("opacity", "不透明度");
        put("shadow-alpha", "阴影不透明度");
        put("slowdown", "减速");
        put("smoothing", "平滑度");
        put("spread", "扩散");
        put("strafe", "侧移");
        put("sword-motion", "剑运动");
        put("vertical", "垂直");

        put("keywords", "关键词");
        put("macro-text", "宏文本");
        put("subtitle", "副标题");
        put("text", "文本");

        put("Arrows", "箭头");
        put("BlockOverlay", "方块彩色边框");
        put("BreakProgress", "破坏进度");
        put("FallView", "落距显示");
        put("Holdlook", "按住视角");
        put("MobESP", "怪物透视");
        put("DamageTags", "伤害标签");
        put("PotionHUD", "药水HUD");
        put("ItemPhysics", "物品物理");
        put("DamageTint", "受伤颜色");
        put("Saturation", "饱和度");
        put("BlockESP", "方块透视");
        put("Freelook", "自由视角");

        put("arrow-type", "箭头样式");
        put("circle-radius", "圆形半径");
        put("team-color", "队伍颜色");
        put("hide-teammates", "隐藏队友");
        put("render-friends", "渲染好友");
        put("render-enemies", "渲染敌人");
        put("render-distance", "渲染距离");
        put("render-only-offscreen", "仅渲染屏幕外");
        put("overlay-visible", "覆盖可见");
        put("overlay-color-mode", "覆盖颜色模式");
        put("overlay-color", "覆盖层颜色");
        put("overlay-color-2", "覆盖颜色2");
        put("overlay-fade-speed", "覆盖渐变速度");
        put("overlay-chroma-speed", "覆盖彩虹速度");
        put("outline-visible", "描边可见");
        put("outline-color-mode", "描边颜色模式");
        put("outline-color-2", "描边颜色2");
        put("outline-fade-speed", "描边渐变速度");
        put("outline-chroma-speed", "描边彩虹速度");
        put("thickness", "线宽");
        put("persistence", "持续显示");
        put("depthless", "忽略深度");
        put("barriers", "屏障");
        put("hide-plants", "隐藏植物");
        put("show-manual", "手动显示");
        put("fade-in", "渐入");
        put("damage-threshold", "伤害阈值");
        put("disable-while-flying", "飞行时禁用");
        put("only-while-sneaking", "仅潜行时");
        put("override-health-format", "覆盖血量格式");
        put("show-damage", "显示伤害");
        put("rear-cam-key", "后视按键");
        put("front-cam-key", "前视按键");
        put("shaded", "着色");
        put("ghasts", "恶魂");
        put("skeletons", "骷髅");
        put("slimes", "史莱姆");
        put("spiders", "蜘蛛");
        put("zombies", "僵尸");
        put("zombie-pigman", "僵尸猪人");
        put("depth", "深度");
        put("text-shadow", "文字阴影");
        put("background-opacity", "背景不透明度");
        put("time-format", "时间格式");
        put("sort-by", "排序依据");
        put("sort-direction", "排序方向");
        put("horizontal-alignment", "水平对齐");
        put("vertical-alignment", "垂直对齐");
        put("edit-position", "编辑位置");
        put("exclude-permanent", "排除永久效果");
        put("draw-background", "绘制背景");
        put("background-blur", "背景模糊");
        put("snap-edges", "吸附屏幕边缘");
        put("edge-line", "贴边线条");
        put("potion-blacklist", "药水黑名单");
        put("rotation-speed", "旋转速度");
        put("tint-color", "染色颜色");
        put("tint-alpha", "透明度");
        put("saturation", "饱和度");
        put("shade", "面着色");
        put("max-renders", "最大渲染数");
        put("scan-speed", "扫描速度");
        put("freelook-key", "自由视角按键");
        put("invert-pitch", "反转俯仰");
        put("lock-pitch", "锁定俯仰");
        put("custom-fov", "自定义FOV");
        put("Edit the HUD position by dragging.", "拖拽以编辑HUD位置。");
        put("Reset position", "重置位置");

        put("Scaffold", "自动搭路");
        put("ground-motion", "地面速度");
        put("air-motion", "空中速度");
        put("tower", "高塔");
        put("keep-y", "保持高度");
        put("keep-y-on-press", "按住时保持高度");
        put("no-keep-y-on-jump-potion", "跳跃药水时不保持高度");
        put("multi-place", "连续放置");
        put("safe-walk", "防跌落");
        put("block-counter", "方块计数");
        put("God Bridge", "神桥");
        put("hold-block", "自动持块");
        put("only-when-sneak", "仅潜行时");
        put("auto-jump", "自动跳跃");
        put("disable-guard", "禁用防护");
        put("show-activation-bounds", "显示激活范围");
        put("smooth-aim", "平滑瞄准");
        put("start-delay", "启动延迟");
        put("activation-delay", "激活延迟");
        put("lock-rotation", "锁定视角");

        put("background-border", "背景边框");
        put("friend-color", "好友颜色");
        put("enemy-color", "敌人颜色");
        put("hide-vanilla", "隐藏原版名字");
        put("only-render-name", "仅渲染名字");
        put("show-invis", "显示隐身玩家");
        put("show-enchants", "显示附魔");
        put("show-durability", "显示耐久");
        put("show-yourself", "显示自己");

        put("auto-send", "自动发送");
        put("breadcrumbs", "轨迹面包屑");
        put("direction", "方向");
        put("flick-delay", "甩动延迟");
        put("randomize-offset", "随机偏移");
        put("randomize-offset-range", "随机偏移范围");
        put("select-hits", "选择时机");
        put("spawn-fake", "生成假人");
        put("strafe-invert", "侧移反转");
        put("use-blink", "扣包");

        put("camera-distance", "相机距离");
        put("camera-height", "相机高度");
        put("look-pitch", "俯视角度");
        put("sensitivity", "灵敏度");
        put("stretch-speed", "拉伸速度");
        put("HOLD", "按住");
        put("TOGGLE", "切换");

        put("glass", "磨砂玻璃");
        put("glass-chat", "聊天磨砂");
        put("glass-gui", "界面磨砂");
        put("glass-list", "模块列表磨砂");
        put("glass-notify", "通知磨砂");
        put("glass-scoreboard", "公告栏磨砂");
        put("glass-tab", "玩家列表磨砂");
        put("glass-target", "目标血条磨砂");
        put("tab-animation", "玩家列表动画");
        put("smooth-list", "平滑列表");

        put("scoreboard-scale", "公告栏大小");
        put("scoreboard-rows", "公告栏行数");
        put("scoreboard-offset-x", "公告栏横向偏移");
        put("scoreboard-offset-y", "公告栏纵向偏移");

        put("Caret", "箭头形");
        put("CUSTOM", "自定义");
        put("Fireball", "火球跳");
        put("FRIEND_MANAGER", "好友管理");
        put("LEGIT", "合法");
        put("MYAU", "主题色");
        put("PACKET", "数据包");
        put("Ready", "就绪");
        put("SINGLE", "单体");
        put("SOFT", "柔和");
        put("SOUP", "汤");
        put("SECOND", "秒");

        load();
    }

    private Lang() {
    }

    private static void put(String en, String zh) {
        DICT.put(en, zh);
    }

    public static boolean isChinese() {
        return ZH.equals(language);
    }

    public static String languageLabel() {
        return isChinese() ? "中文" : "English";
    }

    public static String t(String en) {
        if (en == null || !isChinese()) {
            return en;
        }
        String zh = DICT.get(en);
        if (zh == null) {
            for (Map.Entry<String, String> entry : DICT.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(en)) {
                    zh = entry.getValue();
                    break;
                }
            }
        }
        if (zh == null && en.indexOf(' ') >= 0) {
            zh = DICT.get(en.replace(' ', '-'));
        }
        if (zh == null && en.indexOf('-') >= 0) {
            zh = DICT.get(en.replace('-', ' '));
        }
        return zh != null ? zh : en;
    }

    public static void toggle() {
        setLanguage(isChinese() ? EN : ZH);
    }

    public static void setLanguage(String lang) {
        String normalized = ZH.equalsIgnoreCase(lang) ? ZH : EN;
        if (normalized.equals(language)) {
            return;
        }
        language = normalized;
        save();
    }

    public static void load() {
        if (!LANG_FILE.exists()) {
            language = ZH;
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(LANG_FILE))) {
            String line = reader.readLine();
            if (line != null) {
                String value = line.trim();
                language = ZH.equalsIgnoreCase(value) ? ZH : EN;
            } else {
                language = ZH;
            }
        } catch (IOException e) {
            language = ZH;
        }
    }

    public static void save() {
        try {
            File parent = LANG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(LANG_FILE)) {
                writer.write(language);
            }
        } catch (IOException ignored) {
        }
    }
}
