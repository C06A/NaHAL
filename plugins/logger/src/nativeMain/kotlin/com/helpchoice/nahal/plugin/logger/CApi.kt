@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.helpchoice.nahal.plugin.logger

import com.helpchoice.nahal.haldish.plugin.NativePluginBridge
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.toKString
import platform.posix.getenv

// C bridge for the native plugin shared library (libhaldish_plugin.*). haldish's
// NativeDylibPluginAdapter resolves these symbols after dlopen and forwards each hook. The C init
// contract carries no config, so the log directory comes from a HALDISH_PLUGIN_* env var.
private val plugin = LoggerPlugin(
    directory = getenv("HALDISH_PLUGIN_LOG_DIR")?.toKString() ?: "./haldish-log",
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

@CName("haldish_plugin_post_response")
fun haldishPluginPostResponse(
    linkCount: Int,
    linkRels: CPointer<CPointerVar<ByteVar>>?,
    linkHrefs: CPointer<CPointerVar<ByteVar>>?,
    linkTemplated: CPointer<IntVar>?,
    propCount: Int,
    propKeys: CPointer<CPointerVar<ByteVar>>?,
    propJsonVals: CPointer<CPointerVar<ByteVar>>?,
    statusCode: Int,
    respBody: CPointer<ByteVar>?,
    respContentType: CPointer<ByteVar>?,
): CPointer<ByteVar>? =
    NativePluginBridge.postResponse(
        plugin, linkCount, linkRels, linkHrefs, linkTemplated,
        propCount, propKeys, propJsonVals, statusCode, respBody, respContentType,
    )
