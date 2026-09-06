package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.TntTunnel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The TNT cart remembers how it was going when it was lit, tips its nose down over the fuse,
 * and bores that way when it goes.
 *
 * <p>The heading and the speed are taken at priming rather than at the blast, because a primed
 * cart keeps rolling and may be stopped against the face by then, with nothing to say which way
 * it came or how fast.
 *
 * <p>A lit cart stops where it was lit and sets its charge there. It used to roll on burning,
 * so the tip happened somewhere down the line from the activator rail, and a fast cart met
 * the face before the fuse was through and went off flat against it with no tip at all. Held
 * on the rail, the fuse runs its course and the bore starts from the rail. The cart is lifted
 * as it tips, since the tip turns it about its middle and the nose went through the floor.
 *
 * <p>The tip is put on after the movement has had its say each tick. The new movement writes
 * the cart's pitch from the rail only when the cart moves, and tells clients about it only in
 * the steps it sends them; a cart lit standing still writes nothing and sends nothing, so a tip
 * hung on that write was never seen. Here the tip goes over whatever pitch the rail gave, into
 * the steps already going out, and into a step of its own when there were none.
 */
@Mixin(MinecartTNT.class)
public abstract class MinecartTntMixin {
	@Unique
	private static final float TIP_DEGREES = 55.0F;
	@Unique
	private static final float FULL_FUSE = 80.0F;
	/**
	 * Ticks the tip takes, lit from a standstill and lit at the rail's full pace: a nod that
	 * lands well inside the fuse, and lands sooner the harder the cart came in, the way a
	 * charge slams into a face. It used to take the whole fuse, and read as a slow lean.
	 */
	@Unique
	private static final float TIP_TICKS_STILL = 24.0F;
	@Unique
	private static final float TIP_TICKS_FAST = 10.0F;

	@Unique
	private Direction minecartMania$heading;
	/** How much of its rail's pace the cart had when lit; the bore is that much of its full length. */
	@Unique
	private double minecartMania$share = -1.0;
	/** The pitch the rail gave, before the tip went on it. */
	@Unique
	private float minecartMania$railPitch;
	@Unique
	private int minecartMania$stepsBefore;
	/** Where the cart was lit, and is held. */
	@Unique
	private Vec3 minecartMania$anchor;
	/** Half a cart's length: how far the nose would drop, turned about the middle. */
	@Unique
	private static final double HALF_LENGTH = 0.5;

	@Inject(method = "primeFuse", at = @At("HEAD"))
	private void minecartMania$rememberHeading(DamageSource source, CallbackInfo ci) {
		MinecartTNT cart = (MinecartTNT) (Object) this;
		if (minecartMania$heading == null) minecartMania$heading = justfatlard.minecart_mania.cart.Headings.of(cart);
		if (minecartMania$share < 0.0 && cart.level() instanceof ServerLevel level) {
			double cap = justfatlard.minecart_mania.rail.Rails.maxSpeed(level.getBlockState(cart.getCurrentBlockPosOrRailBelow())) / 20.0;
			minecartMania$share = cap <= 0.0 ? 0.0 : cart.getDeltaMovement().horizontalDistance() / cap;
		}
		if (minecartMania$anchor == null && !cart.level().isClientSide()) {
			minecartMania$anchor = cart.position();
			cart.setDeltaMovement(Vec3.ZERO);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void minecartMania$beforeMoving(CallbackInfo ci) {
		MinecartTNT cart = (MinecartTNT) (Object) this;
		if (cart.getBehavior() instanceof NewMinecartBehavior behavior) minecartMania$stepsBefore = behavior.lerpSteps.size();
		if (minecartMania$anchor != null && !cart.level().isClientSide()) cart.setDeltaMovement(Vec3.ZERO);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minecartMania$tip(CallbackInfo ci) {
		MinecartTNT cart = (MinecartTNT) (Object) this;
		if (cart.level().isClientSide() || !cart.isPrimed() || cart.isRemoved()) return;
		if (!(cart.getBehavior() instanceof NewMinecartBehavior behavior)) return;

		java.util.List<NewMinecartBehavior.MinecartStep> steps = behavior.lerpSteps;
		boolean moved = steps.size() > minecartMania$stepsBefore;
		if (moved) minecartMania$railPitch = cart.getXRot();

		float share = (float) Math.clamp(minecartMania$share < 0.0 ? 1.0 : minecartMania$share, 0.0, 1.0);
		float tipTicks = TIP_TICKS_STILL + (TIP_TICKS_FAST - TIP_TICKS_STILL) * share;
		float since = FULL_FUSE - Math.min(cart.getFuse(), FULL_FUSE);
		float part = Math.clamp(since / tipTicks, 0.0F, 1.0F);
		// Eased out: quick off the mark, settling at the end, rather than a steady lean.
		float burnt = 1.0F - (1.0F - part) * (1.0F - part);
		// Nose down is the other way from climbing, and a flipped cart's pitch runs backwards.
		float tip = -TIP_DEGREES * burnt * (cart.isFlipped() ? -1.0F : 1.0F);
		float pitch = minecartMania$railPitch + tip;

		if (minecartMania$anchor != null) {
			double lift = HALF_LENGTH * Math.sin(Math.toRadians(TIP_DEGREES * burnt));
			cart.setPos(minecartMania$anchor.add(0.0, lift, 0.0));
			cart.setDeltaMovement(Vec3.ZERO);
		}
		if (moved) {
			for (int i = minecartMania$stepsBefore; i < steps.size(); i++) {
				NewMinecartBehavior.MinecartStep step = steps.get(i);
				steps.set(i, new NewMinecartBehavior.MinecartStep(step.position(), step.movement(), step.yRot(), step.xRot() + tip, step.weight()));
			}
		} else {
			steps.add(new NewMinecartBehavior.MinecartStep(cart.position(), cart.getDeltaMovement(), cart.getYRot(), pitch, 1.0F));
		}
		cart.setXRot(pitch);
	}

	/**
	 * A TNT cart does not go off for hitting something at speed.
	 *
	 * <p>Vanilla blows it up the moment it meets a wall faster than a tenth of a block a tick,
	 * which made every fast track a fuse: a cart in a train, a cart on a chain, a cart that
	 * came round a bend into a fence post. Here it goes off when it is lit - the activator
	 * rail, or fire - and not otherwise, so a charge is a thing you set, not a thing that
	 * happens to you. The read of the collision flag in the cart's tick is the one place that
	 * rule lived, and it is answered "no" there.
	 */
	@Redirect(method = "tick", at = @At(value = "FIELD",
		target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartTNT;horizontalCollision:Z"))
	private boolean minecartMania$noCrashExplosion(MinecartTNT cart) {
		return false;
	}

	@Inject(method = "explode(Lnet/minecraft/world/damagesource/DamageSource;D)V", at = @At("HEAD"), cancellable = true)
	private void minecartMania$bore(DamageSource source, double speed, CallbackInfo ci) {
		MinecartTNT cart = (MinecartTNT) (Object) this;
		if (cart.level().isClientSide()) return;
		Direction heading = minecartMania$heading != null ? minecartMania$heading : justfatlard.minecart_mania.cart.Headings.of(cart);
		if (heading.getAxis().isVertical()) heading = Direction.NORTH;
		TntTunnel.bore(cart, heading, minecartMania$share < 0.0 ? 1.0 : minecartMania$share);
		ci.cancel();
	}

	public Direction minecartMania$heading() {
		return minecartMania$heading;
	}
}
