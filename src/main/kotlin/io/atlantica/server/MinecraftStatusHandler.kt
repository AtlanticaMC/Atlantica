package io.atlantica.server

import cz.lukynka.prettylog.GlobalPrettyLogger.log
import cz.lukynka.prettylog.LogType
import io.atlantica.AtlanticaServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.nio.charset.StandardCharsets
import java.util.UUID

class MinecraftStatusHandler(private val server: AtlanticaServer) : SimpleChannelInboundHandler<ByteBuf>() {

    private var protocolVersion = server.config.toml.getLong("protocol-version")?.toInt() ?: 772
    private var state = ConnectionState.HANDSHAKE
    private var username: String? = null
    private var uuid: UUID? = null
    private var configurationFinished = false
    private var minecraftCoreKnown = false

    override fun channelRead0(ctx: ChannelHandlerContext, packet: ByteBuf) {
        val packetId = packet.readVarInt()
        when (state) {
            ConnectionState.HANDSHAKE -> {
                if (packetId == 0) handleHandshake(ctx, packet) else ctx.close()
            }
            ConnectionState.STATUS -> handleStatus(ctx, packetId, packet)
            ConnectionState.LOGIN -> handleLogin(ctx, packetId, packet)
            ConnectionState.CONFIGURATION -> handleConfiguration(ctx, packetId, packet)
            ConnectionState.PLAY -> handlePlay(packetId)
        }
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, packet: ByteBuf) {
        protocolVersion = packet.readVarInt()
        packet.readString()
        packet.readUnsignedShort()
        val nextState = packet.readVarInt()

        state = when (nextState) {
            1 -> ConnectionState.STATUS
            2 -> ConnectionState.LOGIN
            else -> {
                ctx.close()
                return
            }
        }
    }

    private fun handleStatus(ctx: ChannelHandlerContext, packetId: Int, packet: ByteBuf) {
        when (packetId) {
            0 -> sendStatus(ctx)
            1 -> sendPong(ctx, packet)
            else -> ctx.close()
        }
    }

    private fun handleLogin(ctx: ChannelHandlerContext, packetId: Int, packet: ByteBuf) {
        when (packetId) {
            0 -> handleLoginStart(ctx, packet)
            3 -> handleLoginAcknowledged(ctx)
            else -> ctx.close()
        }
    }

    private fun handleLoginStart(ctx: ChannelHandlerContext, packet: ByteBuf) {
        username = packet.readString()
        uuid = if (packet.readableBytes() >= 16) {
            packet.readUuid()
        } else {
            UUID.nameUUIDFromBytes("OfflinePlayer:${username}".toByteArray(StandardCharsets.UTF_8))
        }

        log("${username} is logging in", LogType.DEBUG)
        sendLoginSuccess(ctx)
    }

    private fun sendLoginSuccess(ctx: ChannelHandlerContext) {
        val loginUuid = uuid
        val loginUsername = username
        if (loginUuid == null || loginUsername == null) {
            ctx.close()
            return
        }

        ctx.writeAndFlush(createPacket(2) {
            it.writeUuid(loginUuid)
            it.writeString(loginUsername)
            it.writeVarInt(0)
        })
    }

    private fun handleLoginAcknowledged(ctx: ChannelHandlerContext) {
        state = ConnectionState.CONFIGURATION
        log("${username} completed login and entered configuration", LogType.SUCCESS)
        sendKnownPacks(ctx)
    }

    private fun sendKnownPacks(ctx: ChannelHandlerContext) {
        val versionName = knownPackVersionName()
        log("Offering minecraft:core known pack version $versionName for protocol $protocolVersion", LogType.DEBUG)
        ctx.writeAndFlush(createPacket(0x0E) {
            it.writeVarInt(1)
            it.writeString("minecraft")
            it.writeString("core")
            it.writeString(versionName)
        })
    }

