/*
 * DiscordSRV - A Minecraft to Discord and back link plugin
 * Copyright (C) 2016-2020 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package github.scarsz.discordsrv.commands;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.GamePermissionUtil;
import github.scarsz.discordsrv.util.LangUtil;
import net.dv8tion.jda.api.entities.User;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CommandLink {

    @Command(commandNames = { "link" },
            helpMessage = "Generates a code to link your Minecraft account to your Discord account",
            permission = "discordsrv.link"
    )
    public static void execute(Player sender, String[] args) {
        AccountLinkManager manager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (manager == null) {
            sender.sendMessage(LangUtil.Message.UNABLE_TO_LINK_ACCOUNTS_RIGHT_NOW.toString());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(DiscordSRV.getPlugin(), () -> executeAsync(sender, args, manager));
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    private static void executeAsync(Player sender, String[] args, AccountLinkManager manager) {
        // assume manual link
        if (args.length >= 2) {
            if (!GamePermissionUtil.hasPermission(sender, "discordsrv.link.others")) {
                sender.sendMessage(LangUtil.Message.NO_PERMISSION.toString());
                return;
            }

            List<String> arguments = new ArrayList<>(Arrays.asList(args));
            String minecraft = arguments.remove(0);
            String discord = String.join(" ", arguments);

            OfflinePlayer offlinePlayer = null;

            try {
                offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(minecraft));
            } catch (IllegalArgumentException ignored) {}

            if (offlinePlayer == null) offlinePlayer = Bukkit.getOfflinePlayer(minecraft);
            if (offlinePlayer == null) {
                sender.sendMessage(ChatColor.RED + "Minecraft player could not be found");
                return;
            }

            User user = null;
            try {
                user = DiscordUtil.getJda().getUserById(discord);
            } catch (IllegalArgumentException ignored) {}

            if (user == null) {
                try {
                    user = DiscordUtil.getJda().getUserByTag(discord);
                } catch (IllegalArgumentException ignored) {}
            }

            if (user == null) {
                sender.sendMessage(ChatColor.RED + "Discord user could not be found");
                return;
            }

            DiscordSRV.getPlugin().getAccountLinkManager().link(user.getId(), offlinePlayer.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "Linked together " + ChatColor.GOLD + offlinePlayer.getName()
                    + ChatColor.GREEN + " and " + ChatColor.GOLD + user.getAsTag());
            return;
        }

        // prevent people from generating multiple link codes then claiming them all at once to get multiple rewards
        new ArrayList<>(manager.getLinkingCodes().entrySet()).stream()
                .filter(entry -> entry.getValue().equals(sender.getUniqueId()))
                .forEach(match -> manager.getLinkingCodes().remove(match.getKey()));

        if (manager.getDiscordId(sender.getUniqueId()) != null) {
            sender.sendMessage(LangUtil.Message.ACCOUNT_ALREADY_LINKED.toString());
        } else {
            String code = manager.generateCode(sender.getUniqueId());

            TextComponent component = LegacyComponentSerializer.INSTANCE.deserialize(
                    LangUtil.Message.CODE_GENERATED.toString()
                            .replace("%code%", code)
                            .replace("%botname%", DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName()),
                    '&'
            );

            String clickToCopyCode = LangUtil.Message.CLICK_TO_COPY_CODE.toString();
            if (StringUtils.isNotBlank(clickToCopyCode)) {
                component = component.clickEvent(ClickEvent.suggestCommand(code))
                        .hoverEvent(HoverEvent.showText(
                                LegacyComponentSerializer.INSTANCE.deserialize(
                                        clickToCopyCode,
                                        '&'
                                )
                        ));
            }

            try {
                TextAdapter.sendComponent(sender, component);
            } catch (NoSuchMethodError e) {
                if (e.getMessage().contains("kyori.text")) {
                    sender.sendMessage(
                            LangUtil.Message.CODE_GENERATED.toString(true)
                                    .replace("%code%", code)
                                    .replace("%botname%", DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName())
                    );
                } else {
                    throw e;
                }
            }
        }
    }

}
