package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.FurnaceControls;
import justfatlard.minecart_mania.cart.FurnaceFuel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The furnace cart's controls, hung on its own methods. */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin implements FurnaceFuel {
	@Shadow private int fuel;
	@Shadow public Vec3 push;
	@Shadow protected abstract boolean hasFuel();

	@Override
	public int minecartMania$fuel() {
		return fuel;
	}

	@Override
	public int minecartMania$addFuel(int ticks) {
		int taken = Math.max(0, Math.min(ticks, FurnaceControls.FUEL_LIMIT - fuel));
		fuel += taken;
		return taken;
	}

	/** An empty hand opens the controls; anything else is vanilla's, which is how fuel goes in. */
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void minecartMania$controls(Player player, InteractionHand hand, Vec3 hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (!player.getItemInHand(hand).isEmpty() || player.isShiftKeyDown()) return;
		if (player instanceof ServerPlayer server) {
			FurnaceControls.openScreen(server, (MinecartFurnace) (Object) this);
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	/**
	 * Anything a furnace burns feeds the cart, for the time it would burn in a furnace. Vanilla
	 * takes coal and nothing else, and a cart that will not take the planks in your hand reads as
	 * a cart with nowhere to put fuel.
	 */
	@Inject(method = "addFuel", at = @At("HEAD"), cancellable = true)
	private void minecartMania$anyFuel(Vec3 interactingPos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(ItemTags.FURNACE_MINECART_FUEL)) return;
		MinecartFurnace cart = (MinecartFurnace) (Object) this;
		if (!(cart.level() instanceof ServerLevel level)) return;
		int worth = FurnaceControls.fuelWorth(level, stack);
		if (worth <= 0 || fuel + worth > FurnaceControls.FUEL_LIMIT) {
			cir.setReturnValue(false);
			return;
		}
		fuel += worth;
		push = cart.position().subtract(interactingPos).horizontal();
		cir.setReturnValue(true);
	}

	/**
	 * Fuel by hand does not turn the cart. Vanilla pushes a fed cart away from whoever fed it,
	 * which was the only way to point one; the arrow over the cart is the way it points now,
	 * and the fire only lights what the arrow says.
	 */
	@Inject(method = "addFuel", at = @At("RETURN"))
	private void minecartMania$keepHeading(Vec3 interactingPos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		MinecartFurnace cart = (MinecartFurnace) (Object) this;
		if (cart.level().isClientSide()) return;
		push = FurnaceControls.pushOf(cart);
	}

	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
	private void minecartMania$throttle(ServerLevel level, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(FurnaceControls.maxSpeedFor((MinecartFurnace) (Object) this));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void minecartMania$afterTick(CallbackInfo ci) {
		MinecartFurnace cart = (MinecartFurnace) (Object) this;
		if (cart.level().isClientSide()) return;
		boolean lit = hasFuel();
		FurnaceControls.tick(cart, lit);
		if (lit && fuel > 0) {
			fuel = Math.max(0, fuel - FurnaceControls.extraFuelBurn(cart));
		}
	}
}
