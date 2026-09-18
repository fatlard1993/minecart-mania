package justfatlard.minecart_mania.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A TNT cart blows a tunnel, not a crater.
 *
 * <p>Vanilla's TNT cart is a sphere of damage that takes the track with it. This one puts the
 * whole charge forward: a bore three wide, three high and sixteen long, from the rail it sits
 * on, floor kept. Every block comes out whole - a mining charge that vaporised the ore would be
 * a strange tool - and nothing standing near it is hurt. The cart is spent: what is left of it
 * is a few iron nuggets on the floor of the bore.
 *
 * <p>How far the bore goes is the speed the cart was lit at: the full sixteen at the pace of
 * the rail it was on, and two blocks from a cart lit at a standstill. A charge is only as good
 * as the run-up behind it.
 */
public final class TntTunnel {
	private TntTunnel() {}

	public static final int LENGTH = 16;
	/** What a charge set from a standstill still takes: a pocket two deep, the full three by three. */
	public static final int LEAST = 2;
	private static final int HALF_WIDTH = 1;
	private static final int HEIGHT = 3;
	/** Anything that a pickaxe would never take: bedrock, the world border, end portal frames. */
	private static final float UNBREAKABLE = -1.0F;

	/** Nuggets left of the cart, at least and at most. A cart is five ingots, forty-five nuggets; most of it is gone. */
	public static final int NUGGETS_LEAST = 8;
	public static final int NUGGETS_MOST = 14;

	/**
	 * @param share how much of the rail's full pace the cart had when it was lit, nought to one
	 */
	public static void bore(MinecartTNT cart, Direction heading, double share) {
		bore(cart, heading, share, 1);
	}

	/** The widest a volley bores; past this every extra cart goes into depth instead. */
	private static final int WIDEST = 6;
	private static final int DEPTH_PER_CART = 4;
	private static final int DEPTH_PER_CART_PAST_WIDEST = 8;

	/**
	 * How wide a charge of this many carts bores: three for one, a block more for each more,
	 * to six. The bore is as high as it is wide, from the rail up - the floor is never touched.
	 */
	public static int widthOf(int carts) {
		return Math.min(WIDEST, 2 + Math.max(1, carts));
	}

	/** How deep, at the rail's full pace: sixteen for one, four more per cart, eight more once the bore is as wide as it gets. */
	public static int depthOf(int carts) {
		int extra = Math.max(0, carts - 1);
		int widening = Math.min(extra, WIDEST - 3);
		return LENGTH + widening * DEPTH_PER_CART + (extra - widening) * DEPTH_PER_CART_PAST_WIDEST;
	}

	/**
	 * @param share how much of the rail's full pace the cart had when it was lit, nought to one
	 * @param carts how many carts make up the charge
	 */
	public static void bore(MinecartTNT cart, Direction heading, double share, int carts) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		BlockPos origin = cart.getCurrentBlockPosOrRailBelow();
		Direction side = heading.getClockWise();
		int width = widthOf(carts);
		int height = width;
		// An even width has no middle: the extra column goes to the right of the rail.
		int left = -((width - 1) / 2);
		int right = width / 2;
		int length = Math.max(LEAST, (int) Math.round(depthOf(carts) * Math.clamp(share, 0.0, 1.0)));
		int cleared = 0;
		for (int ahead = 1; ahead <= length; ahead++) {
			for (int across = left; across <= right; across++) {
				for (int up = 0; up < height; up++) {
					BlockPos pos = origin.relative(heading, ahead).relative(side, across).above(up);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.getDestroySpeed(level, pos) == UNBREAKABLE) continue;
					level.destroyBlock(pos, true, cart, 512);
					cleared++;
				}
			}
			if (ahead % 4 == 0) {
				BlockPos at = origin.relative(heading, ahead).above();
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, origin, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.8F);
		justfatlard.minecart_mania.Awards.bored(level, origin, cleared);
		// A volley's leavings are pooled and dropped by the volley; a cart alone drops its own.
		if (carts <= 1) spend(cart);
		else cart.discard();
	}

	/** A cart that has gone off: what is left of it is a handful of iron nuggets on the floor. */
	public static void spend(MinecartTNT cart) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		scatter(level, cart.position(), Items.IRON_NUGGET, nuggetsOf(level));
		cart.discard();
	}

	/** One cart's worth of nuggets. */
	public static int nuggetsOf(ServerLevel level) {
		return NUGGETS_LEAST + level.getRandom().nextInt(NUGGETS_MOST - NUGGETS_LEAST + 1);
	}

	/** So many of this, thrown loose about a spot. */
	public static void scatter(ServerLevel level, Vec3 at, Item item, int count) {
		for (int i = 0; i < count; i++) {
			ItemEntity drop = new ItemEntity(level, at.x, at.y + 0.3, at.z, new ItemStack(item));
			drop.setDeltaMovement(
				(level.getRandom().nextDouble() - 0.5) * 0.4,
				0.2 + level.getRandom().nextDouble() * 0.25,
				(level.getRandom().nextDouble() - 0.5) * 0.4);
			level.addFreshEntity(drop);
		}
	}
}
