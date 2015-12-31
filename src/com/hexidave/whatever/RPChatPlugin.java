package com.hexidave.whatever;

import com.flowpowered.math.vector.Vector3d;
import com.google.inject.Inject;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.command.MessageSinkEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.sink.MessageSink;
import org.spongepowered.api.text.sink.MessageSinks;
import org.spongepowered.api.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// The @Plugin decorator fills out the plugin details for Sponge to understand
@Plugin(id = "rp-chat", name = "Roleplay Chat Plugin", version = "1.1")
public class RPChatPlugin {

	@Inject // <--- This decorator signals the Java compiler to set "game" to the current Game instance
	private Game game;


	/**
	 * Sends a message a specific range to players in that range.
	 *
	 * @param sourcePlayer The Player we're sending commands from
	 * @param range        The range the message can be heard in meters (blocks)
	 * @param message      The message to send (formatted)
	 */
	private void sendRangedMessage(Player sourcePlayer, double range, Text message) {

		// Player location
		World sourcePlayerWorld = sourcePlayer.getWorld();
		Vector3d sourcePlayerPosition = sourcePlayer.getLocation().getPosition();

		// We square the range to make the check per player faster
		// Vector3d.range vs rangeSquared: range does a slower square root
		double rangeSquared = range * range;

		// Collection of players we finally send to after checking their distance
		Set<CommandSource> playersToSendChat = new HashSet<CommandSource>();

		// Loop through all the players that are online
		for (Player player : game.getServer().getOnlinePlayers()) {

			// Check if they're in the same world and that their distance (squared) from the sourcePlayer is within rangeSquared
			if (sourcePlayerWorld.equals(player.getWorld()) && player.getLocation().getPosition().distanceSquared(sourcePlayerPosition) <= rangeSquared) {

				// If so, add them to our collection of players this message will be sent to
				playersToSendChat.add(player);
			}
		}

		// MessageSinks are systems that pass messages along in the server
		// In this case, we're sending to a collection of players

		// Create the sink with the collection as a guide...
		MessageSink sink = MessageSinks.to(playersToSendChat);

		// ...send the message
		sink.sendMessage(message);
	}

	/**
	 * Handles color and formatting for the message to be sent
	 *
	 * @param sourcePlayer          Player that's sending the message
	 * @param isPlayerNameFirst     Is the player name at the front of the out-going message (e.g. <PlayerName> message)?
	 * @param playerNameFormat      String format when assigning Player.getName()
	 * @param message               The message being sent
	 * @param messageFormat         The String format when assigning the message
	 * @param messageColor          The TextColor for the message
	 * @param isPlayerNameColorSame Is the color for the player name the same as the message color?
	 * @return The finalized message with colors
	 */
	private static Text getMessageText(
			Player sourcePlayer,
			boolean isPlayerNameFirst,
			String playerNameFormat,
			String message,
			String messageFormat,
			TextColor messageColor,
			boolean isPlayerNameColorSame) {

		// Create the formatted player name string
		String playerName = String.format(playerNameFormat, sourcePlayer.getName());

		// Add color to the player name and assign it to a Text
		Text playerNameFinal = Texts.of(playerName).builder().color(isPlayerNameColorSame ? messageColor : TextColors.WHITE).build();

		// Format the message and assign it to a Text
		Text messageText = Texts.of(String.format(messageFormat, message));

		// Color the message text
		Text messageFinal = messageText.builder().color(messageColor).build();

		// Swap positions for the message text and player text if needed
		// Text.append(Text) will keep color sections together
		Text finalText;
		if (isPlayerNameFirst) {
			finalText = playerNameFinal.builder().append(messageFinal).build();
		} else {
			finalText = messageFinal.builder().append(playerNameFinal).build();
		}

		// Return the final assembled message with colors
		return finalText;
	}


