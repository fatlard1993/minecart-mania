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
public abstract class MinecartTntMixin implements justfatlard.minecart_mania.cart.TntVolley.TntLit {
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

	@Override
	public void minecartMania$lightAs(Direction heading, double share) {
		minecartMania$heading = heading;
		minecartMania$share = share;
	}

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
			// The carts chained to this one come too, if it was going fast enough to bring them.
			justfatlard.minecart_mania.cart.TntVolley.onPrimed(cart, source, minecartMania$heading, minecartMania$share);
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

		// Faced before the nod is worked out: the nod's sign reads the flipped flag, and facing is
		// what settles it.
		if (minecartMania$anchor != null && minecartMania$heading != null) minecartMania$face(cart, minecartMania$heading);

		java.util.List<NewMinecartBehavior.MinecartStep> steps = behavior.lerpSteps;
		boolean moved = steps.size() > minecartMania$stepsBefore;
		if (moved) minecartMania$railPitch = cart.getXRot();

		// A volley cart nods at the fast end whatever its pace, and only once it has landed.
		boolean volley = justfatlard.minecart_mania.cart.TntVolley.isMember(cart);
		float share = volley ? 1.0F : (float) Math.clamp(minecartMania$share < 0.0 ? 1.0 : minecartMania$share, 0.0, 1.0);
		float tipTicks = TIP_TICKS_STILL + (TIP_TICKS_FAST - TIP_TICKS_STILL) * share;
		float since = FULL_FUSE - Math.min(cart.getFuse(), FULL_FUSE) - justfatlard.minecart_mania.cart.TntVolley.tipDelay(cart);
		float part = Math.clamp(since / tipTicks, 0.0F, 1.0F);
		// Eased out: quick off the mark, settling at the end, rather than a steady lean.
		float burnt = 1.0F - (1.0F - part) * (1.0F - part);
		// Nose down is the other way from climbing, and a flipped cart's pitch runs backwards.
		float tip = -TIP_DEGREES * burnt * (cart.isFlipped() ? -1.0F : 1.0F);
		float pitch = minecartMania$railPitch + tip;

		if (minecartMania$anchor != null) {
			Vec3 resting = justfatlard.minecart_mania.cart.TntVolley.restingAt(cart, cart.level().getGameTime());
			if (resting != null) minecartMania$anchor = resting;
			double lift = HALF_LENGTH * Math.sin(Math.toRadians(TIP_DEGREES * burnt));
			cart.setPos(minecartMania$anchor.add(0.0, lift, 0.0));
			cart.setDeltaMovement(Vec3.ZERO);
		}
		if (moved) {
			for (int i = minecartMania$stepsBefore; i < steps.size(); i++) {
				NewMinecartBehavior.MinecartStep step = steps.get(i);
				float yaw = minecartMania$anchor != null ? cart.getYRot() : step.yRot();
				steps.set(i, new NewMinecartBehavior.MinecartStep(step.position(), step.movement(), yaw, step.xRot() + tip, step.weight()));
			}
		} else {
			steps.add(new NewMinecartBehavior.MinecartStep(cart.position(), cart.getDeltaMovement(), cart.getYRot(), pitch, 1.0F));
		}
		cart.setXRot(pitch);
	}

	/**
	 * Lies along the heading, nose to the face, the same for every cart in the charge.
	 *
	 * <p>Set outright, facing and flipped flag both, every tick. Off the rail a flung cart was
	 * turned by whatever its last motion happened to be, so a volley came to rest every which
	 * way; and the first answer to that turned a cart that was half a circle out by toggling
	 * its flipped flag instead, which left it still half a circle out, so it toggled back the
	 * next tick, and the next. The client eased each of those half-turns, so the cart spent
	 * the fuse spinning through sideways with its nod swapping ends. Nothing about a cart held
	 * at its spot needs remembering from one tick to the next.
	 */
	@Unique
	private static void minecartMania$face(MinecartTNT cart, Direction heading) {
		Vec3 along = Vec3.atLowerCornerOf(heading.getUnitVec3i());
		cart.setFlipped(false);
		cart.setYRot(net.minecraft.util.Mth.wrapDegrees(180.0F - (float) (Math.atan2(along.z, along.x) * 180.0 / Math.PI)));
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
		// A follower goes off on the lead's fuse and is spent by the lead's bore.
		if (justfatlard.minecart_mania.cart.TntVolley.isFollower(cart)) {
			ci.cancel();
			return;
		}
		Direction heading = minecartMania$heading != null ? minecartMania$heading : justfatlard.minecart_mania.cart.Headings.of(cart);
		if (heading.getAxis().isVertical()) heading = Direction.NORTH;
		int carts = justfatlard.minecart_mania.cart.TntVolley.sizeOf(cart);
		// The volley is pooled first, while the chains between its carts are still there to be
		// counted: the lead's own removal parts its chains, and parted the other way they fall as
		// chains rather than going into the pool.
		justfatlard.minecart_mania.cart.TntVolley.spend(cart);
		TntTunnel.bore(cart, heading, minecartMania$share < 0.0 ? 1.0 : minecartMania$share, carts);
		ci.cancel();
	}

	public Direction minecartMania$heading() {
		return minecartMania$heading;
	}
}
