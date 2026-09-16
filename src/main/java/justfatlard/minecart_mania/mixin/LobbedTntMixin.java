package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.WorkingCarts;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNT a dispenser cart threw bursts like a mining charge.
 *
 * <p>It carries the level of the rail it was thrown from, and the burst does not touch that
 * level or anything below it: the floor the train runs on stays whole however close the charge
 * lands. Every block it does take comes out as a drop, and no entity is hurt - the crew, the
 * carts and whatever the charge was aimed past are all out of the question.
 */
@Mixin(PrimedTnt.class)
public abstract class LobbedTntMixin {
	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void minecartMania$miningCharge(CallbackInfo ci) {
		PrimedTnt tnt = (PrimedTnt) (Object) this;
		if (!tnt.entityTags().contains(WorkingCarts.TAG_LOBBED) || !(tnt.level() instanceof ServerLevel level)) return;
		int floor = Integer.MIN_VALUE;
		for (String tag : tnt.entityTags()) {
			if (tag.startsWith(WorkingCarts.TAG_FLOOR)) floor = Integer.parseInt(tag.substring(WorkingCarts.TAG_FLOOR.length()));
		}
		final int keepAtOrBelow = floor;
		ExplosionDamageCalculator charge = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
				return pos.getY() > keepAtOrBelow && super.shouldBlockExplode(explosion, reader, pos, state, power);
			}

			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return false;
			}
		};
		level.explode(tnt, Explosion.getDefaultDamageSource(level, tnt), charge, tnt.position(), 4.0F, false, Level.ExplosionInteraction.TNT);
		tnt.discard();
		ci.cancel();
	}
}
