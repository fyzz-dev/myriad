package dev.myriad.essentials.modules.render;

import dev.myriad.api.Myriad;
import dev.myriad.api.event.Subscribe;
import dev.myriad.api.event.events.Render2DEvent;
import dev.myriad.api.module.Categories;
import dev.myriad.api.module.Module;
import dev.myriad.api.module.Modules;
import dev.myriad.api.render.Canvas;
import dev.myriad.api.render.FontFamily;
import dev.myriad.api.render.PlayerHeads;
import dev.myriad.api.render.Projection;
import dev.myriad.api.setting.BoolSetting;
import dev.myriad.api.setting.ColorSetting;
import dev.myriad.api.setting.DoubleSetting;
import dev.myriad.api.setting.SettingColor;
import dev.myriad.api.setting.SettingGroup;
import dev.myriad.api.util.ColorUtil;
import dev.myriad.api.util.Entities;
import dev.myriad.api.util.ItemInfo;
import dev.myriad.essentials.util.PopColors;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replaces player name tags with Myriad's: head, name, health, ping, totem pops and gamemode, with their armour and
 * held items above. Readable at any distance and drawn through walls. Vanilla player name tags are hidden while on.
 */
public class Nametags extends Module {

	private static final float PAD_X = 3, PAD_Y = 2, GAP = 3, ITEM = 12, ITEM_GAP = 14;

	private final BoolSetting self = sgGeneral.bool("Self").description("Your own tag in third person.").defaultValue(true).build();
	private final BoolSetting head = sgGeneral.bool("Head").description("Draw the player's face before the name.").defaultValue(true).build();
	private final BoolSetting gamemode = sgGeneral.bool("Gamemode").build();
	private final BoolSetting health = sgGeneral.bool("Health").defaultValue(true).build();
	private final BoolSetting ping = sgGeneral.bool("Ping").defaultValue(true).build();
	private final BoolSetting totemPops = sgGeneral.bool("Totem Pops").defaultValue(true).build();
	private final DoubleSetting scale = sgGeneral.doubleSetting("Scale").defaultValue(1.1).range(0.3, 4).decimals(1).build();
	private final DoubleSetting offset = sgGeneral.doubleSetting("Offset").description("Height above the head, in blocks.").defaultValue(0.4).range(-2, 3).decimals(1).build();
	private final DoubleSetting range = sgGeneral.doubleSetting("Range").description("0 = no limit.").defaultValue(0).range(0, 256).decimals(0).build();

	private final SettingGroup sgItems = settings.group("Items");
	private final BoolSetting armor = sgItems.bool("Armor").defaultValue(true).build();
	private final BoolSetting heldItems = sgItems.bool("Held Items").defaultValue(true).build();
	private final BoolSetting heldItemName = sgItems.bool("Held Item Name").defaultValue(true).visible(heldItems::get).build();
	private final BoolSetting durability = sgItems.bool("Durability").description("Durability percentage over armour.").visible(armor::get).build();
	private final ColorSetting useProgress = sgItems.color("Use Progress").description("Bar over the item being eaten or used.").defaultValue(0x5000C800).visible(heldItems::get).build();

	private final SettingGroup sgStyle = settings.group("Style");
	private final BoolSetting background = sgStyle.bool("Background").defaultValue(true).build();
	private final BoolSetting rounded = sgStyle.bool("Rounded").defaultValue(true).visible(background::get).build();
	private final ColorSetting fill = sgStyle.color("Fill").defaultValue(0x64000000).visible(background::get).build();
	private final ColorSetting line = sgStyle.color("Line").defaultValue(SettingColor.role(SettingColor.Mode.ACCENT, 0xC0)).visible(background::get).build();
	private final ColorSetting nameColor = sgStyle.color("Name").defaultValue(SettingColor.role(SettingColor.Mode.TEXT)).build();
	private final ColorSetting friendColor = sgStyle.color("Friends").defaultValue(SettingColor.role(SettingColor.Mode.CYAN)).build();

	private record Segment(String text, int color) {
	}

	public Nametags() {
		super(Categories.RENDER, "Nametags", "Better player name tags.");
	}

	/** Vanilla player labels are hidden while this is on. */
	public static boolean hidesVanilla() {
		return Modules.isActive(Nametags.class);
	}

	@Subscribe
	private void onRender(Render2DEvent e) {
		if (!inGame()) return;
		Canvas c = e.canvas();
		Vec3d cam = Projection.camera();
		List<PlayerEntity> players = new ArrayList<>(mc.world.getPlayers());
		// Far tags first so near ones draw on top.
		players.sort(Comparator.comparingDouble(p -> -p.squaredDistanceTo(cam)));
		for (PlayerEntity p : players) {
			if (!p.isAlive()) continue;
			if (p == mc.player && (!self.get() || mc.options.getPerspective().isFirstPerson())) continue;
			if (range.get() > 0 && p.squaredDistanceTo(cam) > range.get() * range.get()) continue;
			draw(c, p, e.tickDelta());
		}
	}

