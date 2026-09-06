package justfatlard.minecart_mania.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
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

	/** Nuggets left of the cart, at least and at most. A cart is five ingots; most of it is gone. */
	private static final int NUGGETS_LEAST = 3;
	private static final int NUGGETS_MOST = 7;

	/**
	 * @param share how much of the rail's full pace the cart had when it was lit, nought to one
	 */
	public static void bore(MinecartTNT cart, Direction heading, double share) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		BlockPos origin = cart.getCurrentBlockPosOrRailBelow();
		Direction side = heading.getClockWise();
		int length = Math.max(LEAST, (int) Math.round(LENGTH * Math.clamp(share, 0.0, 1.0)));
		int cleared = 0;
		for (int ahead = 1; ahead <= length; ahead++) {
			for (int across = -HALF_WIDTH; across <= HALF_WIDTH; across++) {
				for (int up = 0; up < HEIGHT; up++) {
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
		int nuggets = NUGGETS_LEAST + level.getRandom().nextInt(NUGGETS_MOST - NUGGETS_LEAST + 1);
		for (int i = 0; i < nuggets; i++) {
			ItemEntity nugget = new ItemEntity(level, cart.getX(), cart.getY() + 0.3, cart.getZ(), new ItemStack(Items.IRON_NUGGET));
			nugget.setDeltaMovement(
				(level.getRandom().nextDouble() - 0.5) * 0.3,
				0.2 + level.getRandom().nextDouble() * 0.2,
				(level.getRandom().nextDouble() - 0.5) * 0.3);
			level.addFreshEntity(nugget);
		}
		cart.discard();
	}
}
