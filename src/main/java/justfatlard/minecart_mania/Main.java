package justfatlard.minecart_mania;

import java.util.LinkedHashMap;
import java.util.Map;
import justfatlard.minecart_mania.cart.ChainLinks;
import justfatlard.minecart_mania.cart.FurnaceControls;
import justfatlard.minecart_mania.cart.WorkingCarts;
import justfatlard.minecart_mania.cart.Handbrake;
import justfatlard.minecart_mania.rail.RailCycling;
import justfatlard.minecart_mania.rail.Rails;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class Main implements ModInitializer {
	public static final String MOD_ID = "minecart-mania-justfatlard";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	/** Every block this mod adds, by name, with the vanilla block a Pandorical client stands in with. */
	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	private static final Map<String, String> BASES = new LinkedHashMap<>();
	public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// A rail a player can stand on. Floating track is only worth laying if you can walk it,
		// and vanilla's rails have no collision at all: a bridge of rail was a bridge you fell
		// through. Pandorical carries the rule to the client, which is what makes standing on
		// one predict right; without it the server would hold a player up and the client would
		// keep dropping them.
		if (PandoricalApi.isAvailable()) PandoricalApi.content().solidRails();

		block("wooden_rail", Rails.WOODEN_RAIL, "minecraft:rail");
		block("copper_powered_rail", Rails.COPPER_POWERED_RAIL, "minecraft:powered_rail");
		block("cross_rail", Rails.CROSS_RAIL, "minecraft:rail");
		block("tee_rail", Rails.TEE_RAIL, "minecraft:rail");

		for (var entry : BLOCKS.entrySet()) {
			String name = entry.getKey();
			Block block = entry.getValue();
			Registry.register(BuiltInRegistries.BLOCK, id(name), block);
			BlockItem item = new BlockItem(block, new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, id(name))));
			Registry.register(BuiltInRegistries.ITEM, id(name), item);
			// What vanilla's own registration does for a block item and Registry.register does
			// not: without it the block answers asItem() with air and pick-block gives nothing.
			item.registerBlocks(Item.BY_BLOCK, item);
			ITEMS.put(name, item);

			PandoricalApi.content().registerBlock(MOD_ID + ":" + name, new BlockRegistration()
				.baseBlock(BASES.get(name))
				.interactive()
				.model(MOD_ID + ":block/" + name));
			PandoricalApi.content().registerItem(MOD_ID + ":" + name, new ItemRegistration()
				.model(MOD_ID + ":item/" + name));
		}
		// The carts: plain items, each placing a vanilla cart entity with a note on it
		Map<String, Item> carts = new LinkedHashMap<>(WorkingCarts.ITEMS);
		for (var entry : carts.entrySet()) {
			Registry.register(BuiltInRegistries.ITEM, id(entry.getKey()), entry.getValue());
			ITEMS.put(entry.getKey(), entry.getValue());
			PandoricalApi.content().registerItem(MOD_ID + ":" + entry.getKey(), new ItemRegistration()
				.model(MOD_ID + ":item/" + entry.getKey()));
		}
		// The mod's own assets, and the vanilla rail models it redraws in three dimensions.
		PandoricalApi.content().registerModAssets(MOD_ID);

		RailCycling.register();
		Handbrake.register();
		ChainLinks.register();
		FurnaceControls.register();
		WorkingCarts.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("block-tip")) {
			justfatlard.minecart_mania.integration.WorkingCartTips.register();
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> Handbrake.forget(handler.getPlayer().getUUID()));

		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("minecart_mania"));
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.literal("Minecart Mania"))
			.icon(() -> new ItemStack(ITEMS.get("copper_powered_rail")))
			.displayItems((context, entries) -> ITEMS.values().forEach(item -> entries.accept(new ItemStack(item))))
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, tab);

		System.out.println("[" + MOD_ID + "] Loaded");
	}

	private static void block(String name, Block block, String base) {
		BLOCKS.put(name, block);
		BASES.put(name, base);
	}
}
