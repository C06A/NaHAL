@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.helpchoice.nahal.plugin.chain

import com.helpchoice.nahal.haldish.plugin.HaldishPlugin
import com.helpchoice.nahal.haldish.plugin.NativePluginBridge
import com.helpchoice.nahal.plugin.baseurlrewriter.BaseUrlRewriterPlugin
import com.helpchoice.nahal.plugin.curie.CuriePlugin
import com.helpchoice.nahal.plugin.logger.LoggerPlugin
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.toKString
import platform.posix.getenv

// C bridge for the native plugin shared library (libhaldish_plugin.*). This is a batteries-included
// chain — curie → base-url-rewriter → logger, the same order as :plugins:chain's jvmRun — exposed
// as one dlopen-able plugin. Config for the wrapped plugins comes from HALDISH_PLUGIN_* env vars.
// Built from a separate compilation so the published haldish-plugin-chain klib stays a generic
// combinator with no dependency on the other plugin modules.
private val plugin: HaldishPlugin = ChainPlugin(
    CuriePlugin(),
    BaseUrlRewriterPlugin(configuredBase = getenv("HALDISH_PLUGIN_BASE_URL")?.toKString()),
    LoggerPlugin(directory = getenv("HALDISH_PLUGIN_LOG_DIR")?.toKString() ?: "./haldish-log"),
)

@CName("haldish_plugin_init")
fun haldishPluginInit(platform: CPointer<ByteVar>?, version: CPointer<ByteVar>?) =
    NativePluginBridge.init(plugin, platform, version)

@CName("haldish_plugin_pre_link")
fun haldishPluginPreLink(
    rel: CPointer<ByteVar>?,
    href: CPointer<ByteVar>?,
    templated: Int,
    type: CPointer<ByteVar>?,
    name: CPointer<ByteVar>?,
    title: CPointer<ByteVar>?,
    rootBody: CPointer<ByteVar>?,
    pathJson: CPointer<ByteVar>?,
): CPointer<ByteVar>? =
    NativePluginBridge.preLink(plugin, rel, href, templated, type, name, title, rootBody, pathJson)

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