	private void draw(Canvas c, PlayerEntity p, float tickDelta) {
		Box box = Entities.lerpedBox(p, tickDelta);
		Vec3d at = new Vec3d(box.getCenter().x, box.maxY + offset.get(), box.getCenter().z);
		Vec3d s = Projection.toScreen(at);
		if (s == null || !Projection.onScreen(s, 100)) return;
		double dist = Projection.camera().distanceTo(at);
		float k = (float) (scale.get() * Math.clamp(1.0 - dist * 0.01, 0.5, 1.0));
		float size = c.defaultFontSize() * k;
		float th = c.textHeight(FontFamily.SANS, size);

		List<Segment> segs = segments(p);
		float width = 0;
		for (int i = 0; i < segs.size(); i++) width += c.textWidth(FontFamily.SANS, size, segs.get(i).text) + (i > 0 ? GAP * k : 0);
		boolean face = head.get() && p instanceof AbstractClientPlayerEntity;
		if (face) width += th + GAP * k;
		float w = width + PAD_X * 2 * k, h = th + PAD_Y * 2 * k;
		float x = (float) s.x - w / 2, y = (float) s.y - h;

		if (background.get()) {
			float r = rounded.get() ? Math.min(Myriad.ui().theme().rounding.get(), h / 2) : 0;
			c.roundRect(x, y, w, h, r, fill.argb());
			if ((line.argb() >>> 24) != 0) c.outline(x, y, w, h, r, 1, line.argb());
		}
		float cx = x + PAD_X * k, ty = y + PAD_Y * k;
		int nameIndex = gamemode.get() ? 1 : 0;
		for (int i = 0; i < segs.size(); i++) {
			if (i == nameIndex && face) {
				PlayerHeads.draw(c, p, cx, ty, th);
				cx += th + GAP * k;
			}
			Segment seg = segs.get(i);
			c.text(FontFamily.SANS, size, seg.text, cx + 0.6f, ty + 0.6f, 0x99000000);
			cx += c.text(FontFamily.SANS, size, seg.text, cx, ty, seg.color) + GAP * k;
		}
		if (armor.get() || heldItems.get()) drawItems(c, p, (float) s.x, y - 1, k, size);
	}

	/** The optional gamemode, the name, then the stats. */
	private List<Segment> segments(PlayerEntity p) {
		List<Segment> segs = new ArrayList<>();
		PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
		if (gamemode.get()) segs.add(new Segment(p.isCreative() ? "[C]" : p.isSpectator() ? "[SP]" : "[S]", 0xFFAAAAAA));
		segs.add(new Segment(p.getGameProfile().getName(), Entities.isFriend(p) ? friendColor.argb() : nameColor.argb()));
		if (health.get()) {
			float hp = p.getHealth() + p.getAbsorptionAmount();
			segs.add(new Segment(String.format("%.1f", hp), ColorUtil.lerp(0xFFFF5555, 0xFF55FF55, Math.clamp(hp / p.getMaxHealth(), 0f, 1f))));
		}
		if (ping.get()) segs.add(new Segment((entry == null ? 0 : entry.getLatency()) + "ms", 0xFFCCCCCC));
		if (totemPops.get()) {
			int pops = Myriad.server().totemPops(p);
			if (pops > 0) segs.add(new Segment("-" + pops, PopColors.of(pops)));
		}
		return segs;
	}

	private void drawItems(Canvas c, PlayerEntity p, float centerX, float bottom, float k, float textSize) {
		List<ItemStack> stacks = new ArrayList<>(6);
		if (heldItems.get()) stacks.add(p.getOffHandStack());
		if (armor.get()) {
			stacks.add(p.getEquippedStack(EquipmentSlot.HEAD));
			stacks.add(p.getEquippedStack(EquipmentSlot.CHEST));
			stacks.add(p.getEquippedStack(EquipmentSlot.LEGS));
			stacks.add(p.getEquippedStack(EquipmentSlot.FEET));
		}
		if (heldItems.get()) stacks.add(p.getMainHandStack());
		if (stacks.stream().allMatch(ItemStack::isEmpty)) return;

		float item = ITEM * k, step = ITEM_GAP * k;
		float x0 = centerX - stacks.size() * step / 2, y = bottom - item - (durability.get() ? c.textHeight(FontFamily.SANS, textSize * 0.6f) : 0);
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack st = stacks.get(i);
			if (st.isEmpty()) continue;
			float ix = x0 + i * step + (step - item) / 2;
			boolean main = heldItems.get() && i == stacks.size() - 1, off = heldItems.get() && i == 0;
			if (p.isUsingItem() && ((main && p.getActiveHand() == Hand.MAIN_HAND) || (off && p.getActiveHand() == Hand.OFF_HAND))) {
				int max = p.getActiveItem().getMaxUseTime(p);
				float prog = max <= 0 ? 0 : Math.clamp((max - p.getItemUseTimeLeft()) / (float) max, 0f, 1f);
				c.rect(ix, y, item * prog, item, useProgress.argb());
			}
			c.item(st, ix, y, item);
			if (durability.get() && st.isDamageable() && !main && !off) {
				float pct = ItemInfo.durabilityFraction(st);
				String t = Math.round(pct * 100) + "%";
				float ts = textSize * 0.6f;
				c.text(FontFamily.SANS, ts, t, ix + (item - c.textWidth(FontFamily.SANS, ts, t)) / 2, y + item, ColorUtil.lerp(0xFFFF5555, 0xFF55FF55, pct));
			}
		}
		if (heldItems.get() && heldItemName.get() && !p.getMainHandStack().isEmpty()) {
			String name = p.getMainHandStack().getName().getString();
			float ts = textSize * 0.75f;
			float tw = c.textWidth(FontFamily.SANS, ts, name);
			c.text(FontFamily.SANS, ts, name, centerX - tw / 2, y - c.textHeight(FontFamily.SANS, ts) - 1, 0xFFFFFFFF);
		}
	}
}
