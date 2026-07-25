@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.helpchoice.nahal.plugin.curie

import com.helpchoice.nahal.haldish.plugin.NativePluginBridge
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer

// C bridge for the native plugin shared library (libhaldish_plugin.*). haldish's
// NativeDylibPluginAdapter resolves these symbols after dlopen and forwards each hook. CuriePlugin
// reads the document's CURIE collection at link time, so it needs no configuration.
private val plugin = CuriePlugin()

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
