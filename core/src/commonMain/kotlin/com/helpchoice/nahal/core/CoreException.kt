package com.helpchoice.nahal.core

sealed class CoreException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class NoSuchLinkException(val selector: LinkSelector) :
    CoreException("No link found for selector: $selector")

class PluginConfigException(message: String, cause: Throwable? = null) :
    CoreException(message, cause)
