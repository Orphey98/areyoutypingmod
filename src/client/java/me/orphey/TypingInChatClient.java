package me.orphey;

import me.orphey.mixin.client.ChatAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

public class TypingInChatClient implements ClientModInitializer {
	private boolean chatOpen = false;
	private boolean chatTyping = false;
	private int tickCounter;
	private static final int TICK_DELAY = 80;
	private String chatTextBuf = "";

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		ConfigLoader.load(configDir);

		// Register the reload command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("typinginchatmod")
					.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("reload")
							.executes(context -> {
								// Reload the config
								ConfigLoader.getInstance().reload(FabricLoader.getInstance().getConfigDir());
								context.getSource().sendFeedback(Text.literal("[TypingInChat] Config reloaded (client-side)!"));
								return 1;
							}))
			);
		});

		// Register a tick event to monitor screen changes
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!ConfigLoader.getInstance().isEnableMod()) {
				return;
			}
			if (client.player == null) {
				return;
			}
			Screen currentScreen = MinecraftClient.getInstance().currentScreen;
			if (currentScreen instanceof ChatScreen chatScreen) {
				tickCounter++;
				// If the chat is open and we haven't detected it yet
				if (!chatOpen) {
					chatOpen = true;
                    if (ConfigLoader.getInstance().isDebug()) {
                        client.player.sendMessage(Text.of("Chat GUI opened!"));
                    }
                }
				if (!chatTyping && isTyping(chatScreen) && playersNearby(client)) {
					chatTyping = true;
					ChatPacket.sendPacket((byte) 1);
                    if (ConfigLoader.getInstance().isDebug()) {
                        client.player.sendMessage(Text.of("Player is Typing"));
                    }
                }
				if (tickCounter >= TICK_DELAY) {
					if (chatTyping && !isTyping(chatScreen)) {
						chatTyping = false;
						ChatPacket.sendPacket((byte) 0);
                        if (ConfigLoader.getInstance().isDebug()) {
                            client.player.sendMessage(Text.of("Player stopped typing"));
                        }
                    }
					tickCounter = 0;
				}
			} else {
				if (chatOpen) {
					chatOpen = false;
					chatTyping = false;
					chatTextBuf = "";
					tickCounter = 0;
					ChatPacket.sendPacket((byte) 0);
                    if (ConfigLoader.getInstance().isDebug()) {
                        client.player.sendMessage(Text.of("Chat GUI closed!"));
                    }
                }
			}
		});
	}

	private boolean playersNearby(MinecraftClient client) {
		assert client.player != null;
		List<PlayerEntity> nearbyPlayers = client.player.getWorld().getEntitiesByClass(
				PlayerEntity.class, client.player.getBoundingBox().expand(25), p -> p != client.player);
		return !nearbyPlayers.isEmpty();
	}

	private boolean isTyping(ChatScreen chatScreen) {
		ChatAccessor chatScreenAccessor = (ChatAccessor) chatScreen;
		String chatText = chatScreenAccessor.getChatField().getText(); // Access chat field text
		if (ConfigLoader.getInstance().isIgnoreCommands() && isCommand(chatText)) {
			return false;
		}
		if (chatText != null && !chatText.equals(chatTextBuf)) {
			chatTextBuf = chatText; // Update previous text length
			return true;
		} else {
			return false;
		}

	}

	private boolean isCommand(String chatText) {
		if (chatText == null || chatText.isEmpty()) {
			return false; // Null or empty strings cannot start with "/"
		}
		// Trim leading spaces and check the first character
		String trimmedInput = chatText.trim();
		return !trimmedInput.isEmpty() && trimmedInput.charAt(0) == '/';
	}
}