    private fun handleConfiguration(ctx: ChannelHandlerContext, packetId: Int, packet: ByteBuf) {
        when (packetId) {
            0x00 -> log("Received client information from ${username}", LogType.DEBUG)
            0x02 -> log("Received configuration plugin message from ${username}", LogType.DEBUG)
            0x03 -> handleFinishConfigurationAcknowledged()
            0x04 -> log("Received configuration keep alive from ${username}: ${packet.readLong()}", LogType.DEBUG)
            0x05 -> log("Received configuration pong from ${username}: ${packet.readInt()}", LogType.DEBUG)
            0x07 -> handleKnownPacks(ctx, packet)
            else -> log("Ignoring configuration packet 0x${packetId.toString(16)} from ${username}", LogType.DEBUG)
        }
    }

    private fun handleKnownPacks(ctx: ChannelHandlerContext, packet: ByteBuf) {
        val knownPackCount = packet.readVarInt()
        repeat(knownPackCount) {
            val namespace = packet.readString()
            val id = packet.readString()
            packet.readString()
            if (namespace == "minecraft" && id == "core") {
                minecraftCoreKnown = true
            }
        }

        log("${username} acknowledged $knownPackCount known pack(s)", LogType.DEBUG)
        sendDamageTypeRegistry(ctx)
        sendRequiredNonEmptyRegistries(ctx)
        sendTrimMaterialRegistry(ctx)
        sendAdditionalCoreRegistries(ctx)
        sendFeatureFlags(ctx)
        sendEmptyTags(ctx)
        sendFinishConfiguration(ctx)
    }

    private fun sendDamageTypeRegistry(ctx: ChannelHandlerContext) {
        val damageTypes = listOf(
            "arrow",
            "bad_respawn_point",
            "cactus",
            "campfire",
            "cramming",
            "dragon_breath",
            "drown",
            "dry_out",
            "ender_pearl",
            "explosion",
            "fall",
            "falling_anvil",
            "falling_block",
            "falling_stalactite",
            "fireball",
            "fireworks",
            "fly_into_wall",
            "freeze",
            "generic",
            "generic_kill",
            "hot_floor",
            "in_fire",
            "in_wall",
            "indirect_magic",
            "lava",
            "lightning_bolt",
            "mace_smash",
            "magic",
            "mob_attack",
            "mob_attack_no_aggro",
            "mob_projectile",
            "on_fire",
            "out_of_world",
            "outside_border",
            "player_attack",
            "player_explosion",
            "sonic_boom",
            "spear",
            "spit",
            "stalagmite",
            "starve",
            "sting",
            "sweet_berry_bush",
            "thorns",
            "thrown",
            "trident",
            "unattributed_fireball",
            "wind_charge",
            "wither",
            "wither_skull"
        )

        ctx.writeAndFlush(createPacket(0x07) {
            it.writeString("minecraft:damage_type")
            it.writeVarInt(damageTypes.size)
            damageTypes.forEach { damageType ->
                it.writeString("minecraft:$damageType")
                if (minecraftCoreKnown) {
                    it.writeBoolean(false)
                } else {
                    it.writeBoolean(true)
                    it.writeDamageTypeNbt(damageType)
                }
            }
        })
    }

