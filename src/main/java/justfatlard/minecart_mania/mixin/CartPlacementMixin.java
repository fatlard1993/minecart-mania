package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.Headings;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cart set down by a player starts out facing the way they do, along the rail. */
@Mixin(AbstractMinecart.class)
public abstract class CartPlacementMixin {
	@Inject(method = "createMinecart", at = @At("RETURN"))
	private static <T extends AbstractMinecart> void minecartMania$faceAsPlaced(Level level, double x, double y, double z,
			EntityType<T> type, EntitySpawnReason reason, ItemStack stack, Player player, CallbackInfoReturnable<T> cir) {
		T cart = cir.getReturnValue();
		if (cart == null || player == null || level.isClientSide()) return;
		Headings.place(cart, player);
	}
}
