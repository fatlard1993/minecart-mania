package justfatlard.minecart_mania.mixin;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every world runs the new minecart physics.
 *
 * <p>Vanilla keeps its rewritten cart movement behind the {@code minecart_improvements} flag,
 * and the old movement caps at eight metres a second no matter what the rail says. Everything
 * here is built on the new one: per-rail speeds, the crossing, the junction. Adding the flag to
 * the world's enabled set is the whole switch, and it is added here rather than by a mixin on
 * the cart because the client is told the world's flags on join and picks its movement code
 * from them: a client and server that disagree about which physics is running would fight over
 * where the cart is.
 */
@Mixin(WorldDataConfiguration.class)
public abstract class MinecartImprovementsMixin {
	@Inject(method = "enabledFeatures", at = @At("RETURN"), cancellable = true)
	private void minecartMania$improvements(CallbackInfoReturnable<FeatureFlagSet> cir) {
		cir.setReturnValue(cir.getReturnValue().join(FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS)));
	}
}