    private fun sendRequiredNonEmptyRegistries(ctx: ChannelHandlerContext) {
        val registries = mapOf(
            "minecraft:cat_sound_variant" to listOf("classic", "royal"),
            "minecraft:cat_variant" to listOf(
                "all_black",
                "black",
                "british_shorthair",
                "calico",
                "jellie",
                "persian",
                "ragdoll",
                "red",
                "siamese",
                "tabby",
                "white"
            ),
            "minecraft:chicken_sound_variant" to listOf("classic", "picky"),
            "minecraft:chicken_variant" to listOf("cold", "temperate", "warm"),
            "minecraft:cow_sound_variant" to listOf("classic", "moody"),
            "minecraft:cow_variant" to listOf("cold", "temperate", "warm"),
            "minecraft:frog_variant" to listOf("cold", "temperate", "warm"),
            "minecraft:painting_variant" to listOf(
                "alban", "aztec", "aztec2", "backyard", "baroque", "bomb", "bouquet", "burning_skull",
                "bust", "cavebird", "changing", "cotan", "courbet", "creebet", "dennis", "donkey_kong",
                "earth", "endboss", "fern", "fighters", "finding", "fire", "graham", "humble", "kebab",
                "lowmist", "match", "meditative", "orb", "owlemons", "passage", "pigscene", "plant", "pointer",
                "pond", "pool", "prairie_ride", "sea", "skeleton", "skull_and_roses", "stage", "sunflowers",
                "sunset", "tides", "unpacked", "void", "wanderer", "wasteland", "water", "wind", "wither"
            ),
            "minecraft:pig_sound_variant" to listOf("big", "classic", "mini"),
            "minecraft:pig_variant" to listOf("cold", "temperate", "warm"),
            "minecraft:wolf_sound_variant" to listOf("angry", "big", "classic", "cute", "grumpy", "puglin", "sad"),
            "minecraft:wolf_variant" to listOf("ashen", "black", "chestnut", "pale", "rusty", "snowy", "spotted", "striped", "woods"),
            "minecraft:zombie_nautilus_variant" to listOf("temperate", "warm")
        )

        registries.forEach { (registry, entries) ->
            ctx.writeAndFlush(createPacket(0x07) {
                it.writeString(registry)
                it.writeVarInt(entries.size)
                entries.forEach { entry ->
                    it.writeString("minecraft:$entry")
                    it.writeBoolean(false)
                }
            })
        }
    }

    private fun sendTrimMaterialRegistry(ctx: ChannelHandlerContext) {
        val trimMaterials = listOf(
            "amethyst",
            "copper",
            "diamond",
            "emerald",
            "gold",
            "iron",
            "lapis",
            "netherite",
            "quartz",
            "redstone",
            "resin"
        )

        ctx.writeAndFlush(createPacket(0x07) {
            it.writeString("minecraft:trim_material")
            it.writeVarInt(trimMaterials.size)
            trimMaterials.forEach { material ->
                it.writeString("minecraft:$material")
                it.writeBoolean(false)
            }
        })
    }

    private fun sendAdditionalCoreRegistries(ctx: ChannelHandlerContext) {
        sendKnownPackRegistry(ctx, "minecraft:banner_pattern", listOf(
            "base", "border", "bricks", "circle", "creeper", "cross", "curly_border", "diagonal_left",
            "diagonal_right", "diagonal_up_left", "diagonal_up_right", "flow", "flower", "globe", "gradient",
            "gradient_up", "guster", "half_horizontal", "half_horizontal_bottom", "half_vertical", "half_vertical_right",
            "mojang", "piglin", "rhombus", "skull", "small_stripes", "square_bottom_left", "square_bottom_right",
            "square_top_left", "square_top_right", "straight_cross", "stripe_bottom", "stripe_center", "stripe_downleft",
            "stripe_downright", "stripe_left", "stripe_middle", "stripe_right", "stripe_top", "triangle_bottom",
            "triangle_top", "triangles_bottom", "triangles_top"
        ))
        sendKnownPackRegistry(ctx, "minecraft:chat_type", listOf(
            "chat", "emote_command", "msg_command_incoming", "msg_command_outgoing", "say_command",
            "team_msg_command_incoming", "team_msg_command_outgoing"
        ))
        sendKnownPackRegistry(ctx, "minecraft:dimension_type", listOf("overworld", "overworld_caves", "the_end", "the_nether"))
        sendKnownPackRegistry(ctx, "minecraft:enchantment", listOf(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection", "breach", "channeling",
            "density", "depth_strider", "efficiency", "feather_falling", "fire_aspect", "fire_protection",
            "flame", "fortune", "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty",
            "luck_of_the_sea", "lunge", "lure", "mending", "multishot", "piercing", "power",
            "projectile_protection", "protection", "punch", "quick_charge", "respiration", "riptide", "sharpness",
            "silk_touch", "smite", "soul_speed", "sweeping_edge", "swift_sneak", "thorns", "unbreaking",
            "vanishing_curse", "wind_burst"
        ))
        sendKnownPackRegistry(ctx, "minecraft:instrument", listOf(
            "admire_goat_horn", "call_goat_horn", "dream_goat_horn", "feel_goat_horn", "ponder_goat_horn",
            "seek_goat_horn", "sing_goat_horn", "yearn_goat_horn"
        ))
        sendKnownPackRegistry(ctx, "minecraft:jukebox_song", listOf(
            "11", "13", "5", "blocks", "cat", "chirp", "creator", "creator_music_box", "far", "lava_chicken",
            "mall", "mellohi", "otherside", "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward"
        ))
        sendKnownPackRegistry(ctx, "minecraft:trim_pattern", listOf(
            "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper",
            "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild"
        ))
        sendKnownPackRegistry(ctx, "minecraft:worldgen/biome", listOf(
            "badlands", "bamboo_jungle", "basalt_deltas", "beach", "birch_forest", "cherry_grove", "cold_ocean",
            "crimson_forest", "dark_forest", "deep_cold_ocean", "deep_dark", "deep_frozen_ocean",
            "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves", "end_barrens", "end_highlands",
            "end_midlands", "eroded_badlands", "flower_forest", "forest", "frozen_ocean", "frozen_peaks",
            "frozen_river", "grove", "ice_spikes", "jagged_peaks", "jungle", "lukewarm_ocean", "lush_caves",
            "mangrove_swamp", "meadow", "mushroom_fields", "nether_wastes", "ocean", "old_growth_birch_forest",
            "old_growth_pine_taiga", "old_growth_spruce_taiga", "pale_garden", "plains", "river", "savanna",
            "savanna_plateau", "small_end_islands", "snowy_beach", "snowy_plains", "snowy_slopes", "snowy_taiga",
            "soul_sand_valley", "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains", "swamp",
            "taiga", "the_end", "the_void", "warm_ocean", "warped_forest", "windswept_forest",
            "windswept_gravelly_hills", "windswept_hills", "windswept_savanna", "wooded_badlands"
        ))
    }

