package justfatlard.minecart_mania.cart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * A furnace cart with a throttle, a reverser and a switch.
 *
 * <p>Vanilla's furnace cart has one speed, three quarters of a plain cart's, and one control:
 * the side you fed it from. Here it runs at the full pace of the rail on notch four and a
 * quarter of that on notch one, burning fuel in proportion; a reverser turns it round without
 * a walk to the other end; and a switch stops it in place with its fire still lit. An
 * activator rail throws the switch, so a station can stop and start it.
 */
public final class FurnaceControls {
	private FurnaceControls() {}

	public record Settings(int level, boolean on, boolean reversed) {
		public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("level").forGetter(Settings::level),
			Codec.BOOL.fieldOf("on").forGetter(Settings::on),
			Codec.BOOL.fieldOf("reversed").forGetter(Settings::reversed)
		).apply(i, Settings::new));
		public static final Settings DEFAULT = new Settings(4, true, false);
	}

	private static final AttachmentType<Settings> SETTINGS = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "furnace"), Settings.CODEC);

	public static final String SCREEN = Main.MOD_ID + ":furnace";
	/** Who has which cart's controls open; the press comes back with only the player. */
	private static final Map<UUID, UUID> open = new ConcurrentHashMap<>();
	/** The tick each cart last saw a powered activator rail, so one rail throws the switch once. */
	private static final Map<UUID, Long> activated = new ConcurrentHashMap<>();

	/** Extra push per tick per notch, on top of vanilla's: what "bumped strength" means. */
	private static final double PUSH_PER_LEVEL = 0.012;
	/** Ticks of fire a cart holds, vanilla's figure. */
	public static final int FUEL_LIMIT = 32000;
	/** Vanilla gives a cart three thousand six hundred ticks for a coal that burns sixteen hundred in a furnace. */
	private static final double CART_PER_FURNACE_TICK = 3600.0 / 1600.0;
	/** Who has which cart's panel open, so the fire can be watched and the slot kept true. */
	private static final Map<UUID, FuelSlot> slots = new ConcurrentHashMap<>();
	/** What is waiting in the slot: kept on the cart, the way a furnace keeps what is in its. */
	private static final AttachmentType<ItemStack> FUEL_SLOT = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "fuel_slot"), ItemStack.OPTIONAL_CODEC);
	/** What the last thing on the fire was worth, which is what the flame is measured against. */
	private static final AttachmentType<Integer> LIT_WORTH = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "lit_worth"), Codec.INT);
	/** The way the cart was last being pushed, to push it that way again when it is relit. */
	private static final AttachmentType<Vec3> PUSH_DIR = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "push"), Vec3.CODEC);
	/** The flame's place on the panel, and its full height. */
	private static final int FLAME_X = 151;
	private static final int FLAME_Y = 44;
	private static final int FLAME = 14;
	private static final int WATCH_EVERY = 10;

	public static Settings of(MinecartFurnace cart) {
		return cart.getAttachedOrElse(SETTINGS, Settings.DEFAULT);
	}

	public static void set(MinecartFurnace cart, Settings settings) {
		cart.setAttached(SETTINGS, settings);
	}

	public static void register() {
		var screens = PandoricalApi.screens();
		for (int level = 1; level <= 4; level++) {
			int notch = level;
			screens.onAction(SCREEN, "notch" + level, (player, data) -> with(player, (cart, s) -> new Settings(notch, s.on(), s.reversed())));
		}
		screens.onAction(SCREEN, "reverse", (player, data) -> with(player, (cart, s) -> {
			// Turned round on the spot: the push, the stored way, and the motion itself. Flipping
			// the push alone left the cart coasting on for a while against it, and a panel that
			// said "Reversed" over a cart still going the old way was the whole complaint. The
			// stored way turns too, or a stopped cart set going again would set off the old way.
			Vec3 dir = cart.getAttached(PUSH_DIR);
			if (dir != null) cart.setAttached(PUSH_DIR, dir.scale(-1.0));
			cart.push = cart.push.scale(-1.0);
			cart.setDeltaMovement(cart.getDeltaMovement().scale(-1.0));
			return new Settings(s.level(), s.on(), !s.reversed());
		}));
		screens.onAction(SCREEN, "toggle", (player, data) -> with(player, (cart, s) -> new Settings(s.level(), !s.on(), s.reversed())));
		screens.onClose(SCREEN, player -> {
			open.remove(player.getUUID());
			slots.remove(player.getUUID());
		});
	}

	/**
	 * Ticks of fire one of these is worth to the cart: what it burns for in a furnace, scaled
	 * the way vanilla scales coal. Nought for anything a furnace would not burn.
	 */
	public static int fuelWorth(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.COOKING_FUEL)) return 0;
		LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY)).create(Optional.empty());
		int furnace = ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
		return (int) Math.round(furnace * CART_PER_FURNACE_TICK);
	}

	/**
	 * The panel's one slot: a window on what the cart is carrying for its fire. Nothing goes on
	 * the fire from here; the cart takes one from it when the fire is out and it is running,
	 * the way a furnace takes from its own slot only while it has something to cook. It used to
	 * put everything on the fire the moment it went in, running or not, which emptied a stack
	 * of coal into a parked cart.
	 */
	private static final class FuelSlot extends SimpleContainer {
		final MinecartFurnace cart;
		private boolean settling;

		FuelSlot(MinecartFurnace cart) {
			super(1);
			this.cart = cart;
			settle();
		}

		/** The slot shown to match what the cart holds. */
		void settle() {
			settling = true;
			setItem(0, cart.getAttachedOrElse(FUEL_SLOT, ItemStack.EMPTY).copy());
			settling = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!settling) cart.setAttached(FUEL_SLOT, getItem(0).copy());
		}
	}

	/** What is waiting in the cart's slot. */
	public static ItemStack inSlot(MinecartFurnace cart) {
		return cart.getAttachedOrElse(FUEL_SLOT, ItemStack.EMPTY);
	}

	/** One from the slot onto the fire, if the fire will take it; whether it did. */
	private static boolean feed(ServerLevel level, MinecartFurnace cart) {
		if (!(cart instanceof FurnaceFuel fire)) return false;
		ItemStack stack = inSlot(cart).copy();
		int worth = fuelWorth(level, stack);
		if (worth <= 0 || fire.minecartMania$fuel() + worth > FUEL_LIMIT) return false;
		fire.minecartMania$addFuel(worth);
		stack.shrink(1);
		cart.setAttached(FUEL_SLOT, stack);
		cart.setAttached(LIT_WORTH, worth);
		// Vanilla drops the push when the fire goes out; the cart is pushed on again.
		cart.push = pushOf(cart);
		for (FuelSlot slot : slots.values()) {
			if (slot.cart == cart) slot.settle();
		}
		return true;
	}

	/** The way the cart will push from now on: set when it is placed, or turned round. */
	public static void setHeading(MinecartFurnace cart, Direction heading) {
		cart.setAttached(PUSH_DIR, Vec3.atLowerCornerOf(heading.getUnitVec3i()));
	}

	/** The way the cart is pushed: the way it was last going, or the way it points if it never went. */
	public static Vec3 pushOf(MinecartFurnace cart) {
		Vec3 dir = cart.getAttached(PUSH_DIR);
		return dir != null ? dir : Vec3.atLowerCornerOf(Headings.of(cart).getUnitVec3i());
	}

	/** How high the flame stands, in pixels of its fourteen: the fire against what last lit it. */
	private static int flameHeight(MinecartFurnace cart) {
		int fuel = cart instanceof FurnaceFuel fire ? fire.minecartMania$fuel() : 0;
		if (fuel <= 0) return 0;
		int worth = Math.max(1, cart.getAttachedOrElse(LIT_WORTH, 3600));
		return Math.max(1, Math.min(FLAME, (int) Math.round(FLAME * Math.min(1.0, fuel / (double) worth))));
	}

	/** The lit flame, drawn from the bottom up to this height, the way a furnace's burns down. */
	private static Map<String, String> flameProps(int height) {
		return Map.of(
			ComponentType.PROP_Y, String.valueOf(FLAME_Y + FLAME - height),
			ComponentType.PROP_HEIGHT, String.valueOf(height),
			"texture_v", String.valueOf(FLAME - height));
	}

	/**
	 * Which way the cart is going, as a compass point. A furnace cart is the same shape both
	 * ways round, so "forward" is not a thing it has; the panel used to say Forward or Reversed
	 * and the word came adrift from the cart the first time anything else turned it.
	 */
	private static String headingText(MinecartFurnace cart) {
		Direction heading = Headings.of(cart);
		boolean moving = cart.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
		String name = Character.toUpperCase(heading.getName().charAt(0)) + heading.getName().substring(1);
		return (moving ? "Heading " : "Facing ") + name;
	}

	private static String fireText(MinecartFurnace cart) {
		int fuel = cart instanceof FurnaceFuel fire ? fire.minecartMania$fuel() : 0;
		return fuel <= 0 ? "Fire out" : "Fire " + (fuel / 20 / 60) + ":" + String.format("%02d", fuel / 20 % 60);
	}

	/** Everyone watching this cart's panel sees the flame as it is now. */
	private static void watch(ServerLevel level, MinecartFurnace cart) {
		for (Map.Entry<UUID, UUID> watching : open.entrySet()) {
			if (!watching.getValue().equals(cart.getUUID())) continue;
			if (!(level.getServer().getPlayerList().getPlayer(watching.getKey()) instanceof ServerPlayer player)) continue;
			PandoricalApi.screens().update(player, SCREEN, List.of(
				new ComponentUpdate("flame", flameProps(flameHeight(cart))),
				new ComponentUpdate("fuel", Map.of("text", fireText(cart))),
				new ComponentUpdate("heading", Map.of("text", headingText(cart)))));
		}
	}

	private interface Change {
		Settings apply(MinecartFurnace cart, Settings current);
	}

	private static void with(ServerPlayer player, Change change) {
		UUID cartId = open.get(player.getUUID());
		if (cartId == null || !(player.level() instanceof ServerLevel level)) return;
		if (!(level.getEntity(cartId) instanceof MinecartFurnace cart)) return;
		set(cart, change.apply(cart, of(cart)));
		refresh(player, cart);
	}

	/** The controls redrawn in place after a press: reopening the panel recentred the cursor every click. */
	private static void refresh(ServerPlayer player, MinecartFurnace cart) {
		Settings s = of(cart);
		List<ComponentUpdate> updates = new ArrayList<>();
		for (int level = 1; level <= 4; level++) {
			updates.add(new ComponentUpdate("notch" + level, Map.of(ComponentType.PROP_STYLE, level == s.level() ? "accepted" : "default")));
		}
		updates.add(new ComponentUpdate("heading", Map.of("text", headingText(cart))));
		updates.add(new ComponentUpdate("toggle", Map.of(
			ComponentType.PROP_LABEL, s.on() ? "Running" : "Stopped",
			ComponentType.PROP_STYLE, s.on() ? "accepted" : "default")));
		PandoricalApi.screens().update(player, SCREEN, updates);
	}

	/** The controls: four notches, the reverser, the switch, and a slot for the fire. */
	public static void openScreen(ServerPlayer player, MinecartFurnace cart) {
		open.put(player.getUUID(), cart.getUUID());
		Settings s = of(cart);
		int width = 176;
		int packY = 100;
		int height = packY + 3 * 18 + 4 + 18 + 8;
		// The id is what the client matches a redraw against, and the builder mints its own
		// unless told; addressed by type, every press was applied and never drawn.
		ScreenBuilder screen = new ScreenBuilder(SCREEN).id(SCREEN).container(1, true).size(width, height).pauseGame(false);
		screen.panel("bg", 0, 0, width, height, Map.of("border", "beveled"));
		screen.text("title", 8, 6, Map.of("text", "Furnace Cart", "color", "#404040"));
		screen.text("throttle", 8, 22, Map.of("text", "Speed", "color", "#404040"));
		for (int level = 1; level <= 4; level++) {
			screen.button("notch" + level, 44 + (level - 1) * 24, 18, 20, 16, Map.of(
				ComponentType.PROP_LABEL, String.valueOf(level),
				ComponentType.PROP_STYLE, level == s.level() ? "accepted" : "default"));
		}
		screen.button("reverse", 8, 40, 62, 18, Map.of(ComponentType.PROP_LABEL, "Turn around"));
		screen.button("toggle", 80, 40, 62, 18, Map.of(
			ComponentType.PROP_LABEL, s.on() ? "Running" : "Stopped",
			ComponentType.PROP_STYLE, s.on() ? "accepted" : "default"));
		screen.text("heading", 8, 62, Map.of("text", headingText(cart), "color", "#404040"));
		screen.text("fuel", 8, 74, Map.of("text", fireText(cart), "color", "#404040"));
		// The furnace's own flame: the unlit outline from its screen, the lit one over it.
		screen.sprite("flame_bg", FLAME_X, FLAME_Y, FLAME, FLAME, Map.of(
			"texture", "minecraft:textures/gui/container/furnace.png",
			"texture_width", "256", "texture_height", "256", "texture_u", "56", "texture_v", "36"));
		int lit = flameHeight(cart);
		Map<String, String> flame = new java.util.HashMap<>(flameProps(lit));
		flame.put("texture", "minecraft:textures/gui/sprites/container/furnace/lit_progress.png");
		flame.put("texture_width", String.valueOf(FLAME));
		flame.put("texture_height", String.valueOf(FLAME));
		flame.put("texture_u", "0");
		screen.sprite("flame", FLAME_X, FLAME_Y + FLAME - lit, FLAME, lit, flame);
		screen.inventoryGrid("fuel_slot", FLAME_X - 1, FLAME_Y + FLAME + 4, 1, 1, 0);
		screen.text("pack_label", 8, packY - 12, Map.of("text", "Inventory", "color", "#404040"));
		screen.inventoryGrid("pack", 8, packY, 3, 9, 1);
		screen.inventoryGrid("hotbar", 8, packY + 3 * 18 + 4, 1, 9, 28);
		FuelSlot slot = new FuelSlot(cart);
		slots.put(player.getUUID(), slot);
		PandoricalApi.screens().openContainer(player, screen.build(), slot, java.util.Set.of());
	}

	/** Once a tick, server side, after vanilla's own push: the fire, the throttle and the switch. */
	public static void tick(MinecartFurnace cart, boolean hasFuel) {
		Settings s = of(cart);
		if (cart.level() instanceof ServerLevel level) {
			if (cart.push.horizontalDistanceSqr() > 1.0E-6) cart.setAttached(PUSH_DIR, cart.push.normalize());
			boolean out = !(cart instanceof FurnaceFuel fire) || fire.minecartMania$fuel() <= 0;
			if (s.on() && out && feed(level, cart)) hasFuel = true;
			// Switched on with fire in it: the switch had been zeroing the push every tick it
			// was off, and vanilla only ever sets one when the cart is fed by hand, so a cart
			// stopped and started again sat there lit and going nowhere.
			if (s.on() && hasFuel && cart.push.horizontalDistanceSqr() < 1.0E-6) cart.push = pushOf(cart);
			if (level.getGameTime() % WATCH_EVERY == 0 && open.containsValue(cart.getUUID())) watch(level, cart);
		}
		if (!s.on()) {
			cart.push = Vec3.ZERO;
			return;
		}
		if (!hasFuel || cart.push.lengthSqr() < 1.0E-6) return;
		Vec3 push = cart.push.normalize().scale(PUSH_PER_LEVEL * s.level());
		Vec3 motion = cart.getDeltaMovement();
		Vec3 next = motion.add(push);
		double cap = maxSpeedFor(cart);
		if (next.horizontalDistance() > cap) next = next.normalize().scale(cap);
		cart.setDeltaMovement(next);
	}

	/** Notch four is the rail's own cap; each notch below takes a quarter off. */
	public static double maxSpeedFor(MinecartFurnace cart) {
		if (!(cart.level() instanceof ServerLevel level)) return 0.4;
		double rail = justfatlard.minecart_mania.rail.Rails.maxSpeed(level.getBlockState(cart.getCurrentBlockPosOrRailBelow()));
		return rail / 20.0 * of(cart).level() / 4.0;
	}

	/** Fuel burnt per tick beyond vanilla's one: a hotter fire is a shorter one. */
	public static int extraFuelBurn(MinecartFurnace cart) {
		Settings s = of(cart);
		return s.on() ? s.level() - 1 : 0;
	}

	/** An activator rail throws the switch: once per pass, not once per tick on the rail. */
	public static void activate(AbstractMinecart cart, boolean powered) {
		if (!(cart instanceof MinecartFurnace furnace)) return;
		long now = cart.level().getGameTime();
		Long last = activated.get(cart.getUUID());
		activated.put(cart.getUUID(), now);
		if (!powered || (last != null && now - last < 10)) return;
		Settings s = of(furnace);
		set(furnace, new Settings(s.level(), !s.on(), s.reversed()));
	}
}