	/**
	 * This creates and registers a new command (with aliases) to the server.
	 *
	 * @param commandAliases        A List of command aliases (e.g. "o", "ooc" work for the same /ooc command)
	 * @param minBlockRange         The shortest distance accepted by this command to be heard
	 * @param maxBlockRange         The longest distance accepted by this command to be heard; values < 0 are treated as infinite range
	 * @param firstArgumentIsRange  Is the first argument to the command an integer for the range (within the min/maxBlockRange above)?
	 * @param commandDescription    The description given for the command in /help
	 * @param isPlayerNameFirst     Is the player's name inserted first in the message when sent? (e.g. <PlayerName> message vs. message <PlayerName>)
	 * @param playerNameFormat      The String format for the player name segment when inserting Player.getName()
	 * @param messageFormat         The String format for the message segment
	 * @param messageColor          The TextColor for the message segment
	 * @param isPlayerNameColorSame Is the TextColor for the player name segment the same as the message color?
	 */
	private void registerChatCommandWithFormat(
			final List<String> commandAliases,
			final int minBlockRange,
			final int maxBlockRange,
			final boolean firstArgumentIsRange,
			final String commandDescription,
			final boolean isPlayerNameFirst,
			final String playerNameFormat,
			final String messageFormat,
			final TextColor messageColor,
			final boolean isPlayerNameColorSame) {

		// We start building the command spec, but break the builder section in half
		// to adjust the "arguments" section based on 'firstArgumentIsRange'
		CommandSpec.Builder builder = CommandSpec.builder() // Returns the helper object to build
				.description(Texts.of(commandDescription)); // Adds the command description to the command spec

		// If 'firstArgumentIsRange', then we parse that first, otherwise we skip looking for it
		if (firstArgumentIsRange) {

			// Keep using 'builder' to add components to the command spec
			builder = builder.arguments(
					GenericArguments.onlyOne(GenericArguments.integer(Texts.of("range"))),  // Parse the range
					GenericArguments.remainingJoinedStrings(Texts.of("message"))            // Parse the message text
			);
		} else {

			// Add the simple version of message parsing
			builder = builder.arguments(
					GenericArguments.remainingJoinedStrings(Texts.of("message"))            // Parse just the message
			);
		}

		// This returns the last part of the builder to a new CommandSpec (i.e. see 'build()' at the end)
		CommandSpec cmdSpec = builder
				// Add a CommandExecutor as an anonymous class
				.executor(new CommandExecutor() {

					          // Override the 'execute' function in the base CommandExecutor class,
					          // which is where the main logic happens for the command
					          @Override // <--- This is a "decorator"; it's just a piece of logic the compiler follows
					          public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {

						          // Pull the message string from the arguments
						          String message = args.<String>getOne("message").get();

						          // Start the range at the default of max range
						          int range = maxBlockRange;

						          // If we're parsing the first argument as a range in blocks, do that here
						          if (firstArgumentIsRange) {

							          // Parse the range parameter
							          range = args.<Integer>getOne("range").get();
						          }

						          // Check if the maxBlockRange argument is to be counted as a directive for infinite range
						          boolean isRangeInfinite = maxBlockRange < 0;

						          // If the range isn't supposed to be infinite, check the bounds of the argument
						          if (!isRangeInfinite) {
							          if (range < minBlockRange || range > maxBlockRange) {

								          // If we're outside the min/max range, throw an error
								          String rangeErrorMessage = String.format("Chat command outside allowed range [%d, %d]!", minBlockRange, maxBlockRange);
								          throw new CommandException(Texts.of(rangeErrorMessage));
							          }
						          }

						          // If the source is somehow not a player, bail
						          if (!(src instanceof Player)) {
							          throw new CommandException(Texts.of("Cannot use chat location commandAliases without being a player!"));
						          }

						          // Start using the source of this command as a Player type by casting it
						          Player sourcePlayer = (Player) src;

						          // Format and color the message to be sent out
						          Text finalText = getMessageText(sourcePlayer, isPlayerNameFirst, playerNameFormat, message, messageFormat, messageColor, isPlayerNameColorSame);

						          // Send the message...
						          if (!isRangeInfinite) {
							          // ... a specific range
							          sendRangedMessage(sourcePlayer, (double) range, finalText);
						          } else {
							          // ... to everyone; infinite distance
							          game.getServer().getBroadcastSink().sendMessage(finalText);
						          }

						          // Command succeeded, let the processor know
						          return CommandResult.success();
					          } // End of the 'execute' function
				          } // End of the CommandExecutor anonymous class
				).build(); // <--- *** This finalizes the 'builder' and returns a new CommandSpec above ***

		// Register the new command to the server
		game.getCommandManager().register(this, cmdSpec, commandAliases);
	}

	/**
	 * This is a short-hand version of registerChatCommandWithFormat with some defaults:
	 * playerNameFormat:        "<%s> "
	 * isPlayerNameFirst:       true
	 * messageFormat:           "%s"
	 * isPlayerNameColorSame:   false
	 *
	 * @param commandAliases       A List of command aliases (e.g. "o", "ooc" work for the same /ooc command)
	 * @param minBlockRange        The shortest distance accepted by this command to be heard
	 * @param maxBlockRange        The longest distance accepted by this command to be heard; values < 0 are treated as infinite range
	 * @param firstArgumentIsRange Is the first argument to the command an integer for the range (within the min/maxBlockRange above)?
	 * @param commandDescription   The description given for the command in /help
	 * @param messageColor         The TextColor for the message segment
	 */
	private void registerChatCommand(
			final List<String> commandAliases,
			final int minBlockRange,
			final int maxBlockRange,
			final boolean firstArgumentIsRange,
			final String commandDescription,
			final TextColor messageColor) {

		// Call the original version of the function, filling in the blanks
		registerChatCommandWithFormat(commandAliases, minBlockRange, maxBlockRange, firstArgumentIsRange, commandDescription, true, "<%s> ", "%s", messageColor, false);
	}