    private fun sendKnownPackRegistry(ctx: ChannelHandlerContext, registry: String, entries: List<String>) {
        ctx.writeAndFlush(createPacket(0x07) {
            it.writeString(registry)
            it.writeVarInt(entries.size)
            entries.forEach { entry ->
                it.writeString("minecraft:$entry")
                it.writeBoolean(false)
            }
        })
    }

    private fun knownPackVersionName(): String {
        return when (protocolVersion) {
            775 -> "26.1.2"
            774 -> "1.21.11"
            773 -> "1.21.10"
            772 -> "1.21.8"
            else -> server.config.toml.getString("version-name") ?: "26.1.2"
        }
    }

    private fun sendFeatureFlags(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(createPacket(0x0C) {
            it.writeVarInt(1)
            it.writeString("minecraft:vanilla")
        })
    }

    private fun sendEmptyTags(ctx: ChannelHandlerContext) {
        val damageTypeTags = listOf(
            "always_hurts_ender_dragons",
            "always_kills_armor_stands",
            "always_most_significant_fall",
            "always_triggers_silverfish",
            "avoids_guardian_thorns",
            "burn_from_stepping",
            "burns_armor_stands",
            "bypasses_armor",
            "bypasses_effects",
            "bypasses_enchantments",
            "bypasses_invulnerability",
            "bypasses_resistance",
            "bypasses_shield",
            "bypasses_wolf_armor",
            "can_break_armor_stand",
            "damages_helmet",
            "ignites_armor_stands",
            "is_drowning",
            "is_explosion",
            "is_fall",
            "is_fire",
            "is_freezing",
            "is_lightning",
            "is_player_attack",
            "is_projectile",
            "mace_smash",
            "no_anger",
            "no_impact",
            "no_knockback",
            "panic_causes",
            "panic_environmental_causes",
            "witch_resistant_to",
            "wither_immune_to"
        )

        ctx.writeAndFlush(createPacket(0x0D) {
            it.writeVarInt(1)

            it.writeString("minecraft:damage_type")
            it.writeVarInt(damageTypeTags.size)

            damageTypeTags.forEach { tag ->
                it.writeString("minecraft:$tag")
                it.writeVarInt(0)
            }
        })
    }

