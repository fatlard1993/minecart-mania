package justfatlard.minecart_mania.cart;

/** What a furnace cart's fire knows, reached through its mixin. */
public interface FurnaceFuel {
	/** Ticks of fire left. */
	int minecartMania$fuel();

	/** More fire, up to the cart's limit; how much was actually taken. */
	int minecartMania$addFuel(int ticks);
}
