package justfatlard.minecart_mania.cart;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cart at speed is a cart-sized thing at speed, and whatever stands on the track finds out.
 *
 * <p>Vanilla's push is a nudge that costs the cart as much as the mob. Above walking pace the
 * hit hurts and throws in proportion to the speed, and past a full sprint the cart keeps every
 * bit of its momentum: a laden minecart at thirty metres a second does not notice a zombie.
 */
public final class CartCollisions {
	private CartCollisions() {}

	/** Metres per second. Below this vanilla's nudge is the whole story. */
	private static final double HURTS_FROM = 6.0;
	/** From here on the cart is not slowed by what it hits. */
	private static final double UNSTOPPABLE_FROM = 16.0;
	/** Half a heart per metre a second over the threshold: a copper line kills a zombie in two hits, gold in one. */
	private static final double DAMAGE_PER_MPS = 0.5;

	/** After vanilla has pushed: hurt what was in the way, and take the momentum back if fast enough. */
	public static void afterPush(AbstractMinecart cart, Vec3 before) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		double speed = before.horizontalDistance() * 20.0;
		if (speed < HURTS_FROM) return;

		AABB sweep = cart.getBoundingBox().inflate(0.3, 0.25, 0.3);
		List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, sweep,
			e -> e.getVehicle() != cart && !e.isPassengerOfSameVehicle(cart) && e.isAlive());
		if (struck.isEmpty()) return;

		Vec3 heading = before.horizontal().normalize();
		float damage = (float) ((speed - HURTS_FROM) * DAMAGE_PER_MPS);
		double throwStrength = 0.4 + speed / 16.0;
		for (LivingEntity target : struck) {
			// The entity's own invulnerability window keeps a cart parked against a mob from
			// hitting it every tick; a cart that stays at speed on top of something has passed it.
			target.hurtServer(level, level.damageSources().generic(), damage);
			target.push(heading.x * throwStrength, 0.35, heading.z * throwStrength);
		}
		if (speed >= UNSTOPPABLE_FROM) {
			cart.setDeltaMovement(before);
		}
	}
}