    private fun sendFinishConfiguration(ctx: ChannelHandlerContext) {
        configurationFinished = true
        ctx.writeAndFlush(createPacket(0x03) {})
    }

    private fun handleFinishConfigurationAcknowledged() {
        if (!configurationFinished) return
        state = ConnectionState.PLAY
        log("${username} entered play state", LogType.SUCCESS)
    }

    private fun handlePlay(packetId: Int) {
        log("Ignoring play packet 0x${packetId.toString(16)} from ${username}; play state is not implemented yet", LogType.DEBUG)
    }

    private fun sendStatus(ctx: ChannelHandlerContext) {
        val maxPlayers = server.config.toml.getLong("max-players") ?: 100
        val onlinePlayers = server.config.toml.getLong("online-players") ?: 0
        val motd = server.config.toml.getString("motd") ?: "AtlanticaMC"
        val versionName = server.config.toml.getString("version-name") ?: "26.1.2"
        val response = """
            {"version":{"name":"${versionName.escapeJson()}","protocol":$protocolVersion},"players":{"max":$maxPlayers,"online":$onlinePlayers},"description":{"text":"${motd.escapeJson()}"}}
        """.trimIndent()

        ctx.writeAndFlush(createPacket(0) { it.writeString(response) })
    }

    private fun sendPong(ctx: ChannelHandlerContext, packet: ByteBuf) {
        val payload = packet.readLong()
        ctx.writeAndFlush(createPacket(1) { it.writeLong(payload) })
    }

    private fun createPacket(packetId: Int, writeBody: (ByteBuf) -> Unit): ByteBuf {
        val body = Unpooled.buffer()
        body.writeVarInt(packetId)
        writeBody(body)

        val frame = Unpooled.buffer()
        frame.writeVarInt(body.readableBytes())
        frame.writeBytes(body)
        body.release()
        return frame
    }

    private fun ByteBuf.readVarInt(): Int {
        var value = 0
        var position = 0
        while (position < 35) {
            val currentByte = readByte().toInt()
            value = value or ((currentByte and 0x7F) shl position)
            if ((currentByte and 0x80) == 0) return value
            position += 7
        }
        throw IllegalArgumentException("VarInt is too big")
    }

    private fun ByteBuf.writeVarInt(value: Int) {
        var remaining = value
        do {
            var temp = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) temp = temp or 0x80
            writeByte(temp)
        } while (remaining != 0)
    }

    private fun ByteBuf.readString(): String {
        val length = readVarInt()
        val bytes = ByteArray(length)
        readBytes(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun ByteBuf.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    private fun ByteBuf.readUuid(): UUID {
        return UUID(readLong(), readLong())
    }

    private fun ByteBuf.writeUuid(uuid: UUID) {
        writeLong(uuid.mostSignificantBits)
        writeLong(uuid.leastSignificantBits)
    }

    private fun ByteBuf.writeDamageTypeNbt(messageId: String) {
        writeByte(10)
        writeNbtStringTag("message_id", messageId)
        writeNbtFloatTag("exhaustion", 0.0f)
        writeNbtStringTag("scaling", "never")
        writeByte(0)
    }

    private fun ByteBuf.writeEmptyCompoundNbt() {
        writeByte(10)
        writeByte(0)
    }

    private fun ByteBuf.writeNbtStringTag(name: String, value: String) {
        writeByte(8)
        writeNbtString(name)
        writeNbtString(value)
    }

    private fun ByteBuf.writeNbtFloatTag(name: String, value: Float) {
        writeByte(5)
        writeNbtString(name)
        writeFloat(value)
    }

    private fun ByteBuf.writeNbtString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeShort(bytes.size)
        writeBytes(bytes)
    }

    private fun String.escapeJson(): String = buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private enum class ConnectionState {
        HANDSHAKE,
        STATUS,
        LOGIN,
        CONFIGURATION,
        PLAY
    }
}
