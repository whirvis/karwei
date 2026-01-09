/*
 * Copyright Whirvex Software LLC, All rights reserved.
 */
package io.whirvex.gradle

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import org.gradle.api.InvalidUserDataException
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Type

@JsonAdapter(NexusCredentialsAdapter::class)
internal data class NexusCredentials(
    val username: String,
    val password: String,
)

private val JsonElement.asNexusCredentials
    get() = asJsonObject.let {
        NexusCredentials(
            username = it["username"].asString,
            password = it["password"].asString,
        )
    }

internal fun NexusCredentials.toJsonObject() =
    JsonObject().also {
        it.addProperty("username", username)
        it.addProperty("password", password)
    }

internal object NexusCredentialsAdapter :
    JsonSerializer<NexusCredentials>,
    JsonDeserializer<NexusCredentials> {

    override fun serialize(
        src: NexusCredentials,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        return src.toJsonObject()
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): NexusCredentials {
        return json.asNexusCredentials
    }

}

@JsonAdapter(NexusConfigAdapter::class)
internal data class NexusConfig(
    val credentials: NexusCredentials,
)

// TODO: docs
private val JsonElement.asNexusConfig: NexusConfig
    get() = asJsonObject.let {
        val credentials = it["credentials"]
            .asJsonObject
            .asNexusCredentials
        return NexusConfig(credentials)
    }

internal fun NexusConfig.toJsonObject() =
    JsonObject().also {
        it.add("credentials", credentials.toJsonObject())
    }

internal object NexusConfigAdapter :
    JsonSerializer<NexusConfig>,
    JsonDeserializer<NexusConfig> {

    override fun serialize(
        src: NexusConfig,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        return src.toJsonObject()
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): NexusConfig {
        return json.asNexusConfig
    }

}

private const val EXPECTED_FILE_LOCATION_STR =
    "%expected_file_location%"

private val PRETTY_GSON = GsonBuilder()
    .setPrettyPrinting().create()

private val EXAMPLE_CONFIG = NexusConfig(
    credentials = NexusCredentials(
        username = "snuggles",
        password = "GimmeYogurt2016",
    )
)

private val INVALID_NEXUS_CONFIG_MESSAGE =
    """
        |An error has occurred while parsing the Nexus configuration file.
        |This could be because the file does not exist, its contents consist
        |of malformed JSON, it is missing required data, or something else.
        |
        |Expected file location:
        |  $EXPECTED_FILE_LOCATION_STR
        |
        |An example of a valid config file is given below.
        |
        |${PRETTY_GSON.toJson(EXAMPLE_CONFIG)}
    """.trimMargin()

private fun File.nexusConfigException(
    recap: String, cause: Exception,
): Nothing {
    val indentedConfigMessage =
        "\t" + INVALID_NEXUS_CONFIG_MESSAGE
            .replace(oldValue = "\n", newValue = "\n\t")
            .lines().joinToString(separator = "\n") {
                if (it == "\t") "" else it
            }

    val fullConfigMessage = indentedConfigMessage.replace(
        oldValue = EXPECTED_FILE_LOCATION_STR,
        newValue = absolutePath,
    )

    val subjectLine = "$recap at $absolutePath"
    val message = "$subjectLine\n\n$fullConfigMessage\n"
    throw InvalidUserDataException(message, cause)
}

internal fun File.readNexusConfig() =
    try {
        reader()
            .use { JsonParser.parseReader(it) }
            .asNexusConfig
    } catch (e: FileNotFoundException) {
        nexusConfigException("No config file", e)
    } catch (e: JsonParseException) {
        nexusConfigException("Malformed config file", e)
    } catch (e: IllegalStateException) {
        nexusConfigException("Invalid config file", e)
    }
