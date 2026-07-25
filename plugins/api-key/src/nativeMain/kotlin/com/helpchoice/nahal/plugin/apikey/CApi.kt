@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.helpchoice.nahal.plugin.apikey

import com.helpchoice.nahal.haldish.plugin.NativePluginBridge
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.toKString
import platform.posix.getenv

// C bridge for the native plugin shared library (libhaldish_plugin.*). haldish's
// NativeDylibPluginAdapter resolves these symbols after dlopen and forwards each hook. The C init
// contract carries no config, so the API key and header name come from HALDISH_PLUGIN_* env vars.
private val plugin: ApiKeyPlugin = ApiKeyPlugin(
    apiKey     = getenv("HALDISH_PLUGIN_API_KEY")?.toKString() ?: "",
    headerName = getenv("HALDISH_PLUGIN_API_KEY_HEADER")?.toKString() ?: "X-Api-Key",
)

@CName("haldish_plugin_init")
fun haldishPluginInit(platform: CPointer<ByteVar>?, version: CPointer<ByteVar>?) =
    NativePluginBridge.init(plugin, platform, version)

@CName("haldish_plugin_pre_request")
fun haldishPluginPreRequest(
    url: CPointer<ByteVar>?,
    method: CPointer<ByteVar>?,
    headerCount: Int,
    headerKeys: CPointer<CPointerVar<ByteVar>>?,
    headerVals: CPointer<CPointerVar<ByteVar>>?,
    body: CPointer<ByteVar>?,
    contentType: CPointer<ByteVar>?,
    acceptHal: Int,
): CPointer<ByteVar>? =
    NativePluginBridge.preRequest(
        plugin, url, method, headerCount, headerKeys, headerVals, body, contentType, acceptHal,
    )
