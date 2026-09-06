package justfatlard.minecart_mania.cart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import justfatlard.pandorical.protocol.ComponentUpdate;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Carts that work as they roll: one that lays what it carries, one that throws it.
 *
 * <p>Both are a chest cart wearing a different block, with a note on the entity saying what
 * it is for. A vanilla client renders a chest cart with a dropper in it without knowing
 * anything, and the twenty-seven slots are the hopper of things to lay or throw.
 *
 * <p>The dropper cart places the first block it carries every so many blocks of travel, to
 * its front, back, left or right. Forward is the interesting one: a rail laid one block ahead
 * of a moving cart is a track that builds itself. The dispenser cart throws every so many
 * blocks the same way, and what it throws best is TNT, lobbed ahead and up so it bursts
 * against the face, sparing the floor it runs on and everything standing near it. See
 * {@code LobbedTnt}. It used to throw by the clock, so a cart stalled against a face would keep
 * throwing; but a cart that throws whether or not it moves empties itself into one wall, and
 * the boring machine is a dropper cart behind it laying rail, which needs it to move.
 */
public final class WorkingCarts {
	private WorkingCarts() {}

	public enum Kind implements StringRepresentable {
		DROPPER, DISPENSER;

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}
	}

	public enum Side implements StringRepresentable {
		FRONT, BACK, LEFT, RIGHT;

		public static final Codec<Side> CODEC = StringRepresentable.fromEnum(Side::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}

		public Direction of(Direction heading) {
			return switch (this) {
				case FRONT -> heading;
				case BACK -> heading.getOpposite();
				case LEFT -> heading.getCounterClockWise();
				case RIGHT -> heading.getClockWise();
			};
		}
	}

	/** What the cart is for, which way it faces its work, and how often, in blocks of travel. */
	public record Work(Kind kind, Side side, int interval) {
		public static final Codec<Work> CODEC = RecordCodecBuilder.create(i -> i.group(
			Kind.CODEC.fieldOf("kind").forGetter(Work::kind),
			Side.CODEC.fieldOf("side").forGetter(Work::side),
			Codec.INT.fieldOf("interval").forGetter(Work::interval)
		).apply(i, Work::new));
	}

	private static final AttachmentType<Work> WORK = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "work"), Work.CODEC);

	public static final String SCREEN = Main.MOD_ID + ":working_cart";
	private static final Map<UUID, UUID> open = new ConcurrentHashMap<>();
	/** Distance rolled or ticks waited since the last act; not worth saving. */
	private static final Map<UUID, Double> progress = new ConcurrentHashMap<>();

	public static final String TAG_LOBBED = Main.MOD_ID + ":lobbed";
	public static final String TAG_FLOOR = Main.MOD_ID + ":floor=";
	private static final int LOB_FUSE = 20;

	public static final Map<String, Item> ITEMS = Map.of(
		"dropper_minecart", new WorkingCartItem(Kind.DROPPER, "dropper_minecart"),
		"dispenser_minecart", new WorkingCartItem(Kind.DISPENSER, "dispenser_minecart"));

	public static boolean isCartItem(Item item) {
		return ITEMS.containsValue(item);
	}

	public static Work workOf(AbstractMinecart cart) {
		return cart.getAttached(WORK);
	}

	/** The item this cart came from, or null for a chest cart that is only a chest cart. */
	public static Item itemOf(AbstractMinecart cart) {
		Work work = workOf(cart);
		if (work == null) return null;
		return ITEMS.get(work.kind() == Kind.DROPPER ? "dropper_minecart" : "dispenser_minecart");
	}

	/**
	 * A cart that was given its name as a custom name has it taken back. That was one build's
	 * way of getting the block tip to call it what it is, and the game floats a custom name
	 * over anything under the crosshair; the tip is told the name directly now.
	 */
	private static void unchristen(MinecartChest cart) {
		Item item = itemOf(cart);
		if (item == null || !cart.hasCustomName()) return;
		if (cart.getCustomName().getString().equals(new ItemStack(item).getHoverName().getString())) cart.setCustomName(null);
	}

	/** The block shown in the cart, standing the way a furnace stands in a furnace cart. */
	private static BlockState dressing(Kind kind) {
		return (kind == Kind.DROPPER ? Blocks.DROPPER : Blocks.DISPENSER).defaultBlockState();
	}

	public static void register() {
		var screens = PandoricalApi.screens();
		for (Side side : Side.values()) {
			screens.onAction(SCREEN, "side_" + side.getSerializedName(), (player, data) -> with(player, w -> new Work(w.kind(), side, w.interval())));
		}
		screens.onAction(SCREEN, "less", (player, data) -> with(player, w -> new Work(w.kind(), w.side(), Math.max(1, w.interval() - 1))));
		screens.onAction(SCREEN, "more", (player, data) -> with(player, w -> new Work(w.kind(), w.side(), Math.min(64, w.interval() + 1))));
		screens.onClose(SCREEN, player -> open.remove(player.getUUID()));
	}

	private static void with(ServerPlayer player, java.util.function.UnaryOperator<Work> change) {
		UUID cartId = open.get(player.getUUID());
		if (cartId == null || !(player.level() instanceof ServerLevel level)) return;
		if (!(level.getEntity(cartId) instanceof MinecartChest cart)) return;
		Work work = workOf(cart);
		if (work == null) return;
		cart.setAttached(WORK, change.apply(work));
		refresh(player, cart);
	}

	/**
	 * The controls redrawn in place after a press. Opening the screen again was the easy way,
	 * and it closed the old one first, which put the cursor back in the middle of the screen
	 * on every click.
	 */
	private static void refresh(ServerPlayer player, MinecartChest cart) {
		Work work = workOf(cart);
		if (work == null) return;
		List<ComponentUpdate> updates = new ArrayList<>();
		for (Side side : Side.values()) {
			updates.add(new ComponentUpdate("side_" + side.getSerializedName(),
				Map.of(ComponentType.PROP_STYLE, side == work.side() ? "accepted" : "default")));
		}
		updates.add(new ComponentUpdate("interval", Map.of("text", "Every " + work.interval() + " blocks")));
		PandoricalApi.screens().update(player, SCREEN, updates);
	}

	/** The cart's twenty-seven slots, with the controls above them. */
	public static void openScreen(ServerPlayer player, MinecartChest cart) {
		Work work = workOf(cart);
		if (work == null) return;
		open.put(player.getUUID(), cart.getUUID());

		int width = 176;
		int chestY = 62;
		int packY = chestY + 3 * 18 + 16;
		int height = packY + 3 * 18 + 4 + 18 + 8;
		ScreenBuilder screen = new ScreenBuilder(SCREEN).container(27, true).size(width, height).pauseGame(false);
		screen.panel("bg", 0, 0, width, height, Map.of("border", "beveled"));
		screen.text("title", 8, 6, Map.of("text", work.kind() == Kind.DROPPER ? "Dropper Cart" : "Dispenser Cart", "color", "#404040"));

		int x = 8;
		for (Side side : Side.values()) {
			String name = side.getSerializedName();
			screen.button("side_" + name, x, 18, 38, 16, Map.of(
				ComponentType.PROP_LABEL, Character.toUpperCase(name.charAt(0)) + name.substring(1),
				ComponentType.PROP_STYLE, side == work.side() ? "accepted" : "default"));
			x += 40;
		}
		screen.text("interval", 8, 42, Map.of("text", "Every " + work.interval() + " blocks", "color", "#404040"));
		screen.button("less", 112, 38, 24, 16, Map.of(ComponentType.PROP_LABEL, "-"));
		screen.button("more", 144, 38, 24, 16, Map.of(ComponentType.PROP_LABEL, "+"));

		screen.inventoryGrid("chest", 8, chestY, 3, 9, 0);
		screen.text("pack_label", 8, packY - 12, Map.of("text", "Inventory", "color", "#404040"));
		screen.inventoryGrid("pack", 8, packY, 3, 9, 27);
		screen.inventoryGrid("hotbar", 8, packY + 3 * 18 + 4, 1, 9, 54);
		PandoricalApi.screens().openContainer(player, screen.build(), cart, java.util.Set.of());
	}

	/** Once a tick, server side. */
	public static void tick(AbstractMinecart cart) {
		if (!(cart instanceof MinecartChest chest) || !(cart.level() instanceof ServerLevel level)) return;
		Work work = workOf(chest);
		if (work == null) return;

		unchristen(chest);
		// Earlier carts were dressed with the block stood on end, its face to the sky, which
		// looked like nothing in the game; they are turned round as they are met.
		if (chest.getDisplayBlockState().hasProperty(DispenserBlock.FACING)
				&& chest.getDisplayBlockState().getValue(DispenserBlock.FACING) == Direction.UP) {
			chest.setCustomDisplayBlockState(Optional.of(dressing(work.kind())));
		}

		double done = progress.getOrDefault(cart.getUUID(), 0.0) + cart.getDeltaMovement().horizontalDistance();
		if (done < work.interval()) {
			progress.put(cart.getUUID(), done);
			return;
		}
		progress.put(cart.getUUID(), done - work.interval());
		if (work.kind() == Kind.DROPPER) {
			lay(level, chest, work);
		} else {
			throwOne(level, chest, work);
		}
	}

	private static int firstFilled(MinecartChest chest) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (!chest.getItem(slot).isEmpty()) return slot;
		}
		return -1;
	}

	/** Put down one of the first thing carried, beside the cart on the chosen side. */
	private static void lay(ServerLevel level, MinecartChest chest, Work work) {
		int slot = firstFilled(chest);
		if (slot < 0) return;
		ItemStack stack = chest.getItem(slot);
		Direction heading = Headings.of(chest);
		BlockPos target = chest.getCurrentBlockPosOrRailBelow().relative(work.side().of(heading));

		if (stack.getItem() instanceof BlockItem block) {
			InteractionResult placed = block.place(new DirectionalPlaceContext(level, target, heading, stack, Direction.UP));
			if (placed.consumesAction()) chest.setChanged();
			return;
		}
		if (level.getBlockState(target).isAir()) {
			ItemStack one = stack.split(1);
			level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, one));
			chest.setChanged();
		}
	}

	/**
	 * Throw one of the first thing carried. TNT is lobbed, lit, and marked so its burst spares
	 * the floor and the crew; anything else is tossed the way a dropper drops it.
	 */
	private static void throwOne(ServerLevel level, MinecartChest chest, Work work) {
		int slot = firstFilled(chest);
		if (slot < 0) return;
		ItemStack stack = chest.getItem(slot);
		Direction heading = Headings.of(chest);
		Direction out = work.side().of(heading);
		Vec3 dir = Vec3.atLowerCornerOf(out.getUnitVec3i());
		Vec3 from = chest.position().add(dir.scale(0.8)).add(0, 1.0, 0);
		// The cart's own motion rides along: a throw from a moving cart lands where a moving
		// cart is going, not where it was
		Vec3 velocity = chest.getDeltaMovement().add(dir.scale(0.55)).add(0, 0.45, 0);

		if (stack.is(Items.TNT)) {
			PrimedTnt tnt = new PrimedTnt(level, from.x, from.y, from.z, null);
			tnt.setFuse(LOB_FUSE);
			tnt.setDeltaMovement(velocity);
			tnt.addTag(TAG_LOBBED);
			tnt.addTag(TAG_FLOOR + chest.getCurrentBlockPosOrRailBelow().getY());
			level.addFreshEntity(tnt);
		} else {
			ItemEntity thrown = new ItemEntity(level, from.x, from.y, from.z, stack.copyWithCount(1));
			thrown.setDeltaMovement(velocity);
			level.addFreshEntity(thrown);
		}
		stack.shrink(1);
		chest.setChanged();
	}

	/** The item that puts a working cart on a rail: a chest cart dressed and noted. */
	public static final class WorkingCartItem extends Item {
		private final Kind kind;

		WorkingCartItem(Kind kind, String name) {
			super(new Item.Properties().stacksTo(1)
				.setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Main.id(name))));
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			if (!justfatlard.minecart_mania.rail.Rails.isRail(level.getBlockState(pos))) return InteractionResult.FAIL;
			ItemStack stack = context.getItemInHand();
			if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

			MinecartChest cart = AbstractMinecart.createMinecart(server, pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5,
				EntityTypes.CHEST_MINECART, EntitySpawnReason.SPAWN_ITEM_USE, stack, context.getPlayer());
			cart.setAttached(WORK, new Work(kind, Side.FRONT, kind == Kind.DROPPER ? 1 : 4));
			cart.setCustomDisplayBlockState(Optional.of(dressing(kind)));
			cart.setDisplayOffset(8);
			server.addFreshEntity(cart);
			stack.shrink(1);
			return InteractionResult.SUCCESS;
		}
	}
}