	/**
	 * Registers the "/pm" command with the server
	 */
	private void registerPrivateMessageCommand() {
		CommandSpec cmdSpec = CommandSpec.builder()
				.description(Texts.of("Sends a private message to another player."))
				.arguments(
						GenericArguments.onlyOne(GenericArguments.string(Texts.of("player"))),  // Player target (optionally partial name)
						GenericArguments.remainingJoinedStrings(Texts.of("message"))            // Message to player
				)
				.executor(new CommandExecutor() {
					@Override
					public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {

						String targetPlayer = args.<String>getOne("player").get().toLowerCase();
						String message = args.<String>getOne("message").get();

						// Find all the players that match [targetPlayer] that doesn't include you
						Set<Player> matchingPlayers = new HashSet<>();
						for (Player player : game.getServer().getOnlinePlayers()) {
							// Don't PM yourself
							if (player != src && player.getName().toLowerCase().startsWith(targetPlayer)) {
								matchingPlayers.add(player);
							}
						}

						// Check for command errors
						if (matchingPlayers.size() == 0) {

							// No matching targets
							String errorMsg = String.format("No players match your target [%s]", targetPlayer);
							throw new CommandException(Texts.of(errorMsg));
						} else if (matchingPlayers.size() > 1) {

							// Too many possible targets
							String matchingPlayerNames = String.join("\n", matchingPlayers.stream().map(Player::getName).collect(Collectors.toList()));
							String errorMsg = String.format("More than one match for [%s]:\n%s", targetPlayer, matchingPlayerNames);
							throw new CommandException(Texts.of(errorMsg));
						}

						// Get the receiving player
						Player receivingPlayer = matchingPlayers.iterator().next();
						String receivingPlayerName = receivingPlayer.getName();


						// Handle the name of the sender in case of player or admin message/other
						String senderName;

						if (src instanceof Player) {
							Player sourcePlayer = (Player)src;
							senderName = sourcePlayer.getName();
						} else {
							senderName = "An unknown voice";
						}

						// Format the strings
						String messageToTarget = String.format("%s tells you: %s", senderName, message);
						String messageToSender = String.format("You tell %s: %s", receivingPlayerName, message);

						// Color the text
						Text messageToTargetText = Texts.of(messageToTarget).builder().color(TextColors.YELLOW).build();
						Text messageToSenderText = Texts.of(messageToSender).builder().color(TextColors.YELLOW).build();

						// Send the messages
						src.sendMessage(messageToSenderText);
						receivingPlayer.sendMessage(messageToTargetText);

						return CommandResult.success();
					}
				}).build();

		// Register the command
		game.getCommandManager().register(this, cmdSpec, "pm");
	}

	// This event listener happens while the server is setting up
	@Listener // <--- This decorator tells the Java compiler to assign listener functions to the server events
	public void onServerInitializing(GameInitializationEvent event) {

		// Register all the different commands

		// Local OOC
		registerChatCommandWithFormat(Arrays.asList("b"), 20, 20, false, "Local OOC [20 blocks]", true, "<%s> ", "((%s))", TextColors.DARK_GRAY, false);

		// Global OOC
		registerChatCommandWithFormat(Arrays.asList("o", "ooc"), -1, -1, false, "Global OOC", true, "<%s> ", "((%s))", TextColors.WHITE, false);

		// Whisper
		registerChatCommand(Arrays.asList("w", "whisper"), 2, 2, false, "Whisper [2 blocks]", TextColors.GOLD);

		// Shout
		registerChatCommand(Arrays.asList("s", "shout"), 50, 50, false, "Shout [50 blocks]", TextColors.RED);

		// Me (default range)
		registerChatCommandWithFormat(Arrays.asList("me"), 15, 15, false, "Personal action [15 blocks]", true, "%s ", "%s", TextColors.DARK_PURPLE, true);

		// Me (custom range)
		registerChatCommandWithFormat(Arrays.asList("mer"), 5, 25, true, "Personal action [5-25 blocks]", true, "%s ", "%s", TextColors.DARK_PURPLE, true);

		// Do (default range)
		registerChatCommandWithFormat(Arrays.asList("do"), 15, 15, false, "Do action [15 blocks]", false, "<%s>", "%s ", TextColors.DARK_AQUA, true);

		// Do (custom range)
		registerChatCommandWithFormat(Arrays.asList("dor"), 5, 500, true, "Do action [5-500 blocks]", false, "<%s>", "%s ", TextColors.DARK_AQUA, true);

		// Private message
		registerPrivateMessageCommand();
	}

	// This event listener happens when a regular chat message occurs
	@Listener
	public void onChat(MessageSinkEvent.Chat event) {

		Object rootCause = event.getCause().root();

		// Ignore non-player chat messages
		if (!(rootCause instanceof Player)) {
			return;
		}

		// Get the player that sent the chat message
		Player playerEnt = (Player) event.getCause().root();

		// Prevent the server from passing the message normally
		event.setCancelled(true);

		// Format the text for a default local chat message
		Text message = getMessageText(playerEnt, true, "<%s> ", Texts.toPlain(event.getRawMessage()), "%s", TextColors.GRAY, false);

		// Send it only to players within 10 blocks (meters)
		sendRangedMessage(playerEnt, 10.0, message);
	}
